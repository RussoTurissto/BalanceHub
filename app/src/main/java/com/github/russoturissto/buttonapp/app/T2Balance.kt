package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale

internal data class MobileSim(val subscriptionId: Int, val label: String, val operator: String, val isT2: Boolean, val networkCode: String = "")

internal object T2Balance {
    private const val PREFS = "balancehub_data"
    private const val DAY = 24L * 60L * 60L * 1000L
    private const val MIN_GAP = 60L * 1000L

    fun sims(context: Context): List<MobileSim> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            val name = tm.simOperatorName.orEmpty()
            val code = tm.simOperator.orEmpty()
            return listOf(MobileSim(-1, "SIM", name.ifBlank { "Оператор не определён" }, isT2(name, code), code))
        }
        return try {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val active = manager.activeSubscriptionInfoList.orEmpty()
            if (active.isEmpty()) {
                val name = tm.simOperatorName.orEmpty()
                listOf(MobileSim(-1, "SIM", name.ifBlank { "Оператор не определён" }, isT2(name, tm.simOperator.orEmpty()), tm.simOperator.orEmpty()))
            } else active.map { sub ->
                val scoped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.createForSubscriptionId(sub.subscriptionId) else tm
                val name = sub.carrierName?.toString().orEmpty().ifBlank { scoped.simOperatorName.orEmpty() }
                MobileSim(sub.subscriptionId, "SIM ${sub.simSlotIndex + 1}", name, isT2(name, scoped.simOperator.orEmpty()), scoped.simOperator.orEmpty())
            }
        } catch (_: SecurityException) { emptyList() }
    }

    private fun isT2(name: String, code: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return "tele2" in normalized || normalized == "t2" || code == "25020"
    }

    fun cached(context: Context, id: Int): Pair<String, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = p.getLong("t2_success_$id", 0L)
        val amount = p.getString("t2_amount_$id", null)
        return if (timestamp > 0 && amount != null) amount to timestamp else null
    }

    data class PackageInfo(
        val planMinutes: String?,
        val planGigabytes: String?,
        val savedMinutes: String?,
        val savedGigabytes: String?,
        val packagesExpireAt: Long?,
        val updatedAt: Long
    )

    fun packages(context: Context, id: Int): PackageInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updatedAt = p.getLong("t2_package_update_$id", 0L)
        if (updatedAt == 0L) return null
        val expires = p.getLong("t2_package_expires_$id", 0L).takeIf { it > 0 }
        return PackageInfo(
            p.getString("t2_plan_minutes_$id", null),
            p.getString("t2_plan_gb_$id", null),
            p.getString("t2_saved_minutes_$id", null),
            p.getString("t2_saved_gb_$id", null),
            expires, updatedAt
        )
    }

    fun check(context: Context, sim: MobileSim, force: Boolean, result: (String) -> Unit) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sim.isT2) { result("USSD поддерживается пока только для T2"); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { result("Android не поддерживает USSD API"); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            result("Разрешите телефонные вызовы для проверки баланса"); return
        }
        val now = System.currentTimeMillis()
        val last = p.getLong("t2_attempt_${sim.subscriptionId}", 0L)
        if (now - last < (if (force) MIN_GAP else DAY)) {
            result("Последняя проверка уже выполнена; повтор позже")
            return
        }
        p.edit { putLong("t2_attempt_${sim.subscriptionId}", now) }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val scoped = if (sim.subscriptionId >= 0) tm.createForSubscriptionId(sim.subscriptionId) else tm
        val handler = Handler(Looper.getMainLooper())
        try {
            scoped.sendUssdRequest("*105#", object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                    val snapshot = T2UssdParser.parse(response.toString())
                    if (!snapshot.hasRecognizedData) {
                        result("Ответ T2 получен, но данные не распознаны. Проверьте формат ответа.")
                        return
                    }
                    val updated = System.currentTimeMillis()
                    p.edit {
                        snapshot.balance?.let {
                            putString("t2_amount_" + sim.subscriptionId, it)
                            putLong("t2_success_" + sim.subscriptionId, updated)
                        }
                        if (snapshot.planMinutes != null || snapshot.planGigabytes != null ||
                            snapshot.savedMinutes != null || snapshot.savedGigabytes != null ||
                            snapshot.packagesExpireAt != null
                        ) {
                            snapshot.planMinutes?.let { putString("t2_plan_minutes_" + sim.subscriptionId, it) }
                            snapshot.planGigabytes?.let { putString("t2_plan_gb_" + sim.subscriptionId, it) }
                            snapshot.savedMinutes?.let { putString("t2_saved_minutes_" + sim.subscriptionId, it) }
                            snapshot.savedGigabytes?.let { putString("t2_saved_gb_" + sim.subscriptionId, it) }
                            snapshot.packagesExpireAt?.let { putLong("t2_package_expires_" + sim.subscriptionId, it) }
                            putLong("t2_package_update_" + sim.subscriptionId, updated)
                        }
                    }
                    result(if (snapshot.balance != null) {
                        "Баланс обновлён: " + snapshot.balance + " ₽" +
                            (if (snapshot.packagesExpireAt != null) "; пакеты обновлены" else "")
                    } else {
                        "Остатки пакетов обновлены; в ответе нет суммы баланса"
                    })
                }
                override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                    result("USSD не выполнен (код $failureCode). Проверьте сеть и SIM.")
                }
            }, handler)
        } catch (_: SecurityException) {
            result("Нет разрешения на телефонные вызовы")
        } catch (_: Exception) {
            result("Не удалось отправить USSD. Возможно, устройство или сеть не поддерживают запрос.")
        }
    }
}

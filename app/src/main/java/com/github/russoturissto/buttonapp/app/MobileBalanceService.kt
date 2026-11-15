package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale

/**
 * The same balance workflow for every SIM. Verified single-reply USSD where
 * available; user-entered amounts remain functional for any other operator.
 * Do not assume an MVNO uses the same code as its host network.
 */
internal object MobileBalanceService {
    private const val PREFS = "balancehub_data"
    private const val DAY = 86400000L
    private const val MIN_GAP = 60000L
    private val moneyPattern = Regex(
        """(?iu)(?:баланс(?:\s+счета)?|остаток\s+средств|на\s+счет[еу]|на\s+счёте|доступно\s+на\s+счет[еу])[^0-9-]{0,40}(-?\d[\d ]*(?:[.,]\d{1,2})?)"""
    )

    fun parseBalance(text: String): String? {
        val normalized = text.replace('\u00a0', ' ')
        return moneyPattern.find(normalized)?.groupValues?.get(1)
            ?.replace(" ", "")?.replace(',', '.')
    }

    fun operator(context: Context, sim: MobileSim): RussianMobileOperator? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val override = p.getString("mobile_operator_" + sim.subscriptionId, null)
        return if (!override.isNullOrBlank()) {
            RussianMobileOperators.byName(override)
                ?: RussianMobileOperator(override, override.take(3).uppercase(Locale.ROOT), emptyList())
        } else {
            RussianMobileOperators.find(sim.networkCode, sim.operator)
        }
    }

    fun displayName(context: Context, sim: MobileSim): String =
        operator(context, sim)?.displayName ?: sim.operator

    fun cached(context: Context, sim: MobileSim): Pair<String, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = sim.subscriptionId
        val timestamp = p.getLong("mobile_balance_updated_$id", 0L)
        val value = p.getString("mobile_balance_$id", null)
        val local = if (timestamp > 0 && value != null) value to timestamp else null
        val t2 = if (operator(context, sim)?.displayName == "T2 (Tele2)")
            T2Balance.cached(context, id) else null
        return listOfNotNull(local, t2).maxByOrNull { it.second }
    }

    fun setManual(context: Context, sim: MobileSim, amount: String): Boolean {
        val normalized = amount.trim().replace(',', '.')
        if (!normalized.matches(Regex("""\d{1,9}(?:\.\d{1,2})?"""))) return false
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit {
            putString("mobile_balance_" + sim.subscriptionId, normalized)
            putLong("mobile_balance_updated_" + sim.subscriptionId, System.currentTimeMillis())
        }
        return true
    }

    fun check(
        context: Context, sim: MobileSim,
        force: Boolean, result: (String) -> Unit
    ) {
        val operator = operator(context, sim)
        val code = operator?.balanceUssd
        if (code == null) {
            result("Для этого оператора нет проверенной USSD-команды. Укажите баланс вручную.")
            return
        }
        if (operator.displayName == "T2 (Tele2)") {
            T2Balance.check(context, sim.copy(isT2 = true), force, result)
            return
        }
        if (sim.subscriptionId < 0) {
            result("Для USSD нужна активная SIM-карта. Баланс можно внести вручную.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result("На этой версии Android используйте ручной ввод баланса.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED) {
            result("Разрешите телефонные вызовы или укажите баланс вручную.")
            return
        }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = p.getLong("mobile_ussd_attempt_" + sim.subscriptionId, 0L)
        if (now - last < (if (force) MIN_GAP else DAY)) {
            result("Проверка уже выполнена; повторите запрос позже.")
            return
        }
        p.edit { putLong("mobile_ussd_attempt_" + sim.subscriptionId, now) }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simManager = tm.createForSubscriptionId(sim.subscriptionId)
        try {
            simManager.sendUssdRequest(code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        manager: TelephonyManager, request: String, response: CharSequence
                    ) {
                        val amount = parseBalance(response.toString())
                        if (amount != null) {
                            p.edit {
                                putString("mobile_balance_" + sim.subscriptionId, amount)
                                putLong("mobile_balance_updated_" + sim.subscriptionId,
                                    System.currentTimeMillis())
                            }
                            result("Баланс обновлён: $amount ₽")
                        } else {
                            result("Ответ оператора получен, но баланс не распознан. " +
                                "Можно указать сумму вручную.")
                        }
                    }

                    override fun onReceiveUssdResponseFailed(
                        manager: TelephonyManager, request: String, failureCode: Int
                    ) {
                        result("USSD недоступен (код $failureCode). " +
                            "Попробуйте позже или введите баланс вручную.")
                    }
                }, Handler(Looper.getMainLooper())
            )
        } catch (_: SecurityException) {
            result("Нет разрешения на телефонные вызовы.")
        } catch (_: Exception) {
            result("USSD не поддерживается устройством или сетью. " +
                "Баланс можно указать вручную.")
        }
    }
}

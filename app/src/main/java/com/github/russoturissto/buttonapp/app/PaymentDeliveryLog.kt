package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit
import java.text.DateFormat
import java.util.Date

/** A tiny local, non-identifying journal for actual payment delivery times. */
internal object PaymentDeliveryLog {
    const val EXTRA_EXPECTED_AT = "balancehub_payment_expected_at"
    private const val PREFS = "balancehub_payment_delivery_v1"

    fun scheduled(context: Context, expectedAt: Long, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("scheduled_at", expectedAt)
            putString("scheduled_mode", mode)
        }
    }

    fun received(context: Context, expectedAt: Long, actualAt: Long, dueAt: Long) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("received_at", actualAt)
            putLong("expected_at", expectedAt)
            putLong("received_due_at", dueAt)
            putBoolean("received_interactive", power.isInteractive)
            putBoolean("received_idle", power.isDeviceIdleMode)
            putBoolean("received_power_saver", power.isPowerSaveMode)
            putString("result", "Обработчик запущен")
        }
    }

    fun posted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("posted_at", System.currentTimeMillis())
            putString("result", "Уведомление передано Android")
        }
    }

    fun skipped(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("posted_at", 0L)
            putString("result", reason)
        }
    }

    /** Never report an early delivery as a zero-second Android delay. */
    fun timingLine(expectedAt: Long, actualAt: Long): String? {
        if (expectedAt <= 0L || actualAt <= 0L) return null
        return if (actualAt < expectedAt)
            "Опережение относительно плана: " +
                ((expectedAt - actualAt + 999L) / 1_000L) + " сек"
        else "Задержка Android: " + ((actualAt - expectedAt) / 1_000L) + " сек"
    }

    fun report(context: Context): List<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun time(at: Long): String = if (at > 0L) DateFormat.getDateTimeInstance(
            DateFormat.SHORT, DateFormat.MEDIUM).format(Date(at)) else "неизвестно"
        val scheduled = p.getLong("scheduled_at", 0L)
        val fired = p.getLong("received_at", 0L)
        val expected = p.getLong("expected_at", 0L)
        val sent = p.getLong("posted_at", 0L)
        val lines = mutableListOf(
            "Следующий/последний запланированный сигнал: " + time(scheduled),
            "Метод: " + when (p.getString("scheduled_mode", "")) {
                "exact_idle" -> "точный сигнал без значка будильника"
                "inexact_idle" -> "резервный неточный"
                "expired" -> "просрочен"
                else -> "пока неизвестен"
            },
            "Последний запуск обработчика: " + time(fired))
        if (fired > 0L) {
            lines.add("Срок платежа этого запуска: " +
                time(p.getLong("received_due_at", 0L)))
            lines.add("Плановое время этого запуска: " + time(expected))
            timingLine(expected, fired)?.let(lines::add)
            lines.add("Экран при запуске: " +
                (if (p.getBoolean("received_interactive", true)) "включён" else "выключен"))
            lines.add("Doze при запуске: " +
                (if (p.getBoolean("received_idle", false)) "да" else "нет"))
            lines.add("Экономия энергии при запуске: " +
                (if (p.getBoolean("received_power_saver", false)) "да" else "нет"))
            lines.add("Результат: " + (p.getString("result", "неизвестен") ?: "неизвестен"))
            if (sent >= fired) lines.add("Отправка после запуска: " +
                ((sent - fired) / 1000L) + " сек")
        }
        lines.add("Это отметки приложения, не доказательство показа Android на экране.")
        return lines
    }
}

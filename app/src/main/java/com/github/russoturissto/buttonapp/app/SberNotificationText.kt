package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.os.Build

/**
 * Extracts standard Android notification text without storing it anywhere.
 * Stores only per-field presence/cue/currency/account bitmasks in diagnostics.
 */
internal object SberNotificationText {
    enum class Field(val mask: Int, val label: String) {
        TITLE(1, "Заголовок"),
        BIG_TITLE(2, "Расширенный заголовок"),
        TEXT(4, "Основной текст"),
        BIG_TEXT(8, "Развёрнутый текст"),
        TEXT_LINES(16, "Строки"),
        SUB_TEXT(32, "Дополнительный текст"),
        SUMMARY(64, "Сводка"),
        TICKER(128, "Краткое сообщение"),
        MESSAGES(256, "Сообщения")
    }

    data class Payload(
        val titles: List<String>,
        val bodies: List<String>,
        val fieldsMask: Int,
        val rubleFieldsMask: Int = 0,
        val cueFieldsMask: Int = 0,
        val accountFieldsMask: Int = 0,
        val fieldLengths: List<Int> = emptyList()
    ) {
        val hasReadableText: Boolean get() = titles.isNotEmpty() || bodies.isNotEmpty()
        val hasBody: Boolean get() = bodies.isNotEmpty()
    }

    fun extract(notification: Notification): Payload {
        val titles = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        var mask = 0
        var ruble = 0
        var cue = 0
        var account = 0
        val lengths = IntArray(Field.entries.size)

        fun add(field: Field, value: CharSequence?, isTitle: Boolean = false) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isBlank() || text.length > 8_192) return
            val signals = SberPushSignals.inspect(text)
            mask = mask or field.mask
            lengths[field.ordinal] = maxOf(lengths[field.ordinal], text.length)
            if (signals.money) ruble = ruble or field.mask
            if (signals.balanceCue) cue = cue or field.mask
            if (signals.maskedAccount) account = account or field.mask
            (if (isTitle) titles else bodies).add(text)
        }

        val extras = notification.extras
        if (extras != null) {
            // Some OEM devices throw when unparcelling a specific entry;
            // isolate fields so one bad extra cannot hide all other fields.
            fun safe(field: Field, isTitle: Boolean = false, getter: () -> CharSequence?) {
                try {
                    add(field, getter(), isTitle)
                } catch (_: RuntimeException) {
                    // This field was unavailable to the notification listener.
                }
            }
            safe(Field.TITLE, true) { extras.getCharSequence(Notification.EXTRA_TITLE) }
            safe(Field.BIG_TITLE, true) {
                extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            }
            safe(Field.TEXT) { extras.getCharSequence(Notification.EXTRA_TEXT) }
            safe(Field.BIG_TEXT) { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) }
            try {
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
                    add(Field.TEXT_LINES, it)
                }
            } catch (_: RuntimeException) { /* unreadable lines */ }
            safe(Field.SUB_TEXT) { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) }
            safe(Field.SUMMARY) { extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    @Suppress("DEPRECATION")
                    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    if (messages != null) {
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
                            .forEach { add(Field.MESSAGES, it.text) }
                    }
                } catch (_: RuntimeException) { /* no readable messages */ }
            }
        }
        add(Field.TICKER, notification.tickerText)
        return Payload(titles.distinct(), bodies.distinct(), mask, ruble, cue, account,
            lengths.toList())
    }
}

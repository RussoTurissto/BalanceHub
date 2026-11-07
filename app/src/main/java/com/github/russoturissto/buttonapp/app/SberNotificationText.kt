package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.os.Build

/**
 * Reads only documented Android notification fields. Raw text is ephemeral:
 * it is not saved, logged or sent off-device.
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
        val fieldsMask: Int
    ) {
        val hasReadableText: Boolean get() = titles.isNotEmpty() || bodies.isNotEmpty()
        val hasBody: Boolean get() = bodies.isNotEmpty()
    }

    fun extract(notification: Notification): Payload {
        val titles = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        var mask = 0
        fun add(field: Field, value: CharSequence?, isTitle: Boolean = false) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isBlank() || text.length > 8_192) return
            mask = mask or field.mask
            (if (isTitle) titles else bodies).add(text)
        }

        try {
            val extras = notification.extras
            if (extras != null) {
                add(Field.TITLE, extras.getCharSequence(Notification.EXTRA_TITLE), true)
                add(Field.BIG_TITLE, extras.getCharSequence(Notification.EXTRA_TITLE_BIG), true)
                add(Field.TEXT, extras.getCharSequence(Notification.EXTRA_TEXT))
                add(Field.BIG_TEXT, extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
                    add(Field.TEXT_LINES, it)
                }
                add(Field.SUB_TEXT, extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
                add(Field.SUMMARY, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    if (messages != null) {
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
                            .forEach { add(Field.MESSAGES, it.text) }
                    }
                }
            }
        } catch (_: RuntimeException) {
            // Some OEM notifications have unparcelable extras. Preserve fields
            // already read and fall back to the ticker where available.
        }
        add(Field.TICKER, notification.tickerText)
        return Payload(titles.distinct(), bodies.distinct(), mask)
    }
}

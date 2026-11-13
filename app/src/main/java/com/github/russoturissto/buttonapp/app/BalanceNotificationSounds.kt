package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect

/**
 * Fifteen bundled sounds. Android 8+ owns a channel after it is created.
 * An explicit in-app selection replaces one category's channel generation
 * (and deletes the previous active one); ordinary app upgrades never reset
 * user-selected Android channel settings.
 */
@SuppressLint("UseKtx")
internal object BalanceNotificationSounds {
    const val PAYMENT = "payment_reminders"
    const val EVENT = "event_reminders"
    const val UPDATE = "balancehub_app_updates"

    data class Tone(val key: String, val title: String, val resource: Int)
    val tones = listOf(
        Tone("balancehub", "BalanceHub", R.raw.bh_sound_balancehub),
        Tone("crystal", "Кристалл", R.raw.bh_sound_crystal),
        Tone("drop", "Капля", R.raw.bh_sound_drop),
        Tone("aurora", "Аврора", R.raw.bh_sound_aurora),
        Tone("bamboo", "Бамбук", R.raw.bh_sound_bamboo),
        Tone("orbit", "Орбита", R.raw.bh_sound_orbit),
        Tone("pearl", "Жемчуг", R.raw.bh_sound_pearl),
        Tone("breeze", "Бриз", R.raw.bh_sound_breeze),
        Tone("spark", "Искра", R.raw.bh_sound_spark),
        Tone("lagoon", "Лагуна", R.raw.bh_sound_lagoon),
        Tone("pulse", "Пульс", R.raw.bh_sound_pulse),
        Tone("dawn", "Рассвет", R.raw.bh_sound_dawn),
        Tone("echo", "Эхо", R.raw.bh_sound_echo),
        Tone("comet", "Комета", R.raw.bh_sound_comet),
        Tone("harmony", "Гармония", R.raw.bh_sound_harmony)
    )

    private fun preferences(context: Context) =
        context.getSharedPreferences("balancehub_notification_sounds", Context.MODE_PRIVATE)

    fun selected(context: Context, category: String): String {
        val default = when (category) {
            PAYMENT -> "balancehub"
            EVENT -> "crystal"
            UPDATE -> "spark"
            else -> "system"
        }
        val value = preferences(context).getString(category, default) ?: default
        return value.takeIf { it == "system" || it == "silent" ||
            tones.any { tone -> tone.key == it } } ?: default
    }

    fun title(context: Context, category: String): String =
        when (val key = selected(context, category)) {
            "system" -> "Системный звук"
            "silent" -> "Без звука"
            else -> tones.firstOrNull { it.key == key }?.title ?: "BalanceHub"
        }

    fun choose(context: Context, category: String, key: String) {
        if (key != "system" && key != "silent" && tones.none { it.key == key }) return
        preferences(context).edit().putString(category, key).apply()
    }

    private fun vibrationPrefs(context: Context) = preferences(context)
    fun vibrationEnabled(context: Context): Boolean =
        vibrationPrefs(context).getBoolean("vibration_all", true)
    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        vibrationPrefs(context).edit().putBoolean("vibration_all", enabled).apply()
    }
    fun vibrationFor(context: Context, category: String): Boolean {
        val default = true
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
            vibrationEnabled(context)) &&
            vibrationPrefs(context).getBoolean("vibration_" + category, default)
    }
    fun setVibrationFor(context: Context, category: String, enabled: Boolean) {
        vibrationPrefs(context).edit()
            .putBoolean("vibration_" + category, enabled).apply()
    }
    fun vibrationPattern(category: String): LongArray = when (category) {
        PAYMENT -> longArrayOf(0L, 155L, 95L, 225L)
        EVENT -> longArrayOf(0L, 105L, 95L, 115L)
        else -> longArrayOf(0L, 90L)
    }

    /** Device notification volume and Do Not Disturb always remain in control. */
    fun separateVolume(context: Context): Boolean =
        preferences(context).getBoolean("volume_separate", false)

    fun setSeparateVolume(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("volume_separate", enabled).apply()
    }

    fun volume(context: Context, category: String? = null): Int {
        val prefs = preferences(context)
        val name = if (category != null && separateVolume(context))
            "volume_" + category else "volume_all"
        return prefs.getInt(name, 100).coerceIn(0, 100)
    }

    fun setVolume(context: Context, category: String?, percent: Int) {
        val name = if (category != null) "volume_" + category else "volume_all"
        preferences(context).edit().putInt(name, percent.coerceIn(0, 100)).apply()
    }

    private fun soundUri(context: Context, key: String, volume: Int): Uri? {
        if (key == "silent") return null
        if (key == "system") return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return Uri.parse("content://" + context.packageName + ".sounds/" + key +
            "/v3/" + volume.coerceIn(0, 100))
    }

    fun legacyChannel(category: String): String = when (category) {
        PAYMENT -> "Платежи"
        EVENT -> "События"
        else -> "Обновления"
    }

    /** Existing Android channels remain untouched until the user explicitly applies changes. */
    fun channelId(context: Context, category: String): String =
        StableNotificationChannels.id(category,
            preferences(context).getInt("channel_revision_" + category, 0).coerceAtLeast(0))

    private fun toneReadable(context: Context, uri: Uri?): Boolean = try {
        uri != null && context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize >= 44L
        } == true
    } catch (_: Exception) { false }

    private fun createConfiguredChannel(context: Context, category: String,
        id: String, key: String, vibrate: Boolean, strict: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (manager.getNotificationChannel(id) != null) return true
        val channel = NotificationChannel(id, legacyChannel(category),
            if (category == UPDATE) NotificationManager.IMPORTANCE_DEFAULT
            else NotificationManager.IMPORTANCE_HIGH)
        val gain = volume(context, category)
        if (key == "silent" || gain == 0) {
            channel.setSound(null, null)
        } else {
            val uri = soundUri(context, key, gain)
            val resolved = if (key == "system") uri else uri?.takeIf {
                toneReadable(context, it)
            }
            // Do not claim that a custom tone was applied if Android cannot read its WAV.
            if (strict && resolved == null) return false
            channel.setSound(
                resolved ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
        channel.enableVibration(vibrate)
        if (vibrate) {
            if (Build.VERSION.SDK_INT >= 35) {
                channel.setVibrationEffect(VibrationEffect.createWaveform(
                    vibrationPattern(category), -1))
            } else channel.vibrationPattern = vibrationPattern(category)
        }
        manager.createNotificationChannel(channel)
        return manager.getNotificationChannel(id) != null
    }

    fun ensureChannel(context: Context, category: String): String {
        val id = channelId(context, category)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createConfiguredChannel(context, category, id, selected(context, category),
                vibrationFor(context, category), strict = false)
        }
        return id
    }

    /** Only the user can request a new sound/vibration configuration.
     * Generates a never-reused ID and removes the old channel from active categories.
     * Returns a readable error or null on success.
     */
    @Synchronized
    fun applyUserChoice(context: Context, category: String,
        key: String, vibrate: Boolean): String? {
        if (key != "system" && key != "silent" && tones.none { it.key == key })
            return "Неизвестная мелодия"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            choose(context, category, key)
            setVibrationFor(context, category, vibrate)
            return null
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val oldId = ensureChannel(context, category)
        val prefs = preferences(context)
        val previous = prefs.getInt("channel_revision_" + category, 0).coerceAtLeast(0)
        if (previous >= Int.MAX_VALUE - 1) return "Превышено число изменений канала"
        val next = previous + 1
        val nextId = StableNotificationChannels.id(category, next)
        try {
            if (!createConfiguredChannel(context, category, nextId, key,
                    vibrate, strict = true)) return "Мелодия недоступна Android"
            val actual = manager.getNotificationChannel(nextId)
            val gain = volume(context, category)
            val expectedSound = if (key == "silent" || gain == 0) null
                else soundUri(context, key, gain)
            if (actual == null || actual.sound != expectedSound ||
                actual.shouldVibrate() != vibrate) {
                manager.deleteNotificationChannel(nextId)
                return "Android не применил звук или вибрацию"
            }
            if (!prefs.edit().putString(category, key)
                    .putBoolean("vibration_" + category, vibrate)
                    .putInt("channel_revision_" + category, next).commit()) {
                manager.deleteNotificationChannel(nextId)
                return "Не удалось сохранить параметры уведомления"
            }
            manager.deleteNotificationChannel(oldId)
            deleteUnusedChannels(context)
            return null
        } catch (_: Exception) {
            // Preserve the previous working channel and saved selection.
            if (nextId != channelId(context, category)) {
                try { manager.deleteNotificationChannel(nextId) }
                catch (_: Exception) { }
            }
            return "Не удалось обновить системный канал"
        }
    }

    /** An explicit repair action is offered rather than silently resetting Android settings. */
    fun needsRepair(context: Context, category: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val id = ensureChannel(context, category)
        val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager).getNotificationChannel(id) ?: return true
        val gain = volume(context, category)
        val key = selected(context, category)
        val wanted = if (key == "silent" || gain == 0) null
            else soundUri(context, key, gain)
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
            channel.sound != wanted || channel.shouldVibrate() != vibrationFor(context, category)
    }

    /** Always keep precisely the three current categories in the active channel list. */
    fun ensureDefaultChannels(context: Context) {
        for (category in listOf(PAYMENT, EVENT, UPDATE)) ensureChannel(context, category)
        deleteUnusedChannels(context)
    }

    /**
     * What Android will actually attempt to play, rather than merely the
     * requested app preference. Existing user-customized channels are never
     * reset or silently replaced.
     */
    fun effectiveSoundTitle(context: Context, category: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return title(context, category)
        val id = ensureChannel(context, category)
        val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager).getNotificationChannel(id)
            ?: return "Канал не найден"
        if (channel.importance == NotificationManager.IMPORTANCE_NONE)
            return "Канал отключён в Android"
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return "Беззвучный канал Android"
        val actual = channel.sound ?: return "Без звука в Android"
        val key = selected(context, category)
        val expected = if (key == "silent" || volume(context, category) == 0) null
            else soundUri(context, key, volume(context, category))
        return when {
            actual == expected -> title(context, category)
            actual == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ->
                "Системный звук Android"
            else -> "Мелодия Android"
        }
    }

    fun effectiveSummary(context: Context, category: String): String {
        val sound = effectiveSoundTitle(context, category)
        val vibration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = channelId(context, category)
            val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager).getNotificationChannel(id)
            if (channel?.shouldVibrate() == true &&
                channel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
                "вибрация включена" else "без вибрации"
        } else if (vibrationFor(context, category)) "вибрация включена"
        else "без вибрации"
        return sound + " · " + vibration
    }

    /** Only retired BalanceHub tone variants. Active channels never appear here. */
    fun unusedChannelIds(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val current = listOf(PAYMENT, EVENT, UPDATE).map { channelId(context, it) }.toSet()
        return manager.notificationChannels.map { it.id }
            .filter { it !in current &&
                (StableNotificationChannels.isRetired(it) ||
                    StableNotificationChannels.isManaged(it)) }
    }

    /** Explicit user-initiated cleanup; never silently discards Android choices. */
    fun deleteUnusedChannels(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val stale = unusedChannelIds(context)
        stale.forEach { manager.deleteNotificationChannel(it) }
        return stale.size
    }

    /** For API 25 and earlier: newer Android versions use channel sound. */
    fun configureLegacySound(context: Context, category: String,
        builder: androidx.core.app.NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        val key = selected(context, category)
        builder.setSound(if (key == "silent" || (key != "system" && volume(context, category) == 0))
            null else soundUri(context, key, volume(context, category)))
        builder.setVibrate(if (vibrationFor(context, category))
            vibrationPattern(category) else longArrayOf(0L))
    }

    fun fallbackReason(context: Context, category: String): String? =
        preferences(context).getString("sound_fallback_" + category, null)

    /**
     * Engineering preview of the *effective Android channel sound*, not a
     * separate copy of a raw resource. A successful start is not proof that
     * the speaker was audible (device sound policies still apply).
     */
    fun previewChannel(context: Context, category: String): String {
        stopPreview()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = ensureChannel(context, category)
            (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager).getNotificationChannel(id)?.sound
        } else soundUri(context, selected(context, category), volume(context, category))
        if (uri == null) return "Звук этого уведомления выключен"
        return try {
            if (uri.authority == context.packageName + ".sounds") {
                val readable = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize >= 44L
                } == true
                if (!readable) return "Фирменная мелодия недоступна системе Android"
            }
            val player = MediaPlayer()
            previewPlayer = player
            player.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            player.setDataSource(context, uri)
            player.prepare()
            player.setOnCompletionListener { finished ->
                if (previewPlayer === finished) previewPlayer = null
                finished.release()
            }
            player.setOnErrorListener { failed, _, _ ->
                if (previewPlayer === failed) previewPlayer = null
                failed.release()
                true
            }
            player.start()
            "Воспроизведение через системный URI запущено"
        } catch (error: Exception) {
            stopPreview()
            "Не удалось открыть мелодию: " + error.javaClass.simpleName
        }
    }

    private var previewPlayer: MediaPlayer? = null

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }

    fun preview(context: Context, key: String, category: String) {
        stopPreview()
        val tone = tones.firstOrNull { it.key == key } ?: return
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build())
            context.resources.openRawResourceFd(tone.resource)?.use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            } ?: return
            player.prepare()
            val gain = volume(context, category) / 100f
            player.setVolume(gain, gain)
            previewPlayer = player
            player.setOnCompletionListener {
                it.release()
                if (previewPlayer === it) previewPlayer = null
            }
            player.start()
        } catch (_: Exception) {
            stopPreview()
        }
    }
}

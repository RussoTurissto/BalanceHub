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

    private val notificationAttributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION).build()

    /** The public content URI survives APK upgrades and supports stored volume. */
    private fun resourceUri(context: Context, key: String): Uri? {
        val tone = tones.firstOrNull { it.key == key } ?: return null
        val name = context.resources.getResourceEntryName(tone.resource)
        return Uri.parse("android.resource://" + context.packageName + "/raw/" + name)
    }

    /**
     * Probe the first bytes of the actual file instead of relying on
     * ParcelFileDescriptor.statSize, which can be -1 for a readable stream.
     * Return only a concise diagnostic code; never swallow a failure silently.
     */
    private fun toneProbe(context: Context, uri: Uri): String? = try {
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            "пустой поток"
        } else {
            try {
                val header = ByteArray(12)
                var count = 0
                while (count < header.size) {
                    val read = stream.read(header, count, header.size - count)
                    if (read <= 0) break
                    count += read
                }
                // Successful WAV probe returns null (no error). Do not coalesce
                // this nullable success into the "пустой поток" error.
                if (count != 12 ||
                    !header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
                    !header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()))
                    "неверный WAV" else null
            } finally {
                // Explicit finally is needed because Android Lint does not
                // recognize nullable InputStream.use() for this API.
                stream.close()
            }
        }
    } catch (error: Exception) {
        android.util.Log.w("BalanceHubSound", "Cannot read WAV " + uri.scheme, error)
        error.javaClass.simpleName
    }

    /** Read-only diagnostics shared by the settings and channel audit screen. */
    fun audioProbe(context: Context, uri: Uri): String? = toneProbe(context, uri)

    private data class SoundSelection(val uri: Uri?, val error: String? = null)

    private fun resolveSound(context: Context, key: String, gain: Int): SoundSelection {
        if (key == "silent" || gain == 0) return SoundSelection(null)
        if (key == "system") {
            val system = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            return SoundSelection(system, if (system == null) "В Android не выбран системный звук"
                else null)
        }
        val primary = soundUri(context, key, gain)
            ?: return SoundSelection(null, "Не найдена встроенная мелодия")
        val primaryError = toneProbe(context, primary)
        if (primaryError == null) return SoundSelection(primary)
        // Some OEMs cannot open a custom provider from the sound picker.
        // A named android.resource URI is a second independent, stable lookup.
        val fallback = resourceUri(context, key)
        val fallbackError = fallback?.let { toneProbe(context, it) }
            ?: "ресурс отсутствует"
        if (fallbackError == null && fallback != null) return SoundSelection(fallback)
        return SoundSelection(null, "Не удалось прочитать WAV: provider=$primaryError, " +
            "ресурс=$fallbackError")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun newChannel(context: Context, category: String, id: String,
        sound: Uri?, vibrate: Boolean, importance: Int,
        attributes: AudioAttributes? = notificationAttributes): NotificationChannel {
        val channel = NotificationChannel(id, legacyChannel(category), importance)
        channel.setSound(sound, if (sound == null) null else attributes)
        channel.enableVibration(vibrate)
        if (vibrate) {
            if (Build.VERSION.SDK_INT >= 35) {
                channel.setVibrationEffect(VibrationEffect.createWaveform(
                    vibrationPattern(category), -1))
            } else channel.vibrationPattern = vibrationPattern(category)
        }
        return channel
    }

    private fun createConfiguredChannel(context: Context, category: String,
        id: String, key: String, vibrate: Boolean, strict: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (manager.getNotificationChannel(id) != null) return null
        val selection = resolveSound(context, key, volume(context, category))
        if (strict && selection.error != null) return selection.error
        val sound = if (selection.error == null) selection.uri
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val importance = if (category == UPDATE) NotificationManager.IMPORTANCE_DEFAULT
            else NotificationManager.IMPORTANCE_HIGH
        manager.createNotificationChannel(newChannel(context, category,
            id, sound, vibrate, importance))
        return if (manager.getNotificationChannel(id) != null) null
            else "Android не создал канал уведомлений"
    }

    fun ensureChannel(context: Context, category: String): String {
        val id = channelId(context, category)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createConfiguredChannel(context, category, id, selected(context, category),
                vibrationFor(context, category), strict = false)
        }
        return id
    }

    private fun nextRevision(context: Context, category: String): Int? {
        val previous = preferences(context)
            .getInt("channel_revision_" + category, 0).coerceAtLeast(0)
        return if (previous < Int.MAX_VALUE - 1) previous + 1 else null
    }

    /**
     * User-initiated tone change. The actual Android channel is queried back
     * before the new revision is persisted and the old channel is retired.
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
        val next = nextRevision(context, category) ?: return "Превышено число изменений канала"
        val nextId = StableNotificationChannels.id(category, next)
        val prefs = preferences(context)
        return try {
            val error = createConfiguredChannel(context, category,
                nextId, key, vibrate, strict = true)
            if (error != null) return error
            val actual = manager.getNotificationChannel(nextId)
            val expected = resolveSound(context, key, volume(context, category)).uri
            if (actual == null || actual.sound != expected ||
                actual.shouldVibrate() != vibrate) {
                manager.deleteNotificationChannel(nextId)
                return "Android не применил выбранный звук или вибрацию"
            }
            if (!prefs.edit().putString(category, key)
                    .putBoolean("vibration_" + category, vibrate)
                    .putInt("channel_revision_" + category, next).commit()) {
                manager.deleteNotificationChannel(nextId)
                return "Не удалось сохранить параметры уведомления"
            }
            manager.deleteNotificationChannel(oldId)
            deleteUnusedChannels(context)
            null
        } catch (error: Exception) {
            android.util.Log.e("BalanceHubSound", "Tone channel update failed", error)
            if (nextId != channelId(context, category)) {
                try { manager.deleteNotificationChannel(nextId) }
                catch (_: Exception) { }
            }
            "Не удалось установить мелодию: " + error.javaClass.simpleName
        }
    }

    /**
     * Vibration does NOT require a bundled WAV to be readable. Keep the
     * effective sound URI from Android unchanged while changing vibration.
     */
    @Synchronized
    fun applyVibrationChoice(context: Context, category: String,
        vibrate: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            setVibrationFor(context, category, vibrate)
            return null
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val oldId = ensureChannel(context, category)
        val previous = manager.getNotificationChannel(oldId)
            ?: return "Канал уведомлений Android не найден"
        if (previous.shouldVibrate() == vibrate &&
            (!vibrate || previous.importance >= NotificationManager.IMPORTANCE_DEFAULT)) {
            setVibrationFor(context, category, vibrate)
            return null
        }
        val next = nextRevision(context, category) ?: return "Превышено число изменений канала"
        val nextId = StableNotificationChannels.id(category, next)
        val prefs = preferences(context)
        return try {
            val importance = if (vibrate)
                previous.importance.coerceAtLeast(NotificationManager.IMPORTANCE_DEFAULT)
            else previous.importance
            val channel = newChannel(context, category, nextId,
                previous.sound, vibrate, importance, previous.audioAttributes)
            manager.createNotificationChannel(channel)
            val actual = manager.getNotificationChannel(nextId)
            if (actual?.shouldVibrate() != vibrate || actual.sound != previous.sound) {
                manager.deleteNotificationChannel(nextId)
                return "Android не применил вибрацию"
            }
            if (!prefs.edit().putBoolean("vibration_" + category, vibrate)
                    .putInt("channel_revision_" + category, next).commit()) {
                manager.deleteNotificationChannel(nextId)
                return "Не удалось сохранить вибрацию"
            }
            manager.deleteNotificationChannel(oldId)
            deleteUnusedChannels(context)
            null
        } catch (error: Exception) {
            android.util.Log.e("BalanceHubSound", "Vibration channel update failed", error)
            if (nextId != channelId(context, category)) {
                try { manager.deleteNotificationChannel(nextId) }
                catch (_: Exception) { }
            }
            "Не удалось установить вибрацию: " + error.javaClass.simpleName
        }
    }

    private fun matchesConfiguredSound(context: Context, key: String, gain: Int,
        actual: Uri?): Boolean {
        if (key == "silent" || gain == 0) return actual == null
        if (key == "system")
            return actual == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return actual == soundUri(context, key, gain) || actual == resourceUri(context, key)
    }

    /** An explicit repair action is offered rather than silently resetting Android settings. */
    fun needsRepair(context: Context, category: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val id = ensureChannel(context, category)
        val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager).getNotificationChannel(id) ?: return true
        val gain = volume(context, category)
        val key = selected(context, category)
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
            !matchesConfiguredSound(context, key, gain, channel.sound) ||
            channel.shouldVibrate() != vibrationFor(context, category)
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
        return when {
            matchesConfiguredSound(context, key, volume(context, category), actual) ->
                title(context, category)
            actual == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ->
                "Системный звук Android"
            else -> "Мелодия Android"
        }
    }

    /** Read back the system's actual state, not just our saved preference. */
    fun effectiveVibration(context: Context, category: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return vibrationFor(context, category)
        val id = ensureChannel(context, category)
        val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager).getNotificationChannel(id)
        return channel?.shouldVibrate() == true &&
            channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
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

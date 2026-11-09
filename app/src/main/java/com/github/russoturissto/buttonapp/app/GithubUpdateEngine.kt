package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Uses GitHub Releases only; never sends bank notification data to the network. */
internal object GithubUpdateEngine {
    private const val SETTINGS = "balancehub_updates_v1"
    private const val FEED =
        "https://api.github.com/repos/RussoTurissto/BalanceHub/releases/latest"
    private const val PERIODIC_JOB = "balancehub_release_watch"

    fun preferences(context: Context) =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean =
        preferences(context).getBoolean("notify", true)

    fun setEnabled(context: Context, enable: Boolean) {
        preferences(context).edit().putBoolean("notify", enable).apply()
        if (enable) schedule(context) else {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_JOB)
            NotificationManagerCompat.from(context).cancel(13772)
        }
    }

    fun installedCode(context: Context): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    fun cached(context: Context): UpdateRelease? =
        UpdateReleasePolicy.fromCache(
            preferences(context).getString("cached_release", null),
            installedCode(context))

    fun schedule(context: Context) {
        if (!enabled(context)) return
        val request = PeriodicWorkRequestBuilder<GithubUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_JOB, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    @Throws(IOException::class)
    fun fetch(context: Context): UpdateRelease? {
        val connection = (URL(FEED).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BalanceHub-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("GitHub HTTP " + connection.responseCode)
            // Cap untrusted response size; release notes are truncated separately.
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(120_000)
            }
            val release = try {
                UpdateReleasePolicy.parse(JSONObject(text), installedCode(context))
            } catch (e: Exception) {
                throw IOException("Некорректный ответ GitHub", e)
            }
            preferences(context).edit().putLong("last_success",
                System.currentTimeMillis())
                .putString("cached_release",
                    release?.let(UpdateReleasePolicy::toCache)).apply()
            return release
        } finally { connection.disconnect() }
    }

    /** A manual check always hits GitHub; launch checks can reuse a 30m cache. */
    fun checkAsync(context: Context, manual: Boolean,
        completion: (UpdateRelease?, String?) -> Unit) {
        val app = context.applicationContext
        Thread({
            var release: UpdateRelease? = null
            var error: String? = null
            try {
                val last = preferences(app).getLong("last_success", 0L)
                release = if (!manual && System.currentTimeMillis() - last < 30 * 60_000L)
                    cached(app)
                else fetch(app)
            } catch (_: Exception) {
                release = cached(app)
                error = "Не удалось получить ответ GitHub. Проверьте интернет."
            }
            val result = release
            val problem = error
            Handler(Looper.getMainLooper()).post { completion(result, problem) }
        }, "BalanceHub-GithubCheck").start()
    }

    fun shouldOfferInApp(context: Context, release: UpdateRelease): Boolean =
        preferences(context).getLong("shown_in_app", 0L) < release.code

    fun markInAppOffered(context: Context, release: UpdateRelease) {
        preferences(context).edit().putLong("shown_in_app", release.code).apply()
    }

    fun announceIfNew(context: Context, release: UpdateRelease) {
        if (!enabled(context)) return
        val prefs = preferences(context)
        if (prefs.getLong("announced", 0L) >= release.code) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = "balancehub_app_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            manager.createNotificationChannel(NotificationChannel(
                channel, "Обновления BalanceHub",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Уведомления о новых подписанных релизах"
            })

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_tab", 4)
            putExtra("show_update", true)
        }
        val pending = PendingIntent.getActivity(context, 13772, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(context, channel)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Доступно обновление BalanceHub " + release.tag)
            .setContentText("Посмотрите изменения и скачайте новую версию.")
            .setContentIntent(pending).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        try {
            NotificationManagerCompat.from(context).notify(13772, note)
            prefs.edit().putLong("announced", release.code).apply()
        } catch (_: SecurityException) { /* user revoked notifications */ }
    }
}

class GithubUpdateWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {
    override fun doWork(): Result {
        if (!GithubUpdateEngine.enabled(applicationContext)) return Result.success()
        return try {
            GithubUpdateEngine.fetch(applicationContext)?.let {
                GithubUpdateEngine.announceIfNew(applicationContext, it)
            }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

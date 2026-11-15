package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

internal data class ApkDownloadState(
    val id: Long, val status: Int,
    val received: Long, val total: Long, val reason: Int
)

internal object GithubApkInstaller {
    private const val KEY_ID = "download_id"
    private const val KEY_VERSION = "download_version"

    private fun manager(context: Context) = context.getSystemService(
        Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun fileName(release: UpdateRelease): String =
        "BalanceHub-update-" + release.code + ".apk"
    private fun localFile(context: Context, release: UpdateRelease): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("Хранилище недоступно"), fileName(release))

    fun status(context: Context, release: UpdateRelease): ApkDownloadState? {
        val prefs = GithubUpdateEngine.preferences(context)
        if (prefs.getLong(KEY_VERSION, -1) != release.code) return null
        val id = prefs.getLong(KEY_ID, -1)
        if (id < 0) return null
        val cursor = manager(context).query(DownloadManager.Query().setFilterById(id))
            ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            fun column(name: String) = it.getColumnIndexOrThrow(name)
            ApkDownloadState(id,
                it.getInt(column(DownloadManager.COLUMN_STATUS)),
                it.getLong(column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                it.getLong(column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                it.getInt(column(DownloadManager.COLUMN_REASON)))
        }
    }

    fun begin(context: Context, release: UpdateRelease): Long {
        val current = status(context, release)
        if (current != null && current.status != DownloadManager.STATUS_FAILED)
            return current.id
        if (current != null) manager(context).remove(current.id)
        localFile(context, release).delete()
        val request = DownloadManager.Request(release.url.toUri())
            .setTitle("BalanceHub " + release.tag)
            .setDescription("Подписанное обновление приложения")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context,
                Environment.DIRECTORY_DOWNLOADS, fileName(release))
        val id = manager(context).enqueue(request)
        GithubUpdateEngine.preferences(context).edit {
            putLong(KEY_ID, id)
            putLong(KEY_VERSION, release.code)
        }
        return id
    }

    /** Reject a tampered APK, a downgrade, a foreign package or a new signer. */
    fun verifiedApk(context: Context, release: UpdateRelease): File {
        val file = localFile(context, release)
        if (!file.isFile || file.length() != release.bytes ||
            file.length() > 80_000_000L)
            throw SecurityException("Размер APK не совпадает с GitHub")
        val hash = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        val digest = hash.digest().joinToString("") {
            "%02x".format(Locale.ROOT, it.toInt() and 0xff)
        }
        if (digest != release.sha256) {
            file.delete()
            throw SecurityException("Контрольная сумма APK не совпадает")
        }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw SecurityException("Android не распознал пакет обновления")
        if (archive.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(archive) != release.code ||
            release.code <= GithubUpdateEngine.installedCode(context))
            throw SecurityException("Пакет или версия APK не совпадают")
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val theirs = signers(archive)
        val ours = signers(installed)
        if (theirs.isEmpty() || theirs != ours)
            throw SecurityException("Сертификат подписи APK не совпадает")
        return file
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.signingInfo?.apkContentsSigners?.toList() ?: emptyList()
        else info.signatures?.toList() ?: emptyList()
        return certificates.map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        }.toSet()
    }

    fun allowedToInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    // Called only after API 26+ has been verified by allowedToInstall().
    @SuppressLint("InlinedApi")
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            ("package:" + context.packageName).toUri())

    fun installerIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FileProvider.getUriForFile(context,
                context.packageName + ".updateprovider", file),
                "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun cleanOld(context: Context) {
        val installed = GithubUpdateEngine.installedCode(context)
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles()?.forEach { file ->
            if (!file.name.startsWith("BalanceHub-update-") ||
                !file.name.endsWith(".apk")) return@forEach
            val version = file.name.removePrefix("BalanceHub-update-")
                .removeSuffix(".apk").toLongOrNull()
            if (version != null && version <= installed) file.delete()
        }
    }
}

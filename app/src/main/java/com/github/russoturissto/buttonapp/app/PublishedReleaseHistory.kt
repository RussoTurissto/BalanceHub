package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * Public, stable GitHub releases only. RCs, drafts and untagged historical
 * artifacts never enter the user-facing history. Only tags are fetched:
 * descriptions are curated in AppReleaseNotes, not copied from raw Markdown.
 */
internal object PublishedReleaseHistory {
    private const val PREFS = "balancehub_published_release_history_v1"
    private const val FEED =
        "https://api.github.com/repos/RussoTurissto/BalanceHub/releases?per_page=100&page="
    private val VERSION_TAG = Regex("""v([0-9]+)\.([0-9]+)\.([0-9]+)""")

    fun parsePublishedTags(releases: JSONArray): List<String> {
        val tags = mutableListOf<String>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", true) ||
                release.optBoolean("prerelease", true)) continue
            val tag = release.optString("tag_name", "")
            if (!VERSION_TAG.matches(tag) ||
                release.optString("published_at", "").isBlank()) continue
            tags.add(tag.removePrefix("v"))
        }
        return ordered(tags)
    }

    internal fun ordered(tags: List<String>): List<String> =
        tags.distinct().sortedWith(compareByDescending<String> {
            it.substringBefore('.').toIntOrNull() ?: 0
        }.thenByDescending {
            it.substringAfter('.').substringBefore('.').toIntOrNull() ?: 0
        }.thenByDescending {
            it.substringAfterLast('.').toIntOrNull() ?: 0
        })

    fun offline(context: Context): List<String> {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName
        val fallback = AppReleaseNotes.offlineStableHistory(installed)
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("stable_tags", null) ?: return fallback
        return try {
            val json = JSONArray(cached)
            val parsed = (0 until json.length()).mapNotNull { i ->
                json.optString(i, "").takeIf { VERSION_TAG.matches("v$it") }
            }
            ordered(parsed + fallback)
        } catch (_: Exception) {
            fallback
        }
    }

    /** No application-specific information is sent to GitHub. */
    @Throws(IOException::class)
    fun fetch(): List<String> {
        val discovered = mutableListOf<String>()
        for (page in 1..5) {
            val connection = (URL(FEED + page).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 9_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BalanceHub-Android")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val response = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("GitHub HTTP " + connection.responseCode)
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().take(2_000_000)
                }
            } finally {
                connection.disconnect()
            }
            val pageArray = try { JSONArray(response) } catch (e: Exception) {
                throw IOException("Некорректный ответ GitHub", e)
            }
            discovered.addAll(parsePublishedTags(pageArray))
            if (pageArray.length() < 100) break
        }
        return ordered(discovered)
    }

    fun fetchAsync(context: Context, callback: (List<String>) -> Unit) {
        val app = context.applicationContext
        Thread({
            val stable = try {
                val online = fetch()
                if (online.isNotEmpty()) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                        putString("stable_tags", JSONArray(online).toString())
                    }
                    online
                } else offline(app)
            } catch (_: Exception) {
                offline(app)
            }
            Handler(Looper.getMainLooper()).post { callback(stable) }
        }, "BalanceHub-ReleaseHistory").start()
    }
}

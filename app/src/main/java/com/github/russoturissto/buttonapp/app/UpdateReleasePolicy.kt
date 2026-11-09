package com.github.russoturissto.buttonapp.app

import org.json.JSONObject
import org.json.JSONArray

/** Only the public signed release asset of this repository may be installed. */
internal data class UpdateRelease(
    val tag: String,
    val code: Long,
    val url: String,
    val sha256: String,
    val bytes: Long,
    val notes: String
)

internal object UpdateReleasePolicy {
    private val versionPattern = Regex("^v1\\.0\\.([1-9][0-9]*)$")
    private val checksumPattern = Regex("^[a-f0-9]{64}$")

    fun parse(json: JSONObject, installed: Long): UpdateRelease? {
        val tag = json.optString("tag_name")
        val number = versionPattern.matchEntire(tag)?.groupValues?.get(1)
            ?.toLongOrNull() ?: return null
        if (number <= installed || number > Int.MAX_VALUE ||
            json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") != "BalanceHub.apk") continue
            val url = asset.optString("browser_download_url")
            if (url != "https://github.com/RussoTurissto/BalanceHub/" +
                    "releases/download/$tag/BalanceHub.apk") continue
            val sha256 = asset.optString("digest").removePrefix("sha256:").lowercase()
            if (!checksumPattern.matches(sha256)) continue
            val bytes = asset.optLong("size", -1)
            if (bytes !in 1_024..80_000_000) continue
            return UpdateRelease(tag, number, url, sha256, bytes,
                json.optString("body", "").take(5_000))
        }
        return null
    }

    fun fromCache(raw: String?, installed: Long): UpdateRelease? = try {
        if (raw.isNullOrBlank()) null else parse(JSONObject(raw), installed)
    } catch (_: Exception) { null }

    fun toCache(info: UpdateRelease): String = JSONObject()
        .put("tag_name", info.tag)
        .put("body", info.notes)
        .put("assets", JSONArray().put(JSONObject()
            .put("name", "BalanceHub.apk")
            .put("browser_download_url", info.url)
            .put("digest", "sha256:" + info.sha256)
            .put("size", info.bytes)))
        .toString()
}

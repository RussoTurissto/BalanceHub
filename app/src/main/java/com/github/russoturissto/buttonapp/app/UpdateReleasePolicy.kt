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
    private val versionPattern = Regex("""^v([1-9][0-9]*)\.([0-9]+)\.([0-9]+)$""")
    private val previewPattern = Regex("""^v([1-9][0-9]*)\.([0-9]+)\.([0-9]+)-test\.([1-9][0-9]*)$""")
    // The declared Android versionCode is separate from the user-facing SemVer.
    // It is attached as an HTML comment to approved GitHub release notes.
    private val declaredCodePattern = Regex(
        """<!--\s*BalanceHub-VersionCode:\s*([1-9][0-9]*)\s*-->""")
    private val checksumPattern = Regex("^[a-f0-9]{64}$")

    fun parse(json: JSONObject, installed: Long, preview: Boolean = false): UpdateRelease? {
        val tag = json.optString("tag_name")
        val match = (if (preview) previewPattern else versionPattern)
            .matchEntire(tag) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: return null
        val minor = match.groupValues[2].toLongOrNull() ?: return null
        val patch = match.groupValues[3].toLongOrNull() ?: return null
        val body = json.optString("body", "")
        val explicit = declaredCodePattern.find(body)
            ?.groupValues?.get(1)?.toLongOrNull()
        // Preserve compatibility with older v1.0.RUN_NUMBER releases that
        // predate the SemVer approval workflow and lack the metadata marker.
        val code = explicit ?: if (major == 1L && minor == 0L && patch > 0L)
            patch else return null
        if (code <= installed || code > Int.MAX_VALUE ||
            json.optBoolean("draft") ||
            json.optBoolean("prerelease") != preview) return null
        val assetName = if (preview) "BalanceHub-Test.apk" else "BalanceHub.apk"
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") != assetName) continue
            val url = asset.optString("browser_download_url")
            if (url != "https://github.com/RussoTurissto/BalanceHub/" +
                    "releases/download/$tag/$assetName") continue
            val sha256 = asset.optString("digest").removePrefix("sha256:").lowercase()
            if (!checksumPattern.matches(sha256)) continue
            val bytes = asset.optLong("size", -1)
            if (bytes !in 1_024..80_000_000) continue
            return UpdateRelease(tag, code, url, sha256, bytes,
                body.replace(declaredCodePattern, "").trim().take(5_000))
        }
        return null
    }

    fun fromCache(raw: String?, installed: Long, preview: Boolean = false):
        UpdateRelease? = try {
        if (raw.isNullOrBlank()) null else parse(JSONObject(raw), installed, preview)
    } catch (_: Exception) { null }

    fun toCache(info: UpdateRelease): String = JSONObject()
        .put("tag_name", info.tag)
        .put("prerelease", info.tag.contains("-test."))
        .put("body", info.notes + "\n<!-- BalanceHub-VersionCode: " + info.code + " -->")
        .put("assets", JSONArray().put(JSONObject()
            .put("name", if (info.tag.contains("-test.")) "BalanceHub-Test.apk"
                else "BalanceHub.apk")
            .put("browser_download_url", info.url)
            .put("digest", "sha256:" + info.sha256)
            .put("size", info.bytes)))
        .toString()
}

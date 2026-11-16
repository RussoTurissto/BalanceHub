package com.github.russoturissto.buttonapp.app

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdateReleasePolicyTest {
    private fun release(tag: String = "v1.0.140", digest: String = "sha256:" +
        "a".repeat(64), assetUrl: String = "https://github.com/RussoTurissto/" +
        "BalanceHub/releases/download/$tag/BalanceHub.apk", size: Long = 8_700_000L
    ) = JSONObject().put("tag_name", tag).put("body", "Исправления и новые возможности")
        .put("assets", JSONArray().put(JSONObject()
            .put("name", "BalanceHub.apk")
            .put("browser_download_url", assetUrl)
            .put("digest", digest)
            .put("size", size)))

    @Test fun acceptsStrictGithubReleaseAndCachesIt() {
        val update = UpdateReleasePolicy.parse(release(), 137L)
        assertNotNull(update)
        assertEquals(140L, update!!.code)
        assertEquals("a".repeat(64), update.sha256)
        assertNotNull(UpdateReleasePolicy.fromCache(
            UpdateReleasePolicy.toCache(update), 137L))
    }
    @Test fun acceptsApprovedSemanticVersionsWithIndependentAndroidCode() {
        val approved = release("v1.1.0").put("body",
            "Большое обновление\n<!-- BalanceHub-VersionCode: 210 -->")
        val result = UpdateReleasePolicy.parse(approved, 200L)
        assertNotNull(result)
        assertEquals("v1.1.0", result!!.tag)
        assertEquals(210L, result.code)
        assertEquals("Большое обновление", result.notes)
        assertEquals(210L, UpdateReleasePolicy.fromCache(
            UpdateReleasePolicy.toCache(result), 200L)!!.code)
        assertNotNull(UpdateReleasePolicy.parse(release("v1.1.0").put("body",
            "<!-- BalanceHub-VersionCode: 211 -->"), 210L))
        assertNull(UpdateReleasePolicy.parse(release("v1.0.0"), 200L))
        assertNull(UpdateReleasePolicy.parse(release("v1.1.0"), 200L))
        assertNull(UpdateReleasePolicy.parse(approved, 210L))
    }
    @Test fun stable120IsVisibleOnlyWhenPublishedAndNewer() {
        val body = "• Платежи и события: улучшены напоминания.\\n\\n" +
            "• Уведомления: громкость применяется сразу.\\n" +
            "<!-- BalanceHub-VersionCode: 715 -->"
        val published = release("v1.2.0").put("body", body)
            .put("draft", false).put("prerelease", false)
        val update = UpdateReleasePolicy.parse(published, 710L)
        assertNotNull(update)
        assertEquals("v1.2.0", update!!.tag)
        assertEquals(715L, update.code)
        assertEquals("a".repeat(64), update.sha256)
        assertEquals("• Платежи и события: улучшены напоминания.\\n\\n" +
            "• Уведомления: громкость применяется сразу.", update.notes)
        assertNotNull(UpdateReleasePolicy.fromCache(
            UpdateReleasePolicy.toCache(update), 710L))
        assertNull(UpdateReleasePolicy.parse(published, 715L))
        assertNull(UpdateReleasePolicy.parse(JSONObject(published.toString())
            .put("draft", true), 710L))
        assertNull(UpdateReleasePolicy.parse(JSONObject(published.toString())
            .put("prerelease", true), 710L))
    }

    @Test fun rejectsOldDraftAndBadVersions() {
        assertNull(UpdateReleasePolicy.parse(release(), 140L))
        assertNull(UpdateReleasePolicy.parse(release("v1.0.133"), 137L))
        assertNull(UpdateReleasePolicy.parse(release("v1.1.140"), 137L))
        assertNull(UpdateReleasePolicy.parse(release().put("draft", true), 137L))
    }
    @Test fun rejectsRedirectedAssetMissingDigestAndTooLargeFile() {
        assertNull(UpdateReleasePolicy.parse(release(assetUrl = "https://evil.test/x.apk"),137L))
        assertNull(UpdateReleasePolicy.parse(release(digest = "sha256:bad"),137L))
        assertNull(UpdateReleasePolicy.parse(release(size = 100_000_000L),137L))
        assertNull(UpdateReleasePolicy.fromCache("invalid",137L))
    }
}

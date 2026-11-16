package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublishedReleaseHistoryTest {
    private fun release(tag: String, draft: Boolean = false,
        prerelease: Boolean = false, published: String = "2026-11-16T11:02:11Z"
    ): JSONObject = JSONObject().put("tag_name", tag)
        .put("draft", draft).put("prerelease", prerelease)
        .put("published_at", published)

    @Test fun onlyPublishedStableReleasesAreIncluded() {
        val list = JSONArray()
            .put(release("v1.2.1-rc4", prerelease = true))
            .put(release("v1.2.1-rc3", prerelease = true))
            .put(release("v1.2.0"))
            .put(release("v1.2.1", draft = true, published = ""))
            .put(release("v1.1.2"))
            .put(release("untagged-123"))
            .put(release("v1.1.1", published = ""))
            .put(release("v1.0.144"))
        val found = PublishedReleaseHistory.parsePublishedTags(list)
        assertEquals(listOf("1.2.0", "1.1.2", "1.0.144"), found)
        assertFalse(found.any { "-rc" in it || "untagged" in it })
    }

    @Test fun stableVersionsAreSortedNumericallyWithoutDuplicates() {
        assertEquals(listOf("1.2.1", "1.2.0", "1.1.12", "1.1.2", "1.0.144"),
            PublishedReleaseHistory.ordered(listOf("1.1.2", "1.0.144",
                "1.1.12", "1.2.0", "1.2.1", "1.2.0")))
    }

    @Test fun unknownStableGetsShortNontechnicalFallback() {
        assertEquals("Обновления и исправления.",
            AppReleaseNotes.forStableRelease("1.0.101").single().title)
    }
}

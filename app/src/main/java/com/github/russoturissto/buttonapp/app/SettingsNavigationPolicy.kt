package com.github.russoturissto.buttonapp.app

/** Sanitized hierarchy to keep Android Back behavior deterministic. */
internal object SettingsNavigationPolicy {
    const val ROOT = "root"
    private val parents = mapOf(
        "appearance" to ROOT, "mobile" to ROOT, "banks" to ROOT,
        "notifications" to ROOT, "updates" to ROOT,
        "help" to ROOT, "about" to ROOT,
        "payment" to "notifications", "event" to "notifications",
        "update_sound" to "notifications"
    )
    fun canOpen(current: String, dest: String): Boolean =
        parents[dest] == current ||
            (dest == "vibration" && (current == "notifications" || current == "help"))
    fun sanitize(path: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (page in path) {
            if (!canOpen(result.lastOrNull() ?: ROOT, page)) break
            result.add(page)
        }
        return result
    }
}

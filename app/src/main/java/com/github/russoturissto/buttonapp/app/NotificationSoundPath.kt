package com.github.russoturissto.buttonapp.app

/** Pure validation for persisted notification sound URLs, including legacy ones. */
internal object NotificationSoundPath {
    fun parse(pathSegments: List<String>): Pair<String, Int>? {
        if (pathSegments.size != 1 &&
            (pathSegments.size != 3 || pathSegments[1] != "v3")) return null
        val key = pathSegments[0]
        if (!key.matches(Regex("[a-z]{2,24}"))) return null
        val gain = if (pathSegments.size == 1) 100
            else pathSegments[2].toIntOrNull() ?: return null
        if (gain !in 0..100) return null
        return key to gain
    }
}

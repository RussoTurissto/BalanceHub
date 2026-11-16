package com.github.russoturissto.buttonapp.app

/** Collapse both upcoming sections once on first install and once per APK version. */
internal object UpcomingListVersionPolicy {
    fun shouldCollapse(installedVersionCode: Long, lastSeenVersionCode: Long): Boolean =
        installedVersionCode != lastSeenVersionCode
}

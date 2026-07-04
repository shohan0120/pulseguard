package com.pulseguard.data

/** A launchable app the user can choose to keep alive. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

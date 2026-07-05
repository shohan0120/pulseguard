package com.pulseguard.data

/** All user-tunable configuration for the background maintenance service, as one snapshot. */
data class PulseSettings(
    /** Whether the background maintenance service (watchdog + periodic re-verify + light poke) runs. */
    val engineEnabled: Boolean = false,
    /** How often the background service re-verifies protections. UI restricts to [INTERVAL_OPTIONS]. */
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val selectedPackages: Set<String> = emptySet(),

    // Battery savers ---------------------------------------------------------
    /** Skip a tick while the screen is on (user is present, push already flows). */
    val skipWhenScreenOn: Boolean = true,
    /**
     * Skip a tick while charging. Defaults to FALSE on purpose: the classic complaint is
     * "phone on the charger overnight, notifications hours late", and aggressive ROMs freeze
     * apps regardless of charge state — so protecting while charging is exactly the point, and
     * there's no battery to save anyway. The toggle exists for users who disagree.
     */
    val skipWhenCharging: Boolean = false,
    /** Skip a tick when the device is idle AND on unmetered Wi-Fi (push tends to survive). */
    val skipWhenIdleOnWifi: Boolean = false,
    /** Double the effective interval during the night window. */
    val nightBackoffEnabled: Boolean = true,
    val nightStartHour: Int = DEFAULT_NIGHT_START,
    val nightEndHour: Int = DEFAULT_NIGHT_END,

    // Behaviour --------------------------------------------------------------
    /** Length of the temp-whitelist window granted per app per tick. */
    val tempWhitelistSeconds: Int = DEFAULT_WHITELIST_SECONDS,

    // App-level flags --------------------------------------------------------
    val onboardingCompleted: Boolean = false,
) {
    val hasSelection: Boolean get() = selectedPackages.isNotEmpty()

    companion object {
        val INTERVAL_OPTIONS = listOf(5, 10, 15)
        const val DEFAULT_INTERVAL_MINUTES = 15
        const val DEFAULT_NIGHT_START = 23
        const val DEFAULT_NIGHT_END = 7
        const val DEFAULT_WHITELIST_SECONDS = 60
    }
}

package com.pulseguard.engine

/** Result of one keep-alive tick, persisted for the dashboard. */
data class TickOutcome(
    val timestamp: Long,
    val skipped: Boolean,
    val skipReason: String? = null,
    val pulsedPackages: List<String> = emptyList(),
    val failedPackages: List<String> = emptyList(),
    val shizukuReady: Boolean = false,
)

/** Live snapshot of the engine, surfaced on the home/dashboard screen. */
data class EngineState(
    val serviceRunning: Boolean = false,
    val lastTickTime: Long = 0L,
    val lastTickSkipped: Boolean = false,
    val lastTickReason: String = "",
    val lastPulsedCount: Int = 0,
    val lastFailedCount: Int = 0,
    val nextTickTime: Long = 0L,
    val totalTicks: Int = 0,
    val lastError: String = "",
) {
    val hasRun: Boolean get() = lastTickTime > 0L
}

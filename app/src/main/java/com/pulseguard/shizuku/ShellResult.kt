package com.pulseguard.shizuku

/**
 * App-side view of a shell command result. The wire format from [IUserService.exec] is
 * `"<exitCode>\n<merged stdout+stderr>"`; [decode] parses it back.
 */
data class ShellResult(
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean get() = exitCode == 0

    /** Convenience: first non-blank line of output, useful for single-value queries. */
    val firstLine: String get() = output.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    companion object {
        /** Returned when Shizuku is not connected / not permitted, distinct from a real exit code. */
        val UNAVAILABLE = ShellResult(exitCode = -99, output = "Shizuku unavailable")

        fun decode(raw: String): ShellResult {
            val newline = raw.indexOf('\n')
            if (newline < 0) {
                return ShellResult(raw.trim().toIntOrNull() ?: -1, "")
            }
            val code = raw.substring(0, newline).trim().toIntOrNull() ?: -1
            return ShellResult(code, raw.substring(newline + 1))
        }
    }
}

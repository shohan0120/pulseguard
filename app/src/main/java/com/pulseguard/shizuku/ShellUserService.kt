package com.pulseguard.shizuku

import android.content.Context
import java.io.BufferedReader
import kotlin.system.exitProcess

/**
 * The Shizuku UserService. Instantiated by Shizuku inside a process running as the ADB
 * shell user (UID 2000). Anything spawned via [Runtime.exec] here inherits that UID, so
 * privileged shell commands (`cmd deviceidle tempwhitelist`, `dumpsys`, `cmd appops`, ...)
 * work with no root.
 *
 * Shizuku looks for either a no-arg constructor or one taking a [Context]; we expose both.
 */
class ShellUserService() : IUserService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        // Shizuku calls this (transaction 16777114) to tear the process down.
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            // Drain the single merged stream fully BEFORE waitFor() to avoid a pipe-buffer
            // deadlock on large output (e.g. dumpsys).
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()

            val trimmed =
                if (output.length > MAX_OUTPUT_CHARS) {
                    output.substring(0, MAX_OUTPUT_CHARS) + "\n…[truncated]"
                } else {
                    output
                }
            "$code\n$trimmed"
        } catch (t: Throwable) {
            "-1\n${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private companion object {
        // Keep well under the ~1MB binder transaction ceiling (UTF-16 chars).
        const val MAX_OUTPUT_CHARS = 400_000
    }
}

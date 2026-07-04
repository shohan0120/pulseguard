// IUserService.aidl
package com.pulseguard.shizuku;

/**
 * Runs inside the Shizuku-spawned process, which has the ADB shell UID (2000).
 * Commands executed here therefore run with shell privileges — enough for
 * `cmd deviceidle tempwhitelist`, `dumpsys`, `cmd appops`, etc. — without root.
 */
interface IUserService {

    /**
     * Reserved transaction id used by the Shizuku server to tear the service down.
     * MUST keep this exact id or Shizuku cannot destroy the process.
     */
    void destroy() = 16777114;

    /** Graceful self-exit. */
    void exit() = 1;

    /**
     * Runs `sh -c <command>` as shell and returns the result encoded as:
     *   first line  = integer exit code (or -1 on failure to launch)
     *   remainder   = merged stdout + stderr
     * See ShellResult.decode() on the app side.
     */
    String exec(String command) = 2;
}

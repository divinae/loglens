package com.dv.loglens.service

/**
 * Holds the scrcpy executable to invoke. Blank/unset means plain "scrcpy" resolved from PATH;
 * a configured path lets users point at a specific scrcpy build. Mirrors [Adb] — set once from
 * config on startup and whenever the setting changes, read by [ScrcpyService] when launching.
 */
object Scrcpy {
    @Volatile
    var path: String = ""

    /** The executable to pass to ProcessBuilder — the configured path, or "scrcpy" when unset. */
    fun exe(): String = path.ifBlank { "scrcpy" }
}

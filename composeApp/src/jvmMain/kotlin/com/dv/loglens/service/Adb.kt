package com.dv.loglens.service

/**
 * Holds the adb executable to invoke. Blank/unset means plain "adb" resolved from PATH;
 * a configured path lets users point at a specific adb (like AndLogView's adb-location setting).
 * Set once from config on startup and whenever the setting changes, read by every adb-spawning service.
 */
object Adb {
    @Volatile
    var path: String = ""

    /** The executable to pass to ProcessBuilder — the configured path, or "adb" when unset. */
    fun exe(): String = path.ifBlank { "adb" }
}

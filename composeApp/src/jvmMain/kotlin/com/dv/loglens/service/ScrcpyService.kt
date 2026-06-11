package com.dv.loglens.service

import com.dv.loglens.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launches [scrcpy](https://github.com/Genymobile/scrcpy) to mirror a device in its own window.
 * scrcpy is a separate native app (it cannot be embedded into this Compose/Skia window), so this
 * just spawns it and lets it manage its own window and input — full quality, zero video code here.
 */
object ScrcpyService {
    /**
     * Spawn `scrcpy [-s serial]`, mirroring [serial] in a new window. Fire-and-forget: scrcpy lives
     * until the user closes its window. Returns false if scrcpy isn't installed / not on PATH.
     */
    suspend fun launch(serial: String?): Boolean = withContext(Dispatchers.IO) {
        val cmd = buildList {
            add("scrcpy")
            if (serial != null) { add("-s"); add(serial) }
        }
        try {
            val pb = ProcessBuilder(cmd)
                // Detach the pipes: a fire-and-forget GUI process we never read would otherwise
                // deadlock once its output buffer fills.
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
            // Keep scrcpy on the same adb the app uses when a custom path is configured (scrcpy
            // honours the ADB env var), so both talk to the same server/device.
            if (Adb.path.isNotBlank()) pb.environment()["ADB"] = Adb.exe()
            pb.start()
            true
        } catch (e: Exception) {
            Log.w("failed to launch scrcpy (is it installed and on PATH?)", e)
            false
        }
    }
}

package com.dv.loglens.service

import com.dv.loglens.model.ConnectionType
import com.dv.loglens.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launches [scrcpy](https://github.com/Genymobile/scrcpy) to mirror a device in its own window.
 * scrcpy is a separate native app (it cannot be embedded into this Compose/Skia window), so this
 * just spawns it and lets it manage its own window and input — full quality, zero video code here.
 */
object ScrcpyService {
    // Tuning over a wireless transport: H.265 + a 12 Mbps cap + 60 fps to stay smooth on the limited,
    // higher-latency link. Over USB the bandwidth is ample, so scrcpy's defaults are left in place.
    // Both keep the device awake (--keep-active), enable UHID keyboard (-K) and drop audio forwarding.
    private val WIFI_FLAGS =
        listOf("--video-codec=h265", "-m1200", "--max-fps=60", "--keep-active", "-K", "--no-audio")
    private val USB_FLAGS = listOf("--keep-active", "-K", "--no-audio")

    /**
     * Spawn scrcpy mirroring [serial] in a new window, with flags tuned for the [connection]
     * (Wi-Fi gets H.265 + a bitrate/fps cap; USB uses defaults). The device is always pinned with
     * `-s serial`, so scrcpy's own `-d`/`-e` selectors aren't needed. Fire-and-forget: scrcpy lives
     * until the user closes its window. Returns false if scrcpy isn't installed / not on PATH.
     */
    suspend fun launch(serial: String?, connection: ConnectionType?): Boolean = withContext(Dispatchers.IO) {
        val cmd = buildList {
            add(Scrcpy.exe())
            if (serial != null) { add("-s"); add(serial) }
            addAll(if (connection == ConnectionType.WIFI) WIFI_FLAGS else USB_FLAGS)
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

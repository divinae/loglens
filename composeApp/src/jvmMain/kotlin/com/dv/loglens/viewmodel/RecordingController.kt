package com.dv.loglens.viewmodel

import com.dv.loglens.service.ScreenRecordingService
import com.dv.loglens.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Snapshot of the recording-related settings, read once when a recording starts. */
data class RecordingSettings(
    val dir: String,
    val showTaps: Boolean,
    val bitrateMbps: Int,
    val size: String,
)

/**
 * Owns the `screenrecord` lifecycle (start / stop / pull), pulled out of [MainViewModel] so the
 * god view model isn't also a media-capture controller. The caller wires it to view-model state via
 * the constructor lambdas; behaviour is identical to the old inline implementation.
 */
class RecordingController(
    private val scope: CoroutineScope,
    private val isRecording: () -> Boolean,
    private val setRecording: (Boolean) -> Unit,
    private val currentSerial: () -> String?,
    private val isFileLoaded: () -> Boolean,
    private val settings: () -> RecordingSettings,
    private val showToast: (String) -> Unit,
    private val onSaved: suspend (File) -> Unit,
) {
    @Volatile
    private var recordingProcess: Process? = null
    private var recordingJob: Job? = null

    fun toggle() {
        if (isRecording()) stop() else start()
    }

    private fun start() {
        val serial = currentSerial()
        if (isRecording() || serial == null || isFileLoaded()) return
        setRecording(true)
        showToast("Recording started")
        // No ':' — illegal in sdcard (FAT/sdcardfs) filenames, screenrecord would fail to create the file.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val devicePath = "/sdcard/loglens-rec-$stamp.mp4"
        val cfg = settings()
        val dir = File(cfg.dir.ifBlank { "${System.getProperty("user.home")}/Videos/android" })
        val target = File(dir, "rec_$stamp.mp4")
        recordingJob = scope.launch(Dispatchers.IO) {
            var priorTaps: String? = null
            try {
                if (cfg.showTaps) {
                    priorTaps = ScreenRecordingService.getShowTouches(serial)
                    ScreenRecordingService.setShowTouches(serial, true)
                }
                val proc = ScreenRecordingService.start(serial, devicePath, cfg.bitrateMbps, cfg.size)
                recordingProcess = proc
                // Drain the merged output (blocks until the process ends) so the pipe never stalls
                // and any screenrecord error surfaces.
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (output.isNotBlank()) Log.i("screenrecord: ${output.trim()}")
                // Process ended (manual stop or the ~180s screenrecord limit) — fetch the file.
                if (ScreenRecordingService.pullAndCleanup(serial, devicePath, target)) {
                    onSaved(target)
                    showToast("Recording saved → ${target.name}")
                }
            } catch (e: Exception) {
                Log.w("recording failed", e)
            } finally {
                // Restore the device's prior show-touches setting (don't clobber the user's choice).
                if (cfg.showTaps) runCatching { ScreenRecordingService.setShowTouches(serial, priorTaps == "1") }
                recordingProcess = null
                setRecording(false)
            }
        }
    }

    private fun stop() {
        val serial = currentSerial()
        val proc = recordingProcess
        scope.launch(Dispatchers.IO) {
            // SIGINT the on-device process so it writes the moov atom and exits cleanly.
            ScreenRecordingService.signalStop(serial)
            // Wait for the adb client to exit on its own — that only happens once the device-side
            // screenrecord has finalized the mp4. Destroying it early makes pullAndCleanup race the
            // finalization and pull a file with no moov atom (unplayable). destroy() is a backstop
            // for when SIGINT didn't take (e.g. no pkill on device).
            if (proc != null && !proc.waitFor(10, TimeUnit.SECONDS)) {
                Log.w("screenrecord didn't exit after SIGINT; forcing destroy()")
                proc.destroy()
            }
        }
    }
}

package com.dv.loglens.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ScreenRecordingService {
    private val SIZE_REGEX = Regex("""\d+x\d+""")

    /**
     * Start `adb [-s serial] shell screenrecord [--bit-rate N] [--size WxH] <devicePath>`.
     * [bitrateMbps] <= 0 and a blank/invalid [size] are omitted so screenrecord uses its defaults.
     * Returns the running process to hold/stop.
     */
    fun start(serial: String?, devicePath: String, bitrateMbps: Int, size: String): Process {
        val cmd = buildList {
            add(Adb.exe())
            if (serial != null) { add("-s"); add(serial) }
            add("shell"); add("screenrecord")
            if (bitrateMbps > 0) { add("--bit-rate"); add((bitrateMbps * 1_000_000).toString()) }
            if (SIZE_REGEX.matches(size)) { add("--size"); add(size) }
            add(devicePath)
        }
        return ProcessBuilder(cmd).redirectErrorStream(true).start()
    }

    /** Current `show_touches` system setting ("0"/"1"), or null when unset/unreadable. */
    suspend fun getShowTouches(serial: String?): String? = withContext(Dispatchers.IO) {
        try {
            val cmd = buildList {
                add(Adb.exe())
                if (serial != null) { add("-s"); add(serial) }
                add("shell"); add("settings"); add("get"); add("system"); add("show_touches")
            }
            val out = ProcessBuilder(cmd).start().inputStream.bufferedReader().readText().trim()
            if (out.isBlank() || out == "null") null else out
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setShowTouches(serial: String?, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        val cmd = buildList {
            add(Adb.exe())
            if (serial != null) { add("-s"); add(serial) }
            add("shell"); add("settings"); add("put"); add("system"); add("show_touches"); add(if (enabled) "1" else "0")
        }
        runCatching { ProcessBuilder(cmd).start().waitFor() }
    }

    /**
     * Stop recording cleanly by signalling the on-device process: `pkill -INT screenrecord`.
     * SIGINT lets screenrecord finalize the mp4 — killing only the local adb client can corrupt it.
     */
    suspend fun signalStop(serial: String?): Unit = withContext(Dispatchers.IO) {
        val cmd = buildList {
            add(Adb.exe())
            if (serial != null) { add("-s"); add(serial) }
            add("shell"); add("pkill"); add("-INT"); add("screenrecord")
        }
        runCatching { ProcessBuilder(cmd).start().waitFor() }
    }

    /** Pull the recording off the device into [target], then delete it from the device. */
    suspend fun pullAndCleanup(serial: String?, devicePath: String, target: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                val pull = buildList {
                    add(Adb.exe())
                    if (serial != null) { add("-s"); add(serial) }
                    add("pull"); add(devicePath); add(target.absolutePath)
                }
                val ok = ProcessBuilder(pull).redirectErrorStream(true).start().waitFor() == 0 &&
                    target.length() > 0
                val rm = buildList {
                    add(Adb.exe())
                    if (serial != null) { add("-s"); add(serial) }
                    add("shell"); add("rm"); add("-f"); add(devicePath)
                }
                runCatching { ProcessBuilder(rm).start().waitFor() }
                if (!ok) target.delete()
                ok
            } catch (e: Exception) {
                System.err.println("loglens: pull recording failed: ${e.message}")
                target.delete()
                false
            }
        }
}

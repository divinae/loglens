package com.dv.loglens.service

import com.dv.loglens.model.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatService {
    @Volatile
    private var process: Process? = null

    /**
     * Stream logcat lines. A failure to launch adb is reported via [onError] (so the caller can show
     * a banner/toast) rather than emitted as a fake log line that pollutes the parsed view; the flow
     * then completes empty. A normal stop ([stop]) ends the reader without an error.
     */
    fun stream(
        deviceSerial: String? = null,
        buffers: Set<LogBuffer> = emptySet(),
        sinceTime: String? = null,
        onError: (String) -> Unit = {},
    ): Flow<String> = flow {
        val cmd = buildList {
            add(Adb.exe())
            if (deviceSerial != null) { add("-s"); add(deviceSerial) }
            add("logcat"); add("-v"); add("threadtime")
            // Resume point for reconnects: only lines at/after this "MM-DD HH:MM:SS.mmm".
            if (sinceTime != null) { add("-T"); add(sinceTime) }
            // One -b per selected buffer; empty set leaves adb on its own default selection.
            buffers.forEach { add("-b"); add(it.adbName) }
        }
        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            onError("Failed to start adb: ${e.message}. Check that ADB is installed and on your PATH.")
            return@flow
        }
        process = proc
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        reader.useLines { lines ->
            for (line in lines) {
                if (line.isNotBlank()) emit(line)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        process?.destroy()
        process = null
    }

    /** Clear the device's logcat ring buffers (`adb logcat [-b buf]... -c`) for the given buffers. */
    suspend fun clearBuffers(deviceSerial: String?, buffers: Set<LogBuffer>): Unit = withContext(Dispatchers.IO) {
        val cmd = buildList {
            add(Adb.exe())
            if (deviceSerial != null) { add("-s"); add(deviceSerial) }
            add("logcat")
            buffers.forEach { add("-b"); add(it.adbName) }
            add("-c")
        }
        runCatching { ProcessBuilder(cmd).start().waitFor() }
    }
}

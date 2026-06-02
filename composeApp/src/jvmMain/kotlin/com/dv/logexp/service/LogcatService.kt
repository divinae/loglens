package com.dv.logexp.service

import com.dv.logexp.model.LogBuffer
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

    fun stream(deviceSerial: String? = null, buffers: Set<LogBuffer> = emptySet()): Flow<String> = flow {
        try {
            val cmd = buildList {
                add(Adb.exe())
                if (deviceSerial != null) { add("-s"); add(deviceSerial) }
                add("logcat"); add("-v"); add("threadtime")
                // One -b per selected buffer; empty set leaves adb on its own default selection.
                buffers.forEach { add("-b"); add(it.adbName) }
            }
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            process = proc
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isNotBlank()) emit(line)
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.message}. Sprawdź czy ADB jest w PATH.")
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

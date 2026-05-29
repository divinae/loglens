package com.dv.logexp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

private const val INITIAL_TAIL_LINES = 2000

class LogcatService {
    @Volatile
    private var process: Process? = null

    fun stream(deviceSerial: String? = null): Flow<String> = flow {
        try {
            val cmd = buildList {
                add("adb")
                if (deviceSerial != null) { add("-s"); add(deviceSerial) }
                add("logcat"); add("-v"); add("threadtime")
                // Only tail the recent buffer instead of dumping the entire ring buffer at start,
                // which otherwise floods ingestion (and surfaces old PIDs that no longer resolve).
                add("-T"); add(INITIAL_TAIL_LINES.toString())
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
}

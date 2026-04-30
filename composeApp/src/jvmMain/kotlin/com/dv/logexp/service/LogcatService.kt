package com.dv.logexp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatService {
    @Volatile
    private var process: Process? = null

    fun stream(): Flow<String> = flow {
        try {
            val proc = ProcessBuilder("adb", "logcat", "-v", "threadtime")
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

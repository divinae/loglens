package com.dv.logexp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object PackageService {
    suspend fun fetchPidMap(deviceSerial: String? = null): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val cmd = buildList {
                add("adb")
                if (deviceSerial != null) { add("-s"); add(deviceSerial) }
                add("shell"); add("ps"); add("-A"); add("-o"); add("PID,NAME")
            }
            val process = ProcessBuilder(cmd).start()
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"), 2)
                    if (parts.size == 2 && parts[0].all { it.isDigit() }) {
                        result[parts[0]] = parts[1]
                    }
                }
            }
        } catch (_: Exception) {
            // adb missing / device disconnected — return what we have (possibly empty)
        }
        result
    }
}

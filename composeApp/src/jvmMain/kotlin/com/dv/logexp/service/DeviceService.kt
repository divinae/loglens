package com.dv.logexp.service

import com.dv.logexp.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object DeviceService {
    suspend fun list(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder("adb", "devices", "-l").start()
            BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                lines.drop(1)
                    .mapNotNull { parse(it) }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(line: String): Device? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("*")) return null
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 2) return null
        val serial = parts[0]
        val state = parts[1]
        val attrs = parts.drop(2).mapNotNull {
            val idx = it.indexOf(':')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
        }.toMap()
        return Device(
            serial = serial,
            state = state,
            model = attrs["model"]?.replace('_', ' '),
            product = attrs["product"],
        )
    }
}

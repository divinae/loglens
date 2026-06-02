package com.dv.logexp.service

import com.dv.logexp.model.ConnectionType
import com.dv.logexp.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object DeviceService {
    suspend fun list(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder(Adb.exe(), "devices", "-l").start()
            BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                lines.drop(1)
                    .mapNotNull { parse(it) }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // mDNS/TLS serials can contain spaces, e.g. "adb-XXXX-YYYY (2)._adb-tls-connect._tcp", so we
    // can't just take the first whitespace token. Anchor on the state keyword: everything before it
    // is the serial, everything after is the key:value attribute list.
    private val DEVICE_LINE = Regex(
        "^(.+?)\\s+(device|offline|unauthorized|bootloader|recovery|sideload|host|authorizing|connecting|no permissions)(?:\\s+(.*))?$"
    )

    private fun parse(line: String): Device? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("*")) return null
        val match = DEVICE_LINE.find(trimmed) ?: return null
        val serial = match.groupValues[1]
        val state = match.groupValues[2]
        val attrs = match.groupValues[3].split(Regex("\\s+")).mapNotNull {
            val idx = it.indexOf(':')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
        }.toMap()
        val connection = when {
            "usb" in attrs -> ConnectionType.USB
            // mDNS/TLS pairing names or plain host:port serials are network transports.
            "_adb-tls" in serial || "._tcp" in serial -> ConnectionType.WIFI
            IP_PORT.matches(serial) -> ConnectionType.WIFI
            else -> null
        }
        return Device(
            serial = serial,
            state = state,
            model = attrs["model"]?.replace('_', ' '),
            product = attrs["product"],
            connection = connection,
        )
    }

    private val IP_PORT = Regex("""^\d{1,3}(\.\d{1,3}){3}:\d+$""")
}

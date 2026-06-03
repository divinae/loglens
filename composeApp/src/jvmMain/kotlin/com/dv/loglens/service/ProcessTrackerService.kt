package com.dv.loglens.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.DataInputStream
import java.net.Socket

/**
 * Tracks debuggable app processes the way Android Studio does: the adb server's `track-jdwp`
 * service streams the current pid list on every change, so process starts/deaths surface
 * immediately instead of waiting for a `ps` poll. Speaks the adb smart-socket protocol directly
 * (default port 5037) — the CLI has no stable text format for this.
 */
object ProcessTrackerService {

    private const val DEFAULT_ADB_PORT = 5037
    private const val LENGTH_PREFIX_CHARS = 4
    private const val STATUS_CHARS = 4
    private const val HEX_RADIX = 16

    private fun adbPort(): Int =
        System.getenv("ANDROID_ADB_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_ADB_PORT

    /**
     * Emits the full set of debuggable pids on the device every time it changes (first emission is
     * the current baseline). Completes when the device drops or the adb server closes the stream;
     * fails silently into completion — the caller treats an ended flow as "tracker unavailable".
     */
    fun track(deviceSerial: String?): Flow<Set<String>> = flow {
        Socket("127.0.0.1", adbPort()).use { socket ->
            val out = socket.getOutputStream()
            val input = DataInputStream(socket.inputStream)

            fun send(service: String) {
                out.write("%04x%s".format(service.length, service).toByteArray(Charsets.UTF_8))
                out.flush()
            }

            fun expectOkay(): Boolean {
                val status = ByteArray(STATUS_CHARS)
                input.readFully(status)
                return status.decodeToString() == "OKAY"
            }

            send(if (deviceSerial != null) "host:transport:$deviceSerial" else "host:transport-any")
            if (!expectOkay()) return@use
            send("track-jdwp")
            if (!expectOkay()) return@use

            // Stream of hex4-length-prefixed payloads, each the full newline-separated pid list.
            while (true) {
                val lenHex = ByteArray(LENGTH_PREFIX_CHARS)
                input.readFully(lenHex)
                val len = lenHex.decodeToString().toInt(HEX_RADIX)
                val payload = ByteArray(len)
                input.readFully(payload)
                val pids = payload.decodeToString()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                emit(pids)
            }
        }
    }.flowOn(Dispatchers.IO)
}

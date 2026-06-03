package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId

/**
 * Serialises [LogEntry] rows into the Android Studio `.logcat` JSON shape so structured fields
 * (app id, tag, level, pid/tid, timestamp) survive a save/reopen round-trip — unlike a plain text
 * dump. The output is read back by [AndroidStudioLogParser].
 */
object LogcatExporter {
    private val json = Json { prettyPrint = true }

    @Serializable
    private data class Export(val logcatMessages: List<Message>)

    @Serializable
    private data class Message(val header: Header, val message: String)

    @Serializable
    private data class Header(
        val logLevel: String,
        val pid: Int,
        val tid: Int,
        val applicationId: String,
        val processName: String,
        val tag: String,
        val timestamp: Timestamp,
    )

    @Serializable
    private data class Timestamp(val seconds: Long, val nanos: Long)

    fun toJson(entries: List<LogEntry>): String {
        val messages = entries.map { e ->
            val (seconds, nanos) = epoch(e.date, e.time)
            Message(
                header = Header(
                    logLevel = logLevelWord(e.level),
                    pid = e.pid.toIntOrNull() ?: 0,
                    tid = e.tid.toIntOrNull() ?: 0,
                    applicationId = e.appName,
                    processName = e.appName,
                    tag = e.tag,
                    timestamp = Timestamp(seconds, nanos),
                ),
                message = e.message,
            )
        }
        return json.encodeToString(Export(messages))
    }

    /** Expand the single-char level back to the words Android Studio uses. */
    private fun logLevelWord(level: String): String = when (level.trim().uppercase()) {
        "V" -> "VERBOSE"
        "D" -> "DEBUG"
        "I" -> "INFO"
        "W" -> "WARN"
        "E" -> "ERROR"
        "F", "A" -> "ASSERT"
        else -> "INFO"
    }

    /**
     * Best-effort epoch from our stored date ("yyyy-MM-dd" or "MM-dd") and time ("HH:mm:ss[.SSS]").
     * Year defaults to the current one for the short logcat form. Returns (0, 0) if unparseable.
     */
    private fun epoch(date: String, time: String): Pair<Long, Long> = runCatching {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val secField = parts.getOrElse(2) { "0" }
        val sec = secField.substringBefore('.').toInt()
        val millis = secField.substringAfter('.', "").take(3).padEnd(3, '0').toIntOrNull() ?: 0

        val d = date.split("-")
        val localDate = if (d.size == 3) {
            LocalDate.of(d[0].toInt(), d[1].toInt(), d[2].toInt())
        } else {
            LocalDate.of(Year.now().value, d[0].toInt(), d[1].toInt())
        }
        val dt = LocalDateTime.of(localDate, java.time.LocalTime.of(hour, minute, sec))
        val instant = dt.atZone(ZoneId.systemDefault()).toInstant()
        instant.epochSecond to (millis * 1_000_000L)
    }.getOrDefault(0L to 0L)
}

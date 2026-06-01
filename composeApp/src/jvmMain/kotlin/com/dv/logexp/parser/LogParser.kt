package com.dv.logexp.parser

import com.dv.logexp.model.LogEntry

object LogParser {
    // adb logcat -v threadtime: "MM-dd HH:mm:ss.SSS pid tid LEVEL tag: message"
    private val pattern = Regex(
        "^(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+(.*?):?\\s(.*)$"
    )

    // App logger: "[yyyy-MM-dd HH:mm:ss]: <emoji> LEVEL: message"
    private val appPattern = Regex(
        "^\\[(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2})]:\\s*\\S*\\s*([A-Za-z]+):\\s(.*)$"
    )

    fun parse(id: Int, rawLine: String, pidToPackage: Map<String, String>): LogEntry {
        if (rawLine.startsWith("---------")) {
            return LogEntry(id = id, content = rawLine, message = rawLine, level = "I")
        }

        val trimmed = rawLine.trim()

        pattern.find(trimmed)?.let { match ->
            val (date, time, pid, tid, level, tag, message) = match.destructured
            return LogEntry(
                id = id,
                content = rawLine,
                date = date,
                time = time,
                pid = pid,
                tid = tid,
                level = level,
                tag = tag.trim(),
                message = message,
                appName = pidToPackage[pid] ?: "",
            )
        }

        appPattern.find(trimmed)?.let { match ->
            val (date, time, level, message) = match.destructured
            return LogEntry(
                id = id,
                content = rawLine,
                date = date,
                time = time,
                level = levelChar(level),
                message = message,
            )
        }

        return LogEntry(id = id, content = rawLine, message = rawLine, level = "D")
    }

    /** Map a word-style level (INFO, WARNING, ...) to the single-char codes used for colouring. */
    internal fun levelChar(level: String): String = when (level.uppercase()) {
        "INFO" -> "I"
        "WARNING", "WARN" -> "W"
        "ERROR", "ERR" -> "E"
        "DEBUG" -> "D"
        "VERBOSE", "TRACE" -> "V"
        "FATAL", "ASSERT" -> "F"
        else -> level.take(1).uppercase()
    }
}

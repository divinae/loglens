package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry

object LogParser {
    // adb logcat -v threadtime: "MM-dd HH:mm:ss.SSS pid tid LEVEL tag: message"
    private val pattern = Regex(
        "^(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+(.*?):?\\s(.*)$"
    )

    // App logger: "[yyyy-MM-dd HH:mm:ss]: <emoji> LEVEL: message"
    private val appPattern = Regex(
        "^\\[(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2})]:\\s*\\S*\\s*([A-Za-z]+):\\s(.*)$"
    )

    /**
     * Parse a single line. Unrecognised lines become a standalone entry (level D), preserving the
     * live-stream behaviour where each line is processed in isolation. For files use [parseLog],
     * which folds continuation lines into the preceding entry.
     */
    fun parse(id: Int, rawLine: String, pidToPackage: Map<String, String>): LogEntry =
        parseHeader(id, rawLine, pidToPackage)
            ?: LogEntry(id = id, content = rawLine, message = rawLine, level = "D")

    /**
     * Parse a whole log, folding multiline messages (e.g. Java stack traces) back together: a line
     * that doesn't start a new logcat record is appended to the message of the entry before it,
     * instead of becoming its own bogus entry. Used for files; live capture stays per-line.
     */
    fun parseLog(
        lines: Sequence<String>,
        startId: Int = 0,
        pidToPackage: Map<String, String> = emptyMap(),
    ): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        var id = startId
        // The entry that continuation lines attach to; null after a separator or unstructured line
        // so trailing non-log text isn't glued onto an unrelated record.
        var current: LogEntry? = null
        for (raw in lines) {
            if (raw.isBlank()) continue
            val isSeparator = raw.startsWith("---------")
            val header = parseHeader(id, raw, pidToPackage)
            if (header != null) {
                out.add(header)
                id++
                current = if (isSeparator) null else header
            } else if (current != null) {
                // Continuation of the previous record: keep the raw text in [content] and grow the
                // [message] so a stack trace stays searchable as one logical line.
                val merged = current.copy(
                    content = current.content + "\n" + raw,
                    message = if (current.message.isEmpty()) raw.trimEnd() else current.message + "\n" + raw.trimEnd(),
                )
                out[out.lastIndex] = merged
                current = merged
            } else {
                out.add(LogEntry(id = id, content = raw, message = raw, level = "D"))
                id++
                current = null
            }
        }
        return out
    }

    /** A recognised log record (threadtime, app-logger, or a `--------` separator); null otherwise. */
    private fun parseHeader(id: Int, rawLine: String, pidToPackage: Map<String, String>): LogEntry? {
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

        return null
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

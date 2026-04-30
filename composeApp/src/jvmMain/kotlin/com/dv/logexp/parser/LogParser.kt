package com.dv.logexp.parser

import com.dv.logexp.model.LogEntry

object LogParser {
    private val pattern = Regex(
        "^(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+(.*?):?\\s(.*)$"
    )

    fun parse(id: Int, rawLine: String, pidToPackage: Map<String, String>): LogEntry {
        if (rawLine.startsWith("---------")) {
            return LogEntry(id = id, content = rawLine, message = rawLine, level = "I")
        }

        val match = pattern.find(rawLine.trim())
            ?: return LogEntry(id = id, content = rawLine, message = rawLine, level = "D")

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
            appName = pidToPackage[pid] ?: ""
        )
    }
}

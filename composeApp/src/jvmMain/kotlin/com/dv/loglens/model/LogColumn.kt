package com.dv.loglens.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogColumn(val displayName: String) {
    INDEX("Index"),
    DATE("Date"),
    TIME("Time"),
    DELTA("Δt"),
    PID("PID"),
    TID("TID"),
    PROCESS("App"),
    LEVEL("Level"),
    TAG("Tag"),
    MESSAGE("Message")
}

/**
 * The value of [column] for this log entry. [LogColumn.DELTA] is relative to the previous row, so it
 * has no per-entry value here (empty); the row computes it from the preceding entry via [logDelta].
 */
fun LogEntry.cell(column: LogColumn): String = when (column) {
    LogColumn.INDEX -> id.toString()
    LogColumn.DATE -> date
    LogColumn.TIME -> time
    LogColumn.DELTA -> ""
    LogColumn.PID -> pid
    LogColumn.TID -> tid
    LogColumn.PROCESS -> appName
    LogColumn.LEVEL -> level
    LogColumn.TAG -> tag
    LogColumn.MESSAGE -> message
}

/**
 * Elapsed time from [prev] to [curr] for the Δt column, e.g. `+123ms` or `+1.45s`. Blank when there
 * is no previous row, when either side lacks a timestamp (banners/separators), or when the gap is
 * negative (a clock reset / out-of-order line). Useful for spotting stalls between consecutive logs.
 */
fun logDelta(prev: LogEntry?, curr: LogEntry): String {
    if (prev == null) return ""
    val a = prev.epochishMillis() ?: return ""
    val b = curr.epochishMillis() ?: return ""
    val d = b - a
    if (d < 0) return ""
    return if (d < 1000) {
        "+${d}ms"
    } else {
        // Two decimal places, formatted by hand so the output never depends on the default Locale.
        val whole = d / 1000
        val centis = (d % 1000) / 10
        "+$whole.${centis.toString().padStart(2, '0')}s"
    }
}

/**
 * A comparable millisecond stamp from the entry's date+time, good enough for deltas between adjacent
 * rows. The day component is approximated (`month*31 + day`) so a midnight rollover still yields a
 * positive gap; the absolute value is meaningless, only differences are. Null when there's no time.
 * Handles threadtime (`HH:mm:ss.SSS`) and the app-logger (`HH:mm:ss`) time formats.
 */
private fun LogEntry.epochishMillis(): Long? {
    if (time.isBlank()) return null
    val hms = time.split(':')
    if (hms.size < 3) return null
    val h = hms[0].toIntOrNull() ?: return null
    val m = hms[1].toIntOrNull() ?: return null
    val secAndMs = hms[2].split('.')
    val s = secAndMs[0].toIntOrNull() ?: return null
    val ms = secAndMs.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
    var millis = (((h * 60L) + m) * 60L + s) * 1000L + ms
    // Add a day offset (works for "MM-dd" and "yyyy-MM-dd") so cross-midnight deltas stay positive.
    val dParts = date.split('-')
    if (dParts.size >= 2) {
        val mm = dParts[dParts.size - 2].toIntOrNull()
        val dd = dParts[dParts.size - 1].toIntOrNull()
        if (mm != null && dd != null) millis += (mm * 31L + dd) * 86_400_000L
    }
    return millis
}

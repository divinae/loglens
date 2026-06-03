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
     * Parse a single line as a log record header, returning null when the line is a continuation
     * (e.g. a stack-trace frame). The returned entry carries a placeholder id (0); the caller
     * assigns the real id. Lets live capture fold multiline records the way [parseLog] does for
     * files — the consumer attaches null-lines to the preceding entry via [foldContinuation].
     */
    fun parseLine(rawLine: String, pidToPackage: Map<String, String>): LogEntry? =
        parseHeader(0, rawLine, pidToPackage)

    /** True when [rawLine] is a logcat buffer separator (`--------- beginning of ...`). */
    fun isSeparator(rawLine: String): Boolean = rawLine.startsWith("---------")

    // Tags whose consecutive same-process records are one logical dump (a crash/tombstone), even
    // though logcat prefixes every stack-trace line with its own header.
    private val foldedTags = setOf("AndroidRuntime", "DEBUG")

    /**
     * Attach a continuation line to [current]: keep [contentLine] in [content] and grow the
     * [message] (with [messageLine]) so a stack trace stays searchable as one logical line. Single
     * source of the fold rule, shared by [parseLog] and live capture. For raw continuation lines
     * [messageLine] defaults to [contentLine]; for a re-prefixed logcat crash line pass the parsed
     * message so the per-line `E AndroidRuntime:` prefix isn't repeated into the folded message.
     */
    fun foldContinuation(current: LogEntry, contentLine: String, messageLine: String = contentLine): LogEntry =
        current.copy(
            content = current.content + "\n" + contentLine,
            message = if (current.message.isEmpty()) messageLine.trimEnd()
            else current.message + "\n" + messageLine.trimEnd(),
        )

    /**
     * True when a freshly read line should fold into the current record instead of starting a new
     * one: a raw continuation line ([header] == null), or another line of the same crash/tombstone
     * dump (same [foldedTags] tag + pid + tid). Separators never fold.
     */
    fun shouldFold(anchor: LogEntry, header: LogEntry?, raw: String): Boolean {
        if (isSeparator(raw)) return false
        if (header == null) return true
        return anchor.tag in foldedTags &&
            header.tag == anchor.tag && header.pid == anchor.pid && header.tid == anchor.tid
    }

    /**
     * Parse a whole log, folding multiline messages (Java stack traces, native tombstones) back
     * together: a continuation line — or another line of the same crash dump — is appended to the
     * preceding entry instead of becoming its own bogus record.
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
            val header = parseHeader(id, raw, pidToPackage)
            val isSep = isSeparator(raw)
            val candidate = header ?: LogEntry(id = id, content = raw, message = raw, level = "D")
            val prev = out.lastOrNull()
            if (current != null && shouldFold(current, header, raw)) {
                val merged = foldContinuation(current, raw, header?.message ?: raw)
                out[out.lastIndex] = merged
                current = merged
            } else if (prev != null && !isSep && candidate.sameLogAs(prev)) {
                // Same log printed again back-to-back: bump the count instead of a duplicate row.
                val bumped = prev.copy(repeatCount = prev.repeatCount + 1)
                out[out.lastIndex] = bumped
                current = if (header != null) bumped else null
            } else {
                out.add(candidate)
                id++
                // Only a real record collects continuations; a separator or orphan line resets.
                current = if (header != null && !isSep) candidate else null
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

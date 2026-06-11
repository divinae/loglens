package com.dv.loglens.viewmodel

import com.dv.loglens.model.LogEntry
import com.dv.loglens.parser.LogParser

/**
 * A captured line and its parsed header ([LogParser.parseLine]), or a null header for a
 * continuation line (e.g. a stack-trace frame). [matchesFilter] and [isCrash] are precomputed off
 * the main thread for header lines (both false when [header] is null — [LogIngestor] recomputes for
 * the rare orphan case).
 */
class CapturedLine(
    val raw: String,
    val header: LogEntry?,
    val matchesFilter: Boolean = false,
    val isCrash: Boolean = false,
)

/**
 * Turns a stream of [CapturedLine]s into log records, maintaining the multiline-fold,
 * back-to-back-dedup and crash bookkeeping. Pulled out of [MainViewModel] so this cross-batch
 * folding logic — the trickiest part of capture — can be unit-tested without Compose or adb.
 *
 * The fold cursor ([foldLogsIdx]/[foldFilteredIdx]/[foldCurrent]) persists across [ingest] calls
 * because a trace can span a flush boundary, so one [LogIngestor] lives for the length of a capture
 * session. The stream is ordered and nothing appends between a header and its continuations, so the
 * record stays at the tail index we recorded and folding is O(1).
 *
 * [logs] and [filteredLogs] are mutated in place: the view model passes its observable
 * `SnapshotStateList`s, tests pass plain lists.
 */
class LogIngestor(
    private val logs: MutableList<LogEntry>,
    private val filteredLogs: MutableList<LogEntry>,
    /** Monotonic id source; only consumed when a line actually becomes a new record. */
    private val nextLogId: () -> Int,
    /** Filter predicate for orphan continuations (header lines reuse the precomputed flag). */
    private val matches: (LogEntry) -> Boolean,
    /** Crash predicate for orphan continuations (header lines reuse the precomputed flag). */
    private val isCrash: (LogEntry) -> Boolean,
    /** True if [id] is already a known crash, so a now-visible fold can join the navigator. */
    private val isKnownCrash: (Int) -> Boolean,
    /** Called once per header line (capture bookkeeping: resume timestamp, seen pids/packages). */
    private val onHeader: (LogEntry) -> Unit,
    /** A fresh crash record was appended; [visible] is whether it landed in [filteredLogs]. */
    private val registerCrash: (id: Int, visible: Boolean) -> Unit,
    /** A previously hidden crash became visible because a fold grew its message into a match. */
    private val onCrashBecameVisible: (Int) -> Unit,
) {
    private var foldCurrent: LogEntry? = null
    private var foldLogsIdx = -1
    private var foldFilteredIdx = -1

    fun ingest(batch: List<CapturedLine>) {
        for (cap in batch) {
            val header = cap.header
            if (header != null) onHeader(header)

            if (foldCurrent != null && LogParser.shouldFold(foldCurrent!!, header, cap.raw)) {
                // Continuation (or another line of the same crash dump): grow the current record in
                // place. Re-prefixed crash lines contribute only their parsed message, not the
                // repeated `E AndroidRuntime:` prefix.
                val merged = LogParser.foldContinuation(foldCurrent!!, cap.raw, header?.message ?: cap.raw)
                logs[foldLogsIdx] = merged
                if (foldFilteredIdx >= 0) {
                    filteredLogs[foldFilteredIdx] = merged
                } else if (matches(merged)) {
                    // The grown message now matches a filter it didn't before.
                    filteredLogs.add(merged)
                    foldFilteredIdx = filteredLogs.lastIndex
                    if (isKnownCrash(merged.id)) onCrashBecameVisible(merged.id)
                }
                foldCurrent = merged
            } else {
                val isSep = LogParser.isSeparator(cap.raw)
                val prev = if (foldLogsIdx >= 0) logs[foldLogsIdx] else null
                // Recognised header, or an orphan continuation with no current record → standalone
                // D entry, as in LogParser.parseLog. An id is assigned only if we actually add it
                // (a dedup bump consumes no id).
                val candidate = header
                    ?: LogEntry(id = 0, content = cap.raw, message = cap.raw, level = "D")
                if (prev != null && !isSep && candidate.sameLogAs(prev)) {
                    // Same log again back-to-back: bump the count in place, no new row.
                    val bumped = prev.copy(repeatCount = prev.repeatCount + 1)
                    logs[foldLogsIdx] = bumped
                    if (foldFilteredIdx >= 0) filteredLogs[foldFilteredIdx] = bumped
                    foldCurrent = if (header != null) bumped else null
                } else {
                    val entry = candidate.copy(id = nextLogId())
                    logs.add(entry)
                    foldLogsIdx = logs.lastIndex
                    // Reuse the producer's off-main flags for header lines; only the rare orphan
                    // (null header) is computed here.
                    val matched = if (header != null) cap.matchesFilter else matches(entry)
                    if (matched) {
                        filteredLogs.add(entry)
                        foldFilteredIdx = filteredLogs.lastIndex
                    } else {
                        foldFilteredIdx = -1
                    }
                    val crash = if (header != null) cap.isCrash else isCrash(entry)
                    if (crash) registerCrash(entry.id, foldFilteredIdx >= 0)
                    // A separator collects no continuations, so trailing text isn't glued onto an
                    // unrelated record.
                    foldCurrent = if (header != null && !isSep) entry else null
                }
            }
        }
    }
}

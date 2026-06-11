package com.dv.loglens.viewmodel

import com.dv.loglens.model.LogEntry
import com.dv.loglens.parser.LogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the capture fold/dedup/crash logic. Drives [LogIngestor] with plain lists (the view
 * model passes Compose `SnapshotStateList`s) so the cross-batch behaviour is exercised without
 * Compose or adb. Each test feeds lines exactly as the capture producer would: a parsed header
 * (or null for a continuation) plus the precomputed match/crash flags.
 */
class LogIngestorTest {

    /** Mirrors how MainViewModel.startCapture wires the ingestor, with plain collections. */
    private class Harness(
        val matches: (LogEntry) -> Boolean = { true },
        val isCrash: (LogEntry) -> Boolean = { false },
    ) {
        val logs = ArrayList<LogEntry>()
        val filtered = ArrayList<LogEntry>()
        val crashIds = HashSet<Int>()
        val visibleCrashes = ArrayList<Int>()
        val seenHeaders = ArrayList<LogEntry>()
        private var nextId = 0

        private val ingestor = LogIngestor(
            logs = logs,
            filteredLogs = filtered,
            nextLogId = { nextId++ },
            matches = matches,
            isCrash = isCrash,
            isKnownCrash = crashIds::contains,
            onHeader = { seenHeaders.add(it) },
            registerCrash = { id, visible -> crashIds.add(id); if (visible) visibleCrashes.add(id) },
            onCrashBecameVisible = { visibleCrashes.add(it) },
        )

        /** One ingest() call = one drain cycle; call repeatedly to span a flush boundary. */
        fun feed(vararg raw: String) = ingestor.ingest(raw.map(::cap))

        private fun cap(raw: String): CapturedLine {
            val h = LogParser.parseLine(raw, emptyMap())
            return if (h != null) CapturedLine(raw, h, matches(h), isCrash(h)) else CapturedLine(raw, null)
        }
    }

    @Test
    fun foldsCrashDumpThatSpansABatchBoundary() {
        val h = Harness(isCrash = { it.tag == "AndroidRuntime" })
        // Header arrives in one drain cycle, the stack frames in the next — the fold cursor must
        // survive across ingest() calls or the frames would become bogus standalone records.
        h.feed("06-02 10:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main")
        h.feed(
            "\tat com.foo.Bar.baz(Bar.java:42)",
            "\tat com.foo.Bar.main(Bar.java:7)",
        )

        assertEquals(1, h.logs.size)
        val crash = h.logs[0]
        assertTrue(crash.message.startsWith("FATAL EXCEPTION: main"))
        assertTrue(crash.message.contains("at com.foo.Bar.baz(Bar.java:42)"))
        assertTrue(crash.message.contains("at com.foo.Bar.main(Bar.java:7)"))
        assertEquals(listOf(0), h.visibleCrashes)
    }

    @Test
    fun backToBackDuplicatesCoalesceWithRepeatCount() {
        val h = Harness()
        val line = "06-02 10:00:00.000  500  500 I Tag: same message"
        h.feed(line, line, line)

        assertEquals(1, h.logs.size)
        assertEquals(3, h.logs[0].repeatCount)
    }

    @Test
    fun dedupBumpConsumesNoId() {
        val h = Harness()
        h.feed(
            "06-02 10:00:00.000  500  500 I Tag: dup",
            "06-02 10:00:00.001  500  500 I Tag: dup",
            "06-02 10:00:00.002  500  500 I Tag: different",
        )

        assertEquals(2, h.logs.size)
        // The repeat must not have burned an id — the second distinct row is id 1, not 2.
        assertEquals(0, h.logs[0].id)
        assertEquals(2, h.logs[0].repeatCount)
        assertEquals(1, h.logs[1].id)
    }

    @Test
    fun hiddenCrashBecomesVisibleWhenAFoldGrowsItIntoAMatch() {
        // Filter shows only lines whose message mentions "boom"; the crash header doesn't yet.
        val h = Harness(
            matches = { it.message.contains("boom") },
            isCrash = { it.tag == "AndroidRuntime" },
        )
        h.feed("06-02 10:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main")
        // Registered as a crash but not visible (no "boom" yet).
        assertTrue(0 in h.crashIds)
        assertEquals(emptyList(), h.visibleCrashes)
        assertEquals(0, h.filtered.size)

        h.feed("\tat com.foo.boom(Boom.java:1)")
        // The grown message now matches, so the row appears and the crash joins the navigator.
        assertEquals(1, h.filtered.size)
        assertEquals(listOf(0), h.visibleCrashes)
    }

    @Test
    fun separatorDoesNotCollectTheFollowingContinuation() {
        val h = Harness()
        h.feed(
            "--------- beginning of main",
            "06-02 10:00:00.000  1234  1234 I Tag: hello",
            "\tindented continuation",
        )

        assertEquals(2, h.logs.size)
        assertEquals("--------- beginning of main", h.logs[0].content)
        assertTrue(h.logs[1].message.contains("hello"))
        assertTrue(h.logs[1].message.contains("indented continuation"))
    }

    @Test
    fun filteredViewHoldsOnlyMatchingRowsWhileLogsHoldsAll() {
        val h = Harness(matches = { it.level == "E" })
        h.feed(
            "06-02 10:00:00.000  1  1 I Tag: info",
            "06-02 10:00:00.001  1  1 E Tag: error",
            "06-02 10:00:00.002  1  1 W Tag: warn",
        )

        assertEquals(3, h.logs.size)
        assertEquals(1, h.filtered.size)
        assertEquals("E", h.filtered[0].level)
    }

    @Test
    fun headerBookkeepingFiresForEachRecordHeader() {
        val h = Harness()
        h.feed(
            "06-02 10:00:00.000  1  1 I Tag: a",
            "\tcontinuation has no header",
            "06-02 10:00:00.001  2  2 I Tag: b",
        )

        // Two real headers observed; the continuation line carries none.
        assertEquals(2, h.seenHeaders.size)
        assertEquals(setOf("1", "2"), h.seenHeaders.map { it.pid }.toSet())
    }
}

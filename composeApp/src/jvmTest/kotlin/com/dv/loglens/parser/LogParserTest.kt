package com.dv.loglens.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogParserTest {

    @Test
    fun foldsMultilineStackTraceIntoOneEntry() {
        val lines = """
            06-02 10:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main
            	at com.foo.Bar.baz(Bar.java:42)
            	at com.foo.Bar.main(Bar.java:7)
            06-02 10:00:00.500  1234  1234 I System.out: next line
        """.trimIndent().lineSequence()

        val parsed = LogParser.parseLog(lines)

        assertEquals(2, parsed.size)
        val crash = parsed[0]
        assertEquals("E", crash.level)
        assertEquals("AndroidRuntime", crash.tag)
        assertTrue(crash.message.startsWith("FATAL EXCEPTION: main"))
        assertTrue(crash.message.contains("at com.foo.Bar.baz(Bar.java:42)"))
        assertTrue(crash.message.contains("at com.foo.Bar.main(Bar.java:7)"))
        assertEquals("next line", parsed[1].message)
    }

    @Test
    fun separatorDoesNotSwallowFollowingLines() {
        val lines = """
            --------- beginning of main
            06-02 10:00:00.000  1234  1234 I Tag: hello
            	indented continuation
        """.trimIndent().lineSequence()

        val parsed = LogParser.parseLog(lines)

        assertEquals(2, parsed.size)
        assertEquals("--------- beginning of main", parsed[0].content)
        assertTrue(parsed[1].message.contains("hello"))
        assertTrue(parsed[1].message.contains("indented continuation"))
    }

    @Test
    fun singleLineParseUnchangedForLiveStream() {
        val entry = LogParser.parse(0, "06-02 10:00:00.000  500  500 W MyTag: warn msg", emptyMap())
        assertEquals("W", entry.level)
        assertEquals("MyTag", entry.tag)
        assertEquals("warn msg", entry.message)
        assertEquals("500", entry.pid)
    }

    @Test
    fun foldsLogcatCrashBlockWhereEveryLineHasItsOwnHeader() {
        // In real `adb logcat`, every stack-frame line carries the full prefix and the AndroidRuntime
        // tag — they are not header-less continuations, yet must still collapse into one record.
        val lines = """
            06-02 10:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main
            06-02 10:00:00.000  1234  1234 E AndroidRuntime: 	at com.foo.Bar.baz(Bar.java:42)
            06-02 10:00:00.000  1234  1234 E AndroidRuntime: 	at com.foo.Bar.main(Bar.java:7)
            06-02 10:00:00.500  1234  1234 I System.out: unrelated
        """.trimIndent().lineSequence()

        val parsed = LogParser.parseLog(lines)

        assertEquals(2, parsed.size)
        val crash = parsed[0]
        assertEquals("AndroidRuntime", crash.tag)
        assertTrue(crash.message.contains("FATAL EXCEPTION: main"))
        assertTrue(crash.message.contains("at com.foo.Bar.baz(Bar.java:42)"))
        assertTrue(crash.message.contains("at com.foo.Bar.main(Bar.java:7)"))
        // The repeated per-line prefix must not leak into the folded message.
        assertFalse(crash.message.contains("AndroidRuntime"))
        assertEquals("unrelated", parsed[1].message)
    }

    @Test
    fun differentPidBreaksCrashFold() {
        val lines = """
            06-02 10:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main
            06-02 10:00:00.000  9999  9999 E AndroidRuntime: FATAL EXCEPTION: main
        """.trimIndent().lineSequence()
        assertEquals(2, LogParser.parseLog(lines).size)
    }

    @Test
    fun coalescesConsecutiveIdenticalLogs() {
        val lines = """
            06-02 10:00:00.000  1  1 I Tag: same message
            06-02 10:00:00.100  1  1 I Tag: same message
            06-02 10:00:00.200  1  1 I Tag: same message
            06-02 10:00:00.300  1  1 I Tag: different
        """.trimIndent().lineSequence()

        val parsed = LogParser.parseLog(lines)

        assertEquals(2, parsed.size)
        assertEquals(3, parsed[0].repeatCount)
        assertEquals("same message", parsed[0].message)
        assertEquals(1, parsed[1].repeatCount)
        assertEquals("different", parsed[1].message)
    }

    @Test
    fun nonConsecutiveDuplicatesAreNotCoalesced() {
        val lines = """
            06-02 10:00:00.000  1  1 I Tag: a
            06-02 10:00:00.100  1  1 I Tag: b
            06-02 10:00:00.200  1  1 I Tag: a
        """.trimIndent().lineSequence()
        val parsed = LogParser.parseLog(lines)
        assertEquals(3, parsed.size)
        assertTrue(parsed.all { it.repeatCount == 1 })
    }

    @Test
    fun parseLineReturnsNullForContinuation() {
        assertEquals(null, LogParser.parseLine("\tat com.foo.Bar.baz(Bar.java:42)", emptyMap()))
        val header = LogParser.parseLine("06-02 10:00:00.000  1  1 E AndroidRuntime: FATAL EXCEPTION: main", emptyMap())
        assertTrue(header != null)
        assertEquals("AndroidRuntime", header.tag)
    }

    @Test
    fun foldContinuationGrowsMessageAndContent() {
        val base = LogParser.parse(0, "06-02 10:00:00.000  1  1 E AndroidRuntime: FATAL EXCEPTION: main", emptyMap())
        val merged = LogParser.foldContinuation(base, "\tat com.foo.Bar.baz(Bar.java:42)")
        assertEquals(0, merged.id)
        assertTrue(merged.message.startsWith("FATAL EXCEPTION: main"))
        assertTrue(merged.message.contains("at com.foo.Bar.baz(Bar.java:42)"))
        assertTrue('\n' in merged.content)
    }
}

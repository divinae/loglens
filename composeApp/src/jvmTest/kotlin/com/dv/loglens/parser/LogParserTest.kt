package com.dv.loglens.parser

import kotlin.test.Test
import kotlin.test.assertEquals
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
}

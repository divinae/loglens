package com.dv.loglens.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogQueryTest {
    private fun log(
        content: String = "",
        level: String = "I",
        tag: String = "",
        message: String = "",
        appName: String = "",
        pid: String = "",
        tid: String = "",
    ) = LogEntry(id = 0, content = content, level = level, tag = tag, message = message, appName = appName, pid = pid, tid = tid)

    @Test
    fun emptyMatchesEverything() {
        val q = LogQuery.parse("   ")
        assertTrue(q.isEmpty)
        assertTrue(q.matches(log(content = "anything")))
    }

    @Test
    fun bareTextIsContentSubstringCaseInsensitive() {
        val q = LogQuery.parse("Timeout")
        assertTrue(q.matches(log(content = "request timeout reached")))
        assertFalse(q.matches(log(content = "all good")))
    }

    @Test
    fun fieldTermsMatchTheRightField() {
        assertTrue(LogQuery.parse("tag:Foo").matches(log(tag = "FooBar")))
        assertFalse(LogQuery.parse("tag:Foo").matches(log(message = "FooBar")))
        assertTrue(LogQuery.parse("package:com.foo").matches(log(appName = "com.foo.bar")))
        assertTrue(LogQuery.parse("app:com.foo").matches(log(appName = "com.foo.bar")))
        assertTrue(LogQuery.parse("msg:boom").matches(log(message = "kaboom")))
    }

    @Test
    fun pidAndTidAreExact() {
        assertTrue(LogQuery.parse("pid:1234").matches(log(pid = "1234")))
        assertFalse(LogQuery.parse("pid:1234").matches(log(pid = "12345")))
        assertTrue(LogQuery.parse("tid:42").matches(log(tid = "42")))
    }

    @Test
    fun levelExactAndRange() {
        assertTrue(LogQuery.parse("level:E").matches(log(level = "E")))
        assertFalse(LogQuery.parse("level:E").matches(log(level = "W")))
        // word forms
        assertTrue(LogQuery.parse("level:error").matches(log(level = "E")))
        // severity range V<D<I<W<E<F
        assertTrue(LogQuery.parse("level>=W").matches(log(level = "E")))
        assertTrue(LogQuery.parse("level>=W").matches(log(level = "W")))
        assertFalse(LogQuery.parse("level>=W").matches(log(level = "I")))
        assertTrue(LogQuery.parse("level<I").matches(log(level = "D")))
        // assert ranks with fatal (top)
        assertTrue(LogQuery.parse("level>=E").matches(log(level = "A")))
    }

    @Test
    fun negation() {
        assertFalse(LogQuery.parse("-tag:Spam").matches(log(tag = "Spam")))
        assertTrue(LogQuery.parse("-tag:Spam").matches(log(tag = "Ham")))
        assertFalse(LogQuery.parse("-boom").matches(log(content = "boom here")))
    }

    @Test
    fun impliedAndAcrossTerms() {
        val q = LogQuery.parse("level>=W tag:Net -msg:retry")
        assertTrue(q.matches(log(level = "E", tag = "NetClient", message = "connection failed")))
        assertFalse(q.matches(log(level = "E", tag = "NetClient", message = "retry scheduled")))
        assertFalse(q.matches(log(level = "I", tag = "NetClient", message = "ok")))
    }

    @Test
    fun quotedPhraseKeepsSpaces() {
        val q = LogQuery.parse("msg:\"out of memory\"")
        assertTrue(q.matches(log(message = "fatal: out of memory error")))
        assertFalse(q.matches(log(message = "out of bounds")))
    }

    @Test
    fun regexOnField() {
        val q = LogQuery.parse("tag:/Foo.*Bar/")
        assertTrue(q.matches(log(tag = "FooXyzBar")))
        assertFalse(q.matches(log(tag = "BarFoo")))
        assertNull(q.error)
    }

    @Test
    fun unknownFieldFallsBackToContent() {
        // "http://x" must not be treated as field "http"
        val q = LogQuery.parse("http://example")
        assertTrue(q.matches(log(content = "GET http://example/api")))
    }

    @Test
    fun badRegexFallsBackToContentSubstringWithError() {
        val q = LogQuery.parse("tag:/[unclosed/")
        assertNotNull(q.error)
        // whole raw text used as a content substring fallback
        assertTrue(q.matches(log(content = "tag:/[unclosed/ literal")))
    }

    @Test
    fun aliasesResolve() {
        assertEquals(
            LogQuery.parse("pkg:com.x").matches(log(appName = "com.x")),
            LogQuery.parse("package:com.x").matches(log(appName = "com.x")),
        )
        assertTrue(LogQuery.parse("message:hi").matches(log(message = "hi there")))
        assertTrue(LogQuery.parse("lvl:E").matches(log(level = "E")))
    }
}

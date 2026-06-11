package com.dv.loglens.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for the Δt column's elapsed-time formatting ([logDelta]). */
class LogColumnTest {

    private fun at(date: String, time: String) =
        LogEntry(id = 0, content = "", date = date, time = time)

    @Test
    fun subSecondGapIsShownInMillis() {
        val a = at("06-02", "10:00:00.000")
        val b = at("06-02", "10:00:00.123")
        assertEquals("+123ms", logDelta(a, b))
    }

    @Test
    fun multiSecondGapIsShownInSecondsWithTwoDecimals() {
        val a = at("06-02", "10:00:00.000")
        val b = at("06-02", "10:00:01.450")
        assertEquals("+1.45s", logDelta(a, b))
    }

    @Test
    fun noPreviousRowMeansNoDelta() {
        assertEquals("", logDelta(null, at("06-02", "10:00:00.000")))
    }

    @Test
    fun missingTimestampMeansNoDelta() {
        // A banner/separator has no time.
        val banner = at("", "")
        assertEquals("", logDelta(banner, at("06-02", "10:00:00.000")))
        assertEquals("", logDelta(at("06-02", "10:00:00.000"), banner))
    }

    @Test
    fun negativeGapIsSuppressed() {
        val a = at("06-02", "10:00:01.000")
        val b = at("06-02", "10:00:00.000")
        assertEquals("", logDelta(a, b))
    }

    @Test
    fun crossesMidnightWithoutGoingNegative() {
        val a = at("06-02", "23:59:59.500")
        val b = at("06-03", "00:00:00.500")
        assertEquals("+1.00s", logDelta(a, b))
    }

    @Test
    fun appLoggerSecondResolutionStillWorks() {
        // App-logger timestamps have no millis fraction.
        val a = at("2026-06-02", "10:00:00")
        val b = at("2026-06-02", "10:00:05")
        assertEquals("+5.00s", logDelta(a, b))
    }
}

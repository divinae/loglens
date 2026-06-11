package com.dv.loglens.parser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdbNoiseTest {

    @Test
    fun detectsAdbErrorAndStatusLines() {
        val noise = listOf(
            "adb: device 'ABC123' not found",
            "error: device offline",
            "error: no devices/emulators found",
            "* daemon not running; starting now at tcp:5037",
            "* daemon started successfully",
            "- waiting for device -",
            "adb server version (41) doesn't match this client (39); killing...",
            "cannot connect to daemon at tcp:5037",
        )
        noise.forEach { assertTrue(LogParser.isAdbNoise(it), "should be noise: $it") }
    }

    @Test
    fun keepsRealLogcatLines() {
        val real = listOf(
            "06-11 10:02:47.123  1234  1234 E HttpClient: error: 500 from server",
            "06-11 10:02:47.123  1234  1234 I System.out: adb: connected",
            "--------- beginning of main",
        )
        real.forEach { assertFalse(LogParser.isAdbNoise(it), "should be kept: $it") }
    }

    @Test
    fun classifiesErrorsVsSilentPlumbing() {
        assertTrue(LogParser.isAdbError("adb: device not found"))
        assertTrue(LogParser.isAdbError("error: device offline"))
        assertFalse(LogParser.isAdbError("* daemon started successfully"))
        assertFalse(LogParser.isAdbError("- waiting for device -"))
    }
}

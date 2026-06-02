package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashDetectorTest {

    @Test
    fun detectsJavaFatalException() {
        val e = LogEntry(id = 1, content = "", level = "E", tag = "AndroidRuntime", message = "FATAL EXCEPTION: main")
        assertTrue(CrashDetector.isCrash(e))
    }

    @Test
    fun detectsAnr() {
        val e = LogEntry(id = 1, content = "", level = "E", tag = "ActivityManager", message = "ANR in com.foo (com.foo/.MainActivity)")
        assertTrue(CrashDetector.isCrash(e))
    }

    @Test
    fun detectsNativeFatalSignal() {
        val e = LogEntry(id = 1, content = "", level = "F", tag = "libc", message = "Fatal signal 11 (SIGSEGV)")
        assertTrue(CrashDetector.isCrash(e))
    }

    @Test
    fun ignoresOrdinaryLogs() {
        val e = LogEntry(id = 1, content = "", level = "I", tag = "System.out", message = "everything is fine")
        assertFalse(CrashDetector.isCrash(e))
        // "AndroidRuntime" without the FATAL marker isn't a crash.
        val warn = LogEntry(id = 2, content = "", level = "W", tag = "AndroidRuntime", message = "Shutting down VM")
        assertFalse(CrashDetector.isCrash(warn))
    }
}

package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry

/**
 * Recognises the header line of a crash or ANR so the UI can mark it and offer jump-to navigation.
 * Markers live on the first (header) line of a record, so this only ever needs the parsed entry —
 * continuation lines (stack frames) are folded into the same entry's [LogEntry.message].
 */
object CrashDetector {
    fun isCrash(e: LogEntry): Boolean {
        val msg = e.message
        // Java uncaught exception.
        if (e.tag == "AndroidRuntime" && (e.level == "E" || e.level == "F") &&
            msg.contains("FATAL EXCEPTION")
        ) return true
        // App Not Responding, e.g. "ANR in com.foo (com.foo/.MainActivity)".
        if (msg.startsWith("ANR in ")) return true
        // Native crash: libc abort message, or a tombstone dump from the DEBUG pseudo-process.
        if (msg.contains("Fatal signal ")) return true
        if (e.tag == "DEBUG" && msg.contains("*** *** ***")) return true
        return false
    }
}

package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry

/**
 * Parser for Android bugreports / `dumpstate` text dumps. A bugreport is one big text file split
 * into sections by `------ <NAME> ------` headers; the logcat output lives in sections whose header
 * names the `logcat` command, e.g.
 *
 *     ------ SYSTEM LOG (logcat -b system -v threadtime -d *:v) ------
 *     <threadtime lines>
 *     ------ 0.123s was the duration of 'SYSTEM LOG' ------
 *
 * We pull every such section, concatenate them in file order, and parse with [LogParser.parseLog]
 * (so multiline stack traces fold correctly). The `ps`/processes section is mined for a pid→name
 * map so app-name filtering works on offline dumps too.
 */
object BugreportParser {

    /** True if [text] looks like a bugreport/dumpstate containing embedded logcat sections. */
    fun matches(text: String): Boolean {
        val head = text.take(8192)
        return head.contains("== dumpstate:") || text.contains("(logcat ")
    }

    /** Parse the embedded logcat sections; null if none were found. */
    fun parse(text: String, startId: Int = 0): List<LogEntry>? {
        val collected = ArrayList<String>()
        var inLogcat = false
        for (line in text.lineSequence()) {
            // Only `------ NAME ------` is a section header. The `--------- beginning of main`
            // logcat separator also starts with dashes but isn't a header — don't let it close a
            // section, or the records right after it get dropped.
            if (SECTION_HEADER.matches(line)) {
                // A header opens a logcat section iff it names the logcat command; any other header
                // (including the "was the duration of" footer) closes the current one.
                inLogcat = line.contains("(logcat ")
                continue
            }
            if (inLogcat) collected.add(line)
        }
        if (collected.isEmpty()) return null
        return LogParser.parseLog(collected.asSequence(), startId, parseProcessMap(text))
    }

    /**
     * Best-effort pid→process-name map from the bugreport's `ps` section. `ps -A` rows look like
     * `USER PID PPID VSZ RSS WCHAN ADDR S NAME` — column 2 is the pid, the last column the name.
     */
    private fun parseProcessMap(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        var inProc = false
        for (line in text.lineSequence()) {
            if (SECTION_HEADER.matches(line)) {
                inProc = line.contains("PROCESSES") && line.contains("ps ")
                continue
            }
            if (!inProc) continue
            val cols = line.trim().split(WHITESPACE)
            if (cols.size < 3) continue
            val pid = cols[1]
            if (pid.toIntOrNull() == null) continue // header row ("USER PID ...") and blanks fall out here
            val name = cols.last()
            if (name.isNotEmpty()) map[pid] = name
        }
        return map
    }

    private val WHITESPACE = Regex("\\s+")
    private val SECTION_HEADER = Regex("^------ .* ------\\s*$")
}

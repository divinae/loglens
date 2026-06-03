package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry
import java.io.File
import java.util.zip.ZipFile

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

    /**
     * `adb bugreport` ships a zip whose root holds the flat `bugreport-<device>-<date>.txt` report
     * (alongside `FS/`, `version.txt`, `dumpstate_log.txt`, ...). If [file] is such a zip, return the
     * report's text; null if it isn't a zip or has no usable text report. Call off the UI thread.
     */
    fun extractFromZip(file: File): String? {
        if (!isZip(file)) return null
        return runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                // Prefer the canonical bugreport-*.txt; fall back to the largest .txt in the archive.
                val entry = entries
                    .filter { val n = it.name.substringAfterLast('/'); n.startsWith("bugreport") && n.endsWith(".txt") }
                    .maxByOrNull { it.size }
                    ?: entries.filter { it.name.endsWith(".txt") }.maxByOrNull { it.size }
                    ?: return null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }.getOrNull()
    }

    /** Cheap zip sniff by the local-file-header magic `PK`, so a `.txt` mislabelled as zip (or vice-versa) is judged by content, not extension. */
    private fun isZip(file: File): Boolean = runCatching {
        file.inputStream().use { ins ->
            ins.read() == 0x50 && ins.read() == 0x4B && ins.read() == 0x03 && ins.read() == 0x04
        }
    }.getOrDefault(false)

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

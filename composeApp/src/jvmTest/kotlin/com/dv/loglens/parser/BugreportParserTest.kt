package com.dv.loglens.parser

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BugreportParserTest {

    private val sample = """
        ========================================================
        == dumpstate: 2026-06-02 10:00:00
        ========================================================

        ------ MEMORY INFO (/proc/meminfo) ------
        MemTotal:        8000000 kB
        ------ 0.001s was the duration of 'MEMORY INFO' ------

        ------ PROCESSES (ps -A) ------
        USER     PID   PPID  VSZ    RSS   WCHAN  ADDR  S NAME
        u0_a10   1234  900   100    50    0      0     S com.foo.app
        root     1     0     50     10    0      0     S init
        ------ 0.002s was the duration of 'PROCESSES' ------

        ------ SYSTEM LOG (logcat -b system -v threadtime -d *:v) ------
        --------- beginning of system
        06-02 10:00:00.000  1234  1234 I ActivityManager: started
        06-02 10:00:00.100  1234  1234 E AndroidRuntime: boom
        	at com.foo.app.Main.run(Main.java:1)
        ------ 0.123s was the duration of 'SYSTEM LOG' ------

        ------ EVENT LOG (logcat -b events -v threadtime -d *:v) ------
        06-02 10:00:01.000  1234  1234 I tag_event: hi
        ------ 0.050s was the duration of 'EVENT LOG' ------
    """.trimIndent()

    @Test
    fun detectsBugreport() {
        assertTrue(BugreportParser.matches(sample))
        assertFalseLike(BugreportParser.matches("06-02 10:00:00.000  1 1 I T: plain logcat"))
    }

    @Test
    fun extractsLogcatSectionsAndFoldsMultiline() {
        val parsed = assertNotNull(BugreportParser.parse(sample))
        // separator + 2 system records (crash folded) + 1 event record = 4
        assertEquals(4, parsed.size)
        val crash = parsed.first { it.tag == "AndroidRuntime" }
        assertTrue(crash.message.contains("boom"))
        assertTrue(crash.message.contains("at com.foo.app.Main.run(Main.java:1)"))
        assertTrue(parsed.any { it.tag == "tag_event" })
    }

    @Test
    fun mapsPidToProcessName() {
        val parsed = assertNotNull(BugreportParser.parse(sample))
        val am = parsed.first { it.tag == "ActivityManager" }
        assertEquals("com.foo.app", am.appName)
    }

    @Test
    fun returnsNullWhenNoLogcatSections() {
        val noLogcat = """
            ------ MEMORY INFO (/proc/meminfo) ------
            MemTotal: 1 kB
        """.trimIndent()
        assertNull(BugreportParser.parse(noLogcat))
    }

    @Test
    fun extractsReportFromAdbZip() {
        val zip = File.createTempFile("bugreport-test", ".zip").apply { deleteOnExit() }
        ZipOutputStream(zip.outputStream()).use { out ->
            // Decoy entries the real archive carries; the parser must pick the bugreport-*.txt.
            out.putNextEntry(ZipEntry("version.txt")); out.write("11".toByteArray()); out.closeEntry()
            out.putNextEntry(ZipEntry("bugreport-emu64-2026.txt")); out.write(sample.toByteArray()); out.closeEntry()
        }
        val text = assertNotNull(BugreportParser.extractFromZip(zip))
        assertTrue(text.contains("ActivityManager"))
        val parsed = assertNotNull(BugreportParser.parse(text))
        assertTrue(parsed.any { it.tag == "ActivityManager" })
        zip.delete()
    }

    @Test
    fun extractFromZipIgnoresPlainTextFile() {
        val txt = File.createTempFile("plain", ".txt").apply { deleteOnExit() }
        txt.writeText(sample)
        assertNull(BugreportParser.extractFromZip(txt))
        txt.delete()
    }

    private fun assertFalseLike(value: Boolean) = assertTrue(!value)
}

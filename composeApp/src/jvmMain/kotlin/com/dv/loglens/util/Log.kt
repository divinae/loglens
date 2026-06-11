package com.dv.loglens.util

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal app logger: a single place that emits diagnostics, instead of `System.err.println`
 * scattered across the codebase. Every line is prefixed with `loglens:` and a level, written to
 * [sink] (stderr by default) and appended to a rotating log file on disk so a past session's
 * diagnostics survive after the console is gone — useful when a user reports a bug.
 *
 * Dependency-free by design (no slf4j/logback) for a single-binary desktop app. [sink] is swappable
 * so a future in-app diagnostics panel (or a test) can capture output.
 */
object Log {
    /** Where formatted lines go for live output; defaults to stderr. The file is written too. */
    @Volatile
    var sink: (String) -> Unit = System.err::println

    // Roll the file over at ~1 MB, keeping one previous generation (loglens.log + loglens.log.1).
    private const val MAX_BYTES = 1_000_000L
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val fileLock = Any()

    /** Path to the on-disk log, or null if it couldn't be created (logging then stays stderr-only). */
    val logFilePath: String? get() = logFile?.absolutePath

    private val logFile: File? by lazy {
        runCatching {
            val dir = File(System.getProperty("user.home"), ".config/loglens")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "loglens.log").also { it.parentFile?.mkdirs() }
        }.getOrNull()
    }

    fun i(message: String) = emit("I", message, null)
    fun w(message: String, error: Throwable? = null) = emit("W", message, error)
    fun e(message: String, error: Throwable? = null) = emit("E", message, error)

    private fun emit(level: String, message: String, error: Throwable?) {
        val suffix = error?.message?.let { ": $it" }.orEmpty()
        val line = "loglens: [$level] $message$suffix"
        sink(line)
        appendToFile(level, line, error)
    }

    private fun appendToFile(level: String, line: String, error: Throwable?) {
        val file = logFile ?: return
        synchronized(fileLock) {
            runCatching {
                if (file.length() > MAX_BYTES) {
                    val rolled = File(file.parentFile, "${file.name}.1")
                    rolled.delete()
                    file.renameTo(rolled)
                }
                val stamp = LocalDateTime.now().format(timestampFormat)
                file.appendText("$stamp $line\n")
                // Error logs keep the stack trace in the file (but not in the terse sink line).
                if (error != null && level == "E") file.appendText(error.stackTraceToString())
            }
        }
    }
}

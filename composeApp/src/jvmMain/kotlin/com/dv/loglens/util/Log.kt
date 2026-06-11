package com.dv.loglens.util

/**
 * Minimal app logger: a single place that emits diagnostics, instead of `System.err.println`
 * scattered across the codebase. Every line is prefixed with `loglens:` and a level. Kept
 * dependency-free (no slf4j) for a single-binary desktop app — [sink] is swappable so a future
 * in-app diagnostics panel (or a test) can capture output instead of writing to stderr.
 */
object Log {
    /** Where formatted lines go; defaults to stderr. */
    @Volatile
    var sink: (String) -> Unit = System.err::println

    fun i(message: String) = emit("I", message, null)
    fun w(message: String, error: Throwable? = null) = emit("W", message, error)
    fun e(message: String, error: Throwable? = null) = emit("E", message, error)

    private fun emit(level: String, message: String, error: Throwable?) {
        val suffix = error?.message?.let { ": $it" }.orEmpty()
        sink("loglens: [$level] $message$suffix")
    }
}

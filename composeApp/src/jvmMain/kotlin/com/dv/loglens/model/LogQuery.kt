package com.dv.loglens.model

import com.dv.loglens.parser.CrashDetector
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Parsed quick-filter query. Supports a small field query language layered over the old
 * "bare substring on content" behaviour, so plain text keeps working unchanged.
 *
 * Grammar (terms are implicitly AND-ed):
 *   foobar              bare → substring on content (case-insensitive)
 *   "two words"         quoted phrase (may contain spaces)
 *   tag:Foo             tag contains "Foo"
 *   package:com.foo     appName contains (aliases: app, pkg)
 *   msg:timeout         message contains (alias: message)
 *   pid:1234 tid:42     exact numeric match
 *   level:E             level == E
 *   level>=W            level severity >= W   (V<D<I<W<E<F, A==F)
 *   tag:/Foo.*Bar/      /regex/ on the field (case-insensitive)
 *   tag~:Foo.*Bar       ~: treats the value as a regex (same as the /…/ form)
 *   is:crash            crash/ANR/native-crash records; is:stacktrace = records with a stack trace
 *   age:5m              records from the last 30s / 5m / 2h / 1d (evaluated against query time)
 *   -tag:Spam           negate any term (also bare: -foobar)
 *
 * Parsed once per query change; [matches] is allocation-free per log line.
 */
class LogQuery private constructor(
    val text: String,
    /** Non-null when the query had a syntax/regex error; then [matches] falls back to a plain content substring of [text]. */
    val error: String?,
    private val terms: List<(LogEntry) -> Boolean>,
) {
    fun matches(log: LogEntry): Boolean {
        for (term in terms) if (!term(log)) return false
        return true
    }

    val isEmpty: Boolean get() = terms.isEmpty()

    companion object {
        val EMPTY = LogQuery("", null, emptyList())

        private enum class Field { CONTENT, MESSAGE, TAG, APP, PID, TID, LEVEL }

        private val FIELDS = mapOf(
            "level" to Field.LEVEL, "lvl" to Field.LEVEL,
            "tag" to Field.TAG,
            "app" to Field.APP, "package" to Field.APP, "pkg" to Field.APP,
            "pid" to Field.PID,
            "tid" to Field.TID,
            "msg" to Field.MESSAGE, "message" to Field.MESSAGE,
        )

        /** V<D<I<W<E<F; A (assert) ranks with F. */
        private val LEVEL_RANK = mapOf("V" to 0, "D" to 1, "I" to 2, "W" to 3, "E" to 4, "F" to 5, "A" to 5)

        private fun rankOf(token: String): Int? {
            val canonical = when (token.uppercase()) {
                "VERBOSE", "TRACE", "V" -> "V"
                "DEBUG", "D" -> "D"
                "INFO", "I" -> "I"
                "WARNING", "WARN", "W" -> "W"
                "ERROR", "ERR", "E" -> "E"
                "FATAL", "F" -> "F"
                "ASSERT", "A" -> "A"
                else -> return null
            }
            return LEVEL_RANK[canonical]
        }

        // field, then comparison/colon operator, then the rest. `~:` marks a regex value.
        private val TOKEN_REGEX = Regex("^([A-Za-z]+)(~:|>=|<=|!=|>|<|=|:)(.*)$")

        /** Lines like "\tat com.foo.Bar.baz(Bar.kt:12)" — the JVM stack-frame shape. */
        private val STACK_FRAME = Regex("""(^|\n)\s*at\s+[\w.$<>]+\(""")

        private val AGE_VALUE = Regex("""^(\d+)([smhd])$""")

        /** "MM-dd HH:mm:ss.SSS" — matches the threadtime columns, zero-padded so strings order. */
        private val AGE_CUTOFF_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L
        private const val SECONDS_PER_DAY = 86_400L

        /** Length of the "MM-dd" date prefix in [AGE_CUTOFF_FORMAT]. */
        private const val DATE_LENGTH = 5

        fun parse(text: String): LogQuery {
            if (text.isBlank()) return EMPTY
            val terms = ArrayList<(LogEntry) -> Boolean>()
            try {
                for (token in tokenize(text)) {
                    var t = token
                    var negate = false
                    if (t.length > 1 && t[0] == '-') {
                        negate = true
                        t = t.substring(1)
                    }
                    val term = buildTerm(t)
                    terms.add(if (negate) { log -> !term(log) } else term)
                }
            } catch (e: Exception) {
                // Bad regex etc. — fall back to treating the whole input as a content substring.
                return LogQuery(text, e.message ?: "invalid query", listOf(substringTerm({ it.content }, text)))
            }
            return LogQuery(text, null, terms)
        }

        private fun buildTerm(t: String): (LogEntry) -> Boolean {
            val m = TOKEN_REGEX.find(t)
            val key = m?.groupValues?.get(1)?.lowercase()
            val field = key?.let { FIELDS[it] }
            return when {
                m != null && key == "is" -> buildIsTerm(m.groupValues[3])
                m != null && key == "age" -> buildAgeTerm(m.groupValues[3])
                m != null && field != null -> buildFieldTerm(field, m.groupValues[2], m.groupValues[3])
                // Bare token (or unknown field like "http://x") → substring on content.
                else -> substringTerm({ it.content }, t)
            }
        }

        private fun buildFieldTerm(field: Field, op: String, rawValue: String): (LogEntry) -> Boolean {
            if (field == Field.LEVEL) return buildLevelTerm(op, rawValue)

            val selector: (LogEntry) -> String = when (field) {
                Field.MESSAGE -> { it -> it.message }
                Field.TAG -> { it -> it.tag }
                Field.APP -> { it -> it.appName }
                Field.PID -> { it -> it.pid }
                Field.TID -> { it -> it.tid }
                else -> { it -> it.content }
            }
            val pattern = regexPatternOf(op, rawValue)
            if (pattern != null) {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                return { log -> regex.containsMatchIn(selector(log)) }
            }
            // pid/tid: exact numeric equality; others: substring.
            if (field == Field.PID || field == Field.TID) {
                return { log -> selector(log) == rawValue }
            }
            return substringTerm(selector, rawValue)
        }

        private fun buildLevelTerm(op: String, rawValue: String): (LogEntry) -> Boolean {
            val target = rankOf(rawValue) ?: return { true } // unknown level word → no-op
            return { log ->
                val r = rankOf(log.level)
                if (r == null) false else when (op) {
                    ">=" -> r >= target
                    "<=" -> r <= target
                    ">" -> r > target
                    "<" -> r < target
                    "!=" -> r != target
                    else -> r == target // ":" or "="
                }
            }
        }

        /** `tag~:Foo.*` and a /slash-wrapped/ value both treat it as a regex; null = not a regex term. */
        private fun regexPatternOf(op: String, rawValue: String): String? = when {
            op == "~:" -> rawValue
            rawValue.length >= 2 && rawValue.first() == '/' && rawValue.last() == '/' ->
                rawValue.substring(1, rawValue.length - 1)
            else -> null
        }

        /** `is:crash` (CrashDetector classes) / `is:stacktrace` (record carries JVM stack frames). */
        private fun buildIsTerm(value: String): (LogEntry) -> Boolean = when (value.lowercase()) {
            "crash" -> { log -> CrashDetector.isCrash(log) }
            "stacktrace" -> { log -> STACK_FRAME.containsMatchIn(log.message) }
            else -> { _ -> true } // unknown class → no-op, like an unknown level word
        }

        /**
         * `age:5m` — only records newer than now-minus-duration at parse time. The threadtime
         * date+time columns are zero-padded, so one cutoff string compares lexicographically;
         * entries without a timestamp never match. Recomputed whenever the query reparses.
         */
        private fun buildAgeTerm(value: String): (LogEntry) -> Boolean {
            val m = AGE_VALUE.find(value.lowercase())
                ?: throw IllegalArgumentException("age wants e.g. 30s, 5m, 2h, 1d")
            val amount = m.groupValues[1].toLong()
            val unitSeconds = when (m.groupValues[2]) {
                "s" -> 1L
                "m" -> SECONDS_PER_MINUTE
                "h" -> SECONDS_PER_HOUR
                else -> SECONDS_PER_DAY
            }
            val cutoff = LocalDateTime.now().minusSeconds(amount * unitSeconds).format(AGE_CUTOFF_FORMAT)
            // "MM-dd HH:mm:ss.SSS" → date is the first 5 chars, time starts after the space.
            val cutoffDate = cutoff.substring(0, DATE_LENGTH)
            val cutoffTime = cutoff.substring(DATE_LENGTH + 1)
            return { log ->
                log.time.isNotEmpty() &&
                    (log.date > cutoffDate || (log.date == cutoffDate && log.time >= cutoffTime))
            }
        }

        private fun substringTerm(selector: (LogEntry) -> String, needle: String): (LogEntry) -> Boolean {
            if (needle.isEmpty()) return { true }
            return { log -> selector(log).contains(needle, ignoreCase = true) }
        }

        /** Split on whitespace, keeping "quoted phrases" (quote chars dropped, inner spaces kept). */
        private fun tokenize(s: String): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            var inQuote = false
            for (ch in s) {
                when {
                    ch == '"' -> inQuote = !inQuote
                    ch.isWhitespace() && !inQuote -> {
                        if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
                    }
                    else -> sb.append(ch)
                }
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            return out
        }
    }
}

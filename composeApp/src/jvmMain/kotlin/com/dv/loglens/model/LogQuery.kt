package com.dv.loglens.model

import com.dv.loglens.parser.CrashDetector
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Parsed quick-filter query. Supports a small field query language layered over the old
 * "bare substring on content" behaviour, so plain text keeps working unchanged.
 *
 * Grammar (terms within a group are implicitly AND-ed; a top-level `|` separates OR groups):
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
 *   tag:Foo | tag:Bar   a standalone `|` ORs groups; AND binds tighter than OR.
 *                       `a b | c d` = (a AND b) OR (c AND d). `|` must be whitespace-delimited;
 *                       a `|` inside a token (e.g. tag:/Foo|Bar/) stays a literal regex alternation.
 *
 * Parsed once per query change; [matches] is allocation-free per log line.
 */
class LogQuery private constructor(
    val text: String,
    /** Non-null when the query had a syntax/regex error; then [matches] falls back to a plain content substring of [text]. */
    val error: String?,
    /** OR groups: a log matches when ANY group matches (all of that group's terms are AND-ed). */
    private val groups: List<List<(LogEntry) -> Boolean>>,
    /** Text patterns from positive content/text terms, used to paint find highlights; see [matchRanges]. */
    private val highlighters: List<Highlighter>,
) {
    fun matches(log: LogEntry): Boolean {
        if (groups.isEmpty()) return true
        for (group in groups) {
            var all = true
            for (term in group) if (!term(log)) { all = false; break }
            if (all) return true
        }
        return false
    }

    val isEmpty: Boolean get() = groups.isEmpty()

    /**
     * Inclusive `[first..last]` offsets of every highlightable term within [text], for the find bar's
     * in-cell highlighting. Only positive text-bearing terms contribute (bare words, quoted phrases,
     * substring/regex field values); gating-only terms (level/pid/tid/is/age) and negations paint
     * nothing. Patterns are matched against whatever cell text is passed, so a term highlights wherever
     * its text appears. Empty list when the query carries no highlightable term.
     */
    fun matchRanges(text: String): List<IntRange> {
        if (highlighters.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        for (h in highlighters) h.appendRanges(text, out)
        return out
    }

    /** A literal substring (case-insensitive) or a regex to highlight in cell text. */
    private class Highlighter private constructor(private val literal: String?, private val regex: Regex?) {
        fun appendRanges(text: String, out: MutableList<IntRange>) {
            if (regex != null) {
                for (m in regex.findAll(text)) if (!m.range.isEmpty()) out.add(m.range)
            } else if (literal != null) {
                var i = text.indexOf(literal, ignoreCase = true)
                while (i >= 0) {
                    out.add(i until i + literal.length)
                    i = text.indexOf(literal, i + literal.length, ignoreCase = true)
                }
            }
        }

        companion object {
            fun literal(s: String): Highlighter? = if (s.isEmpty()) null else Highlighter(s, null)
            fun regex(pattern: String): Highlighter = Highlighter(null, Regex(pattern, RegexOption.IGNORE_CASE))
        }
    }

    companion object {
        val EMPTY = LogQuery("", null, emptyList(), emptyList())

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
            val groups = ArrayList<List<(LogEntry) -> Boolean>>()
            var current = ArrayList<(LogEntry) -> Boolean>()
            val highlighters = ArrayList<Highlighter>()
            try {
                for (token in tokenize(text)) {
                    if (token == "|") {
                        // Close the current AND group; stray/leading `|` (empty group) is ignored.
                        if (current.isNotEmpty()) { groups.add(current); current = ArrayList() }
                        continue
                    }
                    var t = token
                    var negate = false
                    if (t.length > 1 && t[0] == '-') {
                        negate = true
                        t = t.substring(1)
                    }
                    val term = buildTerm(t)
                    current.add(if (negate) { log -> !term(log) } else term)
                    // Negated terms hide rather than mark text, so they contribute no highlight.
                    if (!negate) buildHighlighter(t)?.let { highlighters.add(it) }
                }
            } catch (e: Exception) {
                // Bad regex etc. — fall back to treating the whole input as a content substring.
                val fallback = Highlighter.literal(text)?.let { listOf(it) } ?: emptyList()
                return LogQuery(text, e.message ?: "invalid query", listOf(listOf(substringTerm({ it.content }, text))), fallback)
            }
            if (current.isNotEmpty()) groups.add(current)
            if (groups.isEmpty()) return EMPTY
            return LogQuery(text, null, groups, highlighters)
        }

        /** The highlight pattern a positive [t] paints, or null for gating-only terms (level/pid/tid/is/age). */
        private fun buildHighlighter(t: String): Highlighter? {
            val m = TOKEN_REGEX.find(t) ?: return Highlighter.literal(t) // bare token → substring on content
            val key = m.groupValues[1].lowercase()
            val op = m.groupValues[2]
            val raw = m.groupValues[3]
            val field = FIELDS[key]
            return when {
                key == "is" || key == "age" -> null
                field == Field.LEVEL || field == Field.PID || field == Field.TID -> null
                field != null -> regexPatternOf(op, raw)?.let { Highlighter.regex(it) } ?: Highlighter.literal(raw)
                // Unknown field (e.g. http://x) → buildTerm treats the whole token as a content substring.
                else -> Highlighter.literal(t)
            }
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

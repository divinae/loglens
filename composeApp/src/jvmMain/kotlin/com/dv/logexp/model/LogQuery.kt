package com.dv.logexp.model

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

        // field, then comparison/colon operator, then the rest.
        private val TOKEN_REGEX = Regex("^([A-Za-z]+)(>=|<=|!=|>|<|=|:)(.*)$")

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
                    val m = TOKEN_REGEX.find(t)
                    val field = m?.let { FIELDS[it.groupValues[1].lowercase()] }
                    val term = if (m != null && field != null) {
                        buildFieldTerm(field, m.groupValues[2], m.groupValues[3])
                    } else {
                        // Bare token (or unknown field like "http://x") → substring on content.
                        substringTerm({ it.content }, t)
                    }
                    terms.add(if (negate) { log -> !term(log) } else term)
                }
            } catch (e: Exception) {
                // Bad regex etc. — fall back to treating the whole input as a content substring.
                return LogQuery(text, e.message ?: "invalid query", listOf(substringTerm({ it.content }, text)))
            }
            return LogQuery(text, null, terms)
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
            // /regex/
            if (rawValue.length >= 2 && rawValue.first() == '/' && rawValue.last() == '/') {
                val regex = Regex(rawValue.substring(1, rawValue.length - 1), RegexOption.IGNORE_CASE)
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

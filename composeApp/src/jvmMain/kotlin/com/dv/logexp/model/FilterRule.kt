package com.dv.logexp.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class FilterMode { SHOW, HIDE, HIGHLIGHT, SHOW_AND_HIGHLIGHT }

@Serializable
data class FieldPattern(
    val pattern: String = "",
    val isRegex: Boolean = false,
    val caseSensitive: Boolean = false,
)

@Serializable
data class FilterRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New filter",
    val enabled: Boolean = true,
    val mode: FilterMode = FilterMode.SHOW,
    val highlightColor: Long = 0x33FFEB3BL,
    val message: FieldPattern = FieldPattern(),
    val tag: FieldPattern = FieldPattern(),
    val app: FieldPattern = FieldPattern(),
    val pid: FieldPattern = FieldPattern(),
    val levels: Set<String> = emptySet(),
) {
    fun matches(log: LogEntry): Boolean {
        if (!message.matches(log.message)) return false
        if (!tag.matches(log.tag)) return false
        if (!app.matches(log.appName)) return false
        if (!pid.matches(log.pid)) return false
        if (levels.isNotEmpty() && log.level !in levels) return false
        return true
    }
}

private fun FieldPattern.matches(value: String): Boolean {
    if (pattern.isBlank()) return true
    return if (isRegex) {
        runCatching {
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, opts).containsMatchIn(value)
        }.getOrElse { false }
    } else {
        value.contains(pattern, ignoreCase = !caseSensitive)
    }
}

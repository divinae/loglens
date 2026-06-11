package com.dv.loglens.viewmodel

import com.dv.loglens.model.FieldPattern
import com.dv.loglens.model.FilterMode
import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.LogEntry
import com.dv.loglens.model.LogQuery
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for the query + SHOW/HIDE matching rules now isolated in [LogFilter]. */
class LogFilterTest {

    private fun entry(tag: String = "Tag", level: String = "I", message: String = "msg") =
        LogEntry(id = 0, content = message, level = level, tag = tag, message = message)

    private fun showRule(tag: String) =
        FilterRule(mode = FilterMode.SHOW, tag = FieldPattern(pattern = tag))

    private fun hideRule(tag: String) =
        FilterRule(mode = FilterMode.HIDE, tag = FieldPattern(pattern = tag))

    @Test
    fun emptyCriteriaMatchEverything() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, emptyList())
        assertTrue(f.matches(entry()))
    }

    @Test
    fun hideRuleExcludesMatchingRows() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, listOf(hideRule("Spam")))
        assertFalse(f.matches(entry(tag = "Spam")))
        assertTrue(f.matches(entry(tag = "Keep")))
    }

    @Test
    fun aShowRulePresentMeansOnlyMatchingRowsPass() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, listOf(showRule("Wanted")))
        assertTrue(f.matches(entry(tag = "Wanted")))
        // Once any SHOW rule exists, non-matching rows are hidden.
        assertFalse(f.matches(entry(tag = "Other")))
    }

    @Test
    fun hideWinsOverShow() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, listOf(showRule("App"), hideRule("App")))
        assertFalse(f.matches(entry(tag = "App")))
    }

    @Test
    fun disabledRulesAreIgnored() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, listOf(hideRule("Spam").copy(enabled = false)))
        assertTrue(f.matches(entry(tag = "Spam")))
    }

    @Test
    fun showAndHighlightCountsAsAShowRule() {
        val f = LogFilter()
        val rule = FilterRule(mode = FilterMode.SHOW_AND_HIGHLIGHT, tag = FieldPattern(pattern = "Hi"))
        f.update(LogQuery.EMPTY, listOf(rule))
        assertTrue(f.matches(entry(tag = "Hi")))
        assertFalse(f.matches(entry(tag = "Lo")))
    }

    @Test
    fun pureHighlightRuleDoesNotFilterRowsOut() {
        val f = LogFilter()
        val rule = FilterRule(mode = FilterMode.HIGHLIGHT, tag = FieldPattern(pattern = "Hi"))
        f.update(LogQuery.EMPTY, listOf(rule))
        // HIGHLIGHT is neither SHOW nor HIDE, so every row still passes.
        assertTrue(f.matches(entry(tag = "Hi")))
        assertTrue(f.matches(entry(tag = "Lo")))
    }

    @Test
    fun quickQueryIsAndedWithTheRules() {
        val f = LogFilter()
        f.update(LogQuery.parse("level:E"), listOf(showRule("Net")))
        assertTrue(f.matches(entry(tag = "Net", level = "E")))
        // Passes the SHOW rule but fails the query.
        assertFalse(f.matches(entry(tag = "Net", level = "I")))
        // Passes the query but fails the SHOW rule.
        assertFalse(f.matches(entry(tag = "Other", level = "E")))
    }

    @Test
    fun updateReplacesPreviousCriteria() {
        val f = LogFilter()
        f.update(LogQuery.EMPTY, listOf(hideRule("Spam")))
        assertFalse(f.matches(entry(tag = "Spam")))
        // Clearing the filters must drop the old partition, not keep hiding.
        f.update(LogQuery.EMPTY, emptyList())
        assertTrue(f.matches(entry(tag = "Spam")))
    }
}

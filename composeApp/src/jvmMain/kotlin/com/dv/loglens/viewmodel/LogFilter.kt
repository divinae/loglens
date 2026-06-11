package com.dv.loglens.viewmodel

import com.dv.loglens.model.FilterMode
import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.LogEntry
import com.dv.loglens.model.LogQuery

/**
 * The quick-filter query plus the SHOW/HIDE rule predicate, split out of [MainViewModel] so the
 * matching rules are unit-testable without Compose. Holds the parsed query and the active
 * SHOW/HIDE partition; [update] refreshes both together whenever the criteria change so per-log
 * matching doesn't re-scan/re-categorise the rule list.
 *
 * Fields are `@Volatile` because the capture producer calls [matches] off the main thread
 * (Dispatchers.Default) while the UI thread calls [update]; a stale read is harmless (a criteria
 * change triggers a full rebuild anyway) but the publish must be visible across threads.
 *
 * The "always show bookmarks" override stays in the view model — it reads Compose bookmark state.
 */
class LogFilter {
    @Volatile
    private var query: LogQuery = LogQuery.EMPTY
    @Volatile
    private var show: List<FilterRule> = emptyList()
    @Volatile
    private var hide: List<FilterRule> = emptyList()

    fun update(query: LogQuery, filters: List<FilterRule>) {
        this.query = query
        val active = filters.filter { it.enabled }
        show = active.filter { it.mode == FilterMode.SHOW || it.mode == FilterMode.SHOW_AND_HIGHLIGHT }
        hide = active.filter { it.mode == FilterMode.HIDE }
    }

    /** True if [log] passes the quick-filter query and the SHOW/HIDE rules (bookmarks aside). */
    fun matches(log: LogEntry): Boolean {
        if (!query.matches(log)) return false
        if (hide.any { it.matches(log) }) return false
        if (show.isNotEmpty() && show.none { it.matches(log) }) return false
        return true
    }
}

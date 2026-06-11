package com.dv.loglens

import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.ThemeMode
import java.io.File

/**
 * Every action [AppContent] can trigger, hoisted out of the view so it never touches the view model
 * directly. The wrapping [App] binds these to [com.dv.loglens.viewmodel.MainViewModel]; tests and
 * previews can pass fakes.
 */
class AppCallbacks(
    val setAutoFollow: (Boolean) -> Unit,
    val toggleSelection: (Int, Boolean) -> Unit,
    val selectRange: (Collection<Int>) -> Unit,
    val ensureExpanded: (Int) -> Unit,
    val toggleExpand: (Int) -> Unit,
    val toggleBookmark: (Int) -> Unit,
    val showToast: (String) -> Unit,
    val setQuickFilterVisible: (Boolean) -> Unit,
    val clearSelection: () -> Unit,
    val addFilter: (FilterRule) -> Unit,
    val removeFilter: (String) -> Unit,
    val replaceFilters: (List<FilterRule>) -> Unit,
    val reorderFilters: (Int, Int) -> Unit,
    val setFilterEnabled: (String, Boolean) -> Unit,
    val toggleRecording: () -> Unit,
    val launchScrcpy: () -> Unit,
    val togglePause: () -> Unit,
    val toggleLogBuffer: (LogBuffer) -> Unit,
    val toggleColumn: (LogColumn) -> Unit,
    val toggleCapture: () -> Unit,
    val toggleAutoFollow: () -> Unit,
    val setTheme: (ThemeMode) -> Unit,
    val setSoftWrap: (Boolean) -> Unit,
    val setScreenshotDir: (String) -> Unit,
    val setRecordingSize: (String) -> Unit,
    val setRecordingShowTaps: (Boolean) -> Unit,
    val setRecordingDir: (String) -> Unit,
    val setRecordingBitrateMbps: (Int) -> Unit,
    val setOpenScreenshotAfterCapture: (Boolean) -> Unit,
    val setFontWeight: (String) -> Unit,
    val setFontSize: (Float) -> Unit,
    val setFontFamily: (String) -> Unit,
    val setFiltersListWidth: (Float) -> Unit,
    val setExpandMultilineByDefault: (Boolean) -> Unit,
    val setAlwaysShowBookmarks: (Boolean) -> Unit,
    val setAdbPath: (String) -> Unit,
    val setScrcpyPath: (String) -> Unit,
    val selectDevice: (String?) -> Unit,
    val refreshDevices: () -> Unit,
    val openRecentFile: (String) -> Unit,
    val loadLogsFromFile: (File) -> Unit,
    val commitSearchQuery: () -> Unit,
    val clearSearchHistory: () -> Unit,
    val clearRecentFiles: () -> Unit,
    val clearLogs: () -> Unit,
    val updateSearchQuery: (String) -> Unit,
    val updateColumnWidth: (LogColumn, Float) -> Unit,
    val crashOrdinal: (Int) -> Int,
    val bookmarkOrdinal: (Int) -> Int,
    val findNextBookmark: (Int) -> Int?,
    val findPreviousBookmark: (Int) -> Int?,
    val findNextCrash: (Int) -> Int?,
    val findPreviousCrash: (Int) -> Int?,
    val getSelectionAnchor: () -> Int?,
)

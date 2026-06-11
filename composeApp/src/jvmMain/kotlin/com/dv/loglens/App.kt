package com.dv.loglens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dv.loglens.model.FilterMode
import com.dv.loglens.model.FontWeightPreset
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.model.cell
import com.dv.loglens.model.logDelta
import com.dv.loglens.service.SystemFonts
import com.dv.loglens.parser.LogcatExporter
import com.dv.loglens.service.ScreenshotService
import com.dv.loglens.util.FilePicker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO
import com.dv.loglens.util.copyToClipboard
import com.dv.loglens.ui.QuickFilterBar
import com.dv.loglens.ui.FilterBar
import com.dv.loglens.ui.FiltersDialog
import com.dv.loglens.ui.BookmarkNavigator
import com.dv.loglens.ui.CrashNavigator
import com.dv.loglens.ui.FindBar
import com.dv.loglens.ui.LogHeaders
import com.dv.loglens.ui.ShortcutsDialog
import com.dv.loglens.ui.IntelliJVerticalScrollbar
import com.dv.loglens.ui.Toast
import com.dv.loglens.ui.LogRow
import com.dv.loglens.ui.TopBar
import com.dv.loglens.ui.logKeyboardShortcuts
import com.dv.loglens.ui.theme.islandsComponentStyling
import com.dv.loglens.ui.theme.islandsEditor
import com.dv.loglens.ui.theme.islandsPanel
import com.dv.loglens.ui.theme.islandsThemeDefinition
import com.dv.loglens.model.LogEntry
import com.dv.loglens.viewmodel.MainViewModel
import com.dv.loglens.viewmodel.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

/** A [BringIntoViewSpec] that never scrolls — used so focusing a cell's copy field doesn't jump the list. */
@OptIn(ExperimentalFoundationApi::class)
private val NoScrollBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppContent(
    state: ScreenState,
    logs: List<LogEntry>,
    filteredLogs: List<LogEntry>,
    selectedIds: Set<Int>,
    bookmarkedIds: Set<Int>,
    crashIds: Set<Int>,
    expandedIds: Set<Int>,
    pidToPackage: Map<String, String>,
    visibleCrashes: List<Int>,
    recomputeTick: () -> Int,
    recordingSaved: Flow<File>,
    toast: Flow<String>,
    callbacks: AppCallbacks,
    // Wraps the top bar; on macOS main() passes a WindowDraggableArea (plus a traffic-lights
    // inset) so the bar doubles as the title bar when the native one is hidden.
    topBarDecoration: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {

    // Open a finished recording in the default player, reusing the screenshot "open after capture" toggle.
    LaunchedEffect(Unit) {
        recordingSaved.collect { file ->
            if (state.openScreenshotAfterCapture) {
                runCatching { ProcessBuilder("xdg-open", file.absolutePath).start() }
            }
        }
    }
    // Transient toast over the log list; each new message restarts the auto-dismiss timer.
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastSeq by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        toast.collect { msg ->
            toastText = msg
            toastSeq++
        }
    }
    LaunchedEffect(toastSeq) {
        if (toastSeq > 0) {
            delay(2500)
            toastText = null
        }
    }
    val resolvedFontFamily = remember(state.fontFamily, state.fontWeight) {
        SystemFonts.resolve(state.fontFamily, FontWeightPreset.fromName(state.fontWeight).weight)
    }
    // Distinct running process/app names, offered as pick-list options in the filter dialog.
    val appOptions = remember(pidToPackage.size) { pidToPackage.values.distinct().sorted() }

    var showFiltersDialog by remember { mutableStateOf(false) }
    var filtersDialogInitialId by remember { mutableStateOf<String?>(null) }
    var filtersDialogAddOnOpen by remember { mutableStateOf(false) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var currentMatchIdx by remember { mutableStateOf(0) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    // The single cell currently in substring-selection mode (rendered as a read-only text field), or null.
    var activeCell by remember { mutableStateOf<Pair<Int, LogColumn>?>(null) }

    val copySelected: () -> Unit = remember {
        {
            val text = logs.filter { it.id in selectedIds }
                .joinToString("\n") { it.content }
            copyToClipboard(text)
        }
    }
    val copyColumnSelected: (LogColumn) -> Unit = remember {
        { column ->
            val text = logs.filter { it.id in selectedIds }
                .joinToString("\n") { it.cell(column) }
            copyToClipboard(text)
        }
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val findFocusRequester = remember { FocusRequester() }
    // True while the filter/find text field has focus; plain-key shortcuts (B, arrows) must not
    // fire then — the field consumes the typed character but the key event still bubbles up.
    var textInputFocused by remember { mutableStateOf(false) }

    val isDark = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val islandsTheme = remember(isDark) { islandsThemeDefinition(isDark) }
    IntUiTheme(theme = islandsTheme, styling = islandsComponentStyling(isDark)) {
        val listState = rememberLazyListState()

        // Maintained incrementally in the ViewModel (filtered per-batch during capture) instead of
        // re-scanning the whole log list on every recomposition.

        val highlightFilters = remember(state.filters) {
            state.filters.filter {
                it.enabled && (it.mode == FilterMode.HIGHLIGHT || it.mode == FilterMode.SHOW_AND_HIGHLIGHT)
            }
        }
        // Resolved highlight colour per log id. Lives outside the LazyColumn items (which recompose
        // from scratch on every scroll-in) so scrolling back over rows already seen doesn't re-run
        // each highlight filter's matches() — that message scan per visible row is what makes scroll
        // drag once highlight state.filters are on. Color.Unspecified is the "no highlight" sentinel so the
        // common no-match rows are cached too (HashMap.getOrPut treats a null value as absent). Keyed
        // on the filter set, so it's rebuilt whenever the highlight state.filters change.
        val highlightColors = remember(highlightFilters) { HashMap<Int, Color>() }

        val findMatchIndices = remember(filteredLogs.size, findQuery) {
            if (findQuery.isBlank()) emptyList<Int>()
            else filteredLogs.mapIndexedNotNull { idx, log ->
                if (log.content.contains(findQuery, ignoreCase = true)) idx else null
            }
        }
        LaunchedEffect(findMatchIndices) {
            if (currentMatchIdx >= findMatchIndices.size) currentMatchIdx = 0
        }

        fun jumpToMatch(target: Int) {
            if (findMatchIndices.isEmpty()) return
            val safe = ((target % findMatchIndices.size) + findMatchIndices.size) % findMatchIndices.size
            currentMatchIdx = safe
            val rowIdx = findMatchIndices[safe]
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val viewportStart = info.viewportStartOffset
            val viewportEnd = info.viewportEndOffset
            val fullyVisible = visible.any { v ->
                v.index == rowIdx && v.offset >= viewportStart && v.offset + v.size <= viewportEnd
            }
            if (!fullyVisible) {
                val rowSize = visible.firstOrNull()?.size ?: 20
                val centerOffset = -((viewportEnd - viewportStart - rowSize) / 2)
                scope.launch { listState.animateScrollToItem(rowIdx, centerOffset) }
            }
            val log = filteredLogs[rowIdx]
            callbacks.toggleSelection(log.id, false)
            callbacks.setAutoFollow(false)
            // Expand a folded match so the matching line isn't hidden behind the collapsed first line.
            if ('\n' in log.message) callbacks.ensureExpanded(log.id)
        }

        // Jump to a log by id (used by crash navigation): align its first line to the top of the
        // viewport so a long folded trace reads from its start, then select and expand it.
        fun jumpToLogId(id: Int) {
            val rowIdx = filteredLogs.indexOfFirst { it.id == id }
            if (rowIdx < 0) return
            scope.launch { listState.animateScrollToItem(rowIdx) }
            callbacks.toggleSelection(id, false)
            callbacks.setAutoFollow(false)
            if ('\n' in filteredLogs[rowIdx].message) callbacks.ensureExpanded(id)
        }

        fun jumpToCrash(forward: Boolean) {
            if (visibleCrashes.isEmpty()) return
            val currentId = selectedIds.lastOrNull()
                ?: filteredLogs.getOrNull(listState.firstVisibleItemIndex)?.id
                ?: -1
            val target = if (forward) callbacks.findNextCrash(currentId) else callbacks.findPreviousCrash(currentId)
            target?.let { jumpToLogId(it) }
        }

        fun jumpToBookmark(forward: Boolean) {
            if (bookmarkedIds.isEmpty()) return
            val currentId = selectedIds.lastOrNull()
                ?: filteredLogs.getOrNull(listState.firstVisibleItemIndex)?.id
                ?: -1
            val target =
                if (forward) callbacks.findNextBookmark(currentId) else callbacks.findPreviousBookmark(currentId)
            target?.let { jumpToLogId(it) }
        }

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }

        LaunchedEffect(findVisible) {
            if (findVisible) {
                findFocusRequester.requestFocus()
            } else {
                // The disposed find field may not emit a focus-lost event — clear the flag so
                // plain-key shortcuts come back.
                textInputFocused = false
                runCatching { focusRequester.requestFocus() }
            }
        }

        LaunchedEffect(state.quickFilterVisible) {
            // Same as above: hiding the quick-filter bar disposes its field without a focus event.
            if (!state.quickFilterVisible && !findVisible) textInputFocused = false
        }

        var isAutoScrolling by remember { mutableStateOf(false) }

        LaunchedEffect(filteredLogs.size, state.autoFollow) {
            if (state.autoFollow && filteredLogs.isNotEmpty() && !listState.isScrollInProgress) {
                isAutoScrolling = true
                listState.scrollToItem(filteredLogs.size - 1)
                isAutoScrolling = false
            }
        }

        LaunchedEffect(listState) {
            var prevIndex = listState.firstVisibleItemIndex
            var prevOffset = listState.firstVisibleItemScrollOffset
            var prevTick = recomputeTick()
            snapshotFlow {
                Triple(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    recomputeTick(),
                )
            }
                .collect { (idx, off, tick) ->
                    val recomputed = tick != prevTick
                    val scrolledUp = idx < prevIndex || (idx == prevIndex && off < prevOffset)
                    // Any scroll-up cancels auto-follow. We don't gate on isScrollInProgress —
                    // it's false between mouse-wheel ticks during capture, so a wheel scroll-up was
                    // often missed and the next captured line yanked the view back to the tail. A
                    // filter recompute rebuilds the list and resets the scroll position; that index
                    // drop (flagged by recomputeTick) is not a user scroll, so skip it.
                    if (scrolledUp && !recomputed && state.autoFollow && !isAutoScrolling) {
                        callbacks.setAutoFollow(false)
                    }
                    prevIndex = idx
                    prevOffset = off
                    prevTick = tick
                }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(islandsPanel(isDark))
                .focusRequester(focusRequester)
                .focusable()
                .logKeyboardShortcuts(
                    filteredLogs = filteredLogs,
                    selectedIds = selectedIds,
                    listState = listState,
                    scope = scope,
                    onToggleBookmark = callbacks.toggleBookmark,
                    findNextBookmark = callbacks.findNextBookmark,
                    findPreviousBookmark = callbacks.findPreviousBookmark,
                    onSelect = { id -> callbacks.toggleSelection(id, false) },
                    onDisableAutoFollow = { callbacks.setAutoFollow(false) },
                    onFocusSearch = {
                        findVisible = true
                        runCatching { findFocusRequester.requestFocus() }
                    },
                    findActive = findVisible,
                    onFindNext = { jumpToMatch(currentMatchIdx + 1) },
                    onFindPrev = { jumpToMatch(currentMatchIdx - 1) },
                    onCloseFind = { findVisible = false; findQuery = "" },
                    onCopySelected = copySelected,
                    onClearLogs = callbacks.clearLogs,
                    textInputFocused = textInputFocused,
                )
        ) {
            // Wrapped so main() can turn the bar into the window-drag handle on macOS, where the
            // native title bar is hidden.
            topBarDecoration {
                TopBar(
                    autoFollow = state.autoFollow,
                    themeMode = state.themeMode,
                    devices = state.devices,
                    selectedDevice = state.selectedDevice,
                    onSelectDevice = callbacks.selectDevice,
                    onRefreshDevices = callbacks.refreshDevices,
                    onClear = callbacks.clearLogs,
                    isCapturing = state.isCapturing,
                    onToggleCapture = callbacks.toggleCapture,
                    isPaused = state.isPaused,
                    onTogglePause = callbacks.togglePause,
                    isRecording = state.isRecording,
                    onToggleRecording = callbacks.toggleRecording,
                    onMirrorDevice = callbacks.launchScrcpy,
                    onTakeScreenshot = {
                        val serial = state.selectedDevice
                        val dir = File(state.screenshotDir.ifBlank { "${System.getProperty("user.home")}/Pictures/android" })
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Date())
                        val target = File(dir, "scr_$stamp.png")
                        scope.launch(Dispatchers.IO) {
                            if (ScreenshotService.capture(serial, target)) {
                                runCatching { ImageIO.read(target)?.let(::copyToClipboard) }
                                callbacks.showToast("Screenshot saved → ${target.name}")
                                if (state.openScreenshotAfterCapture) {
                                    runCatching { ProcessBuilder("xdg-open", target.absolutePath).start() }
                                }
                            }
                        }
                    },
                    onSaveLogs = { filtered, asLogcat ->
                        // Snapshot now: the source list keeps growing during capture while IO runs.
                        val snapshot = (if (filtered) filteredLogs else logs).toList()
                        val ext = if (asLogcat) "logcat" else "txt"
                        val base = if (filtered) "logs-filtered" else "logs"
                        // No ':' — illegal in filenames on some filesystems.
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())
                        scope.launch(Dispatchers.IO) {
                            val target = FilePicker.saveFile(
                                if (filtered) "Save filtered logs" else "Save all logs",
                                "$base-$stamp.$ext",
                            )
                            if (target != null) {
                                val payload = if (asLogcat) {
                                    LogcatExporter.toJson(snapshot)
                                } else {
                                    snapshot.joinToString("\n") { it.content }
                                }
                                target.writeText(payload)
                            }
                        }
                    },
                    onOpenLogs = {
                        scope.launch(Dispatchers.IO) {
                            FilePicker.openFile("Open logs")?.let(callbacks.loadLogsFromFile)
                        }
                    },
                    recentFiles = state.recentFiles,
                    onOpenRecent = callbacks.openRecentFile,
                    onClearRecent = callbacks.clearRecentFiles,
                    loadedFile = state.loadedFile,
                    onToggleAutoFollow = callbacks.toggleAutoFollow,
                    onSetTheme = callbacks.setTheme,
                    fontSize = state.fontSize,
                    onSetFontSize = callbacks.setFontSize,
                    fontFamily = state.fontFamily,
                    onSetFontFamily = callbacks.setFontFamily,
                    fontWeight = state.fontWeight,
                    onSetFontWeight = callbacks.setFontWeight,
                    softWrap = state.softWrap,
                    onSetSoftWrap = callbacks.setSoftWrap,
                    expandMultilineByDefault = state.expandMultilineByDefault,
                    onSetExpandMultilineByDefault = callbacks.setExpandMultilineByDefault,
                    alwaysShowBookmarks = state.alwaysShowBookmarks,
                    onSetAlwaysShowBookmarks = callbacks.setAlwaysShowBookmarks,
                    onOpenFind = {
                        if (findVisible) {
                            findVisible = false
                            findQuery = ""
                        } else {
                            findVisible = true
                            runCatching { findFocusRequester.requestFocus() }
                        }
                    },
                    findActive = findVisible,
                    logCount = filteredLogs.size,
                    totalLogCount = logs.size,
                    logBuffers = state.logBuffers,
                    onToggleLogBuffer = callbacks.toggleLogBuffer,
                    screenshotDir = state.screenshotDir,
                    onSetScreenshotDir = callbacks.setScreenshotDir,
                    openScreenshotAfterCapture = state.openScreenshotAfterCapture,
                    onSetOpenScreenshotAfterCapture = callbacks.setOpenScreenshotAfterCapture,
                    adbPath = state.adbPath,
                    onSetAdbPath = callbacks.setAdbPath,
                    recordingDir = state.recordingDir,
                    onSetRecordingDir = callbacks.setRecordingDir,
                    recordingShowTaps = state.recordingShowTaps,
                    onSetRecordingShowTaps = callbacks.setRecordingShowTaps,
                    recordingBitrateMbps = state.recordingBitrateMbps,
                    onSetRecordingBitrateMbps = callbacks.setRecordingBitrateMbps,
                    recordingSize = state.recordingSize,
                    onSetRecordingSize = callbacks.setRecordingSize,
                    onShowShortcuts = { showShortcutsDialog = true },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                var dragAnchorIdx by remember { mutableStateOf<Int?>(null) }
                val filteredLogsRef = rememberUpdatedState(filteredLogs)
                // Shared with the scrollbar: while its thumb is dragged the rows render in a cheap
                // "scrub" mode so each jump's viewport composes fast (see LogRow).
                val scrollbarInteraction = remember { MutableInteractionSource() }
                val isScrubbing by scrollbarInteraction.collectIsDraggedAsState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(islandsEditor(isDark)),
                ) {
                    Column(Modifier.fillMaxSize()) {
                    LogHeaders(
                        visibleColumns = state.visibleColumns,
                        columnWidths = state.columnWidths,
                        isDarkMode = isDark,
                        onResize = callbacks.updateColumnWidth,
                        onToggleColumn = callbacks.toggleColumn,
                        currentWidthOf = { col -> state.columnWidths[col] ?: 100f },
                    )
                    Divider(
                        orientation = Orientation.Horizontal,
                        color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.4f),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        activeCell = null
                                        runCatching { focusRequester.requestFocus() }
                                        dragAnchorIdx = indexAtY(listState, pos.y)
                                    },
                                    onDragEnd = { dragAnchorIdx = null },
                                    onDragCancel = { dragAnchorIdx = null },
                                    onDrag = { change, _ ->
                                        val anchor = dragAnchorIdx ?: return@detectDragGestures
                                        val idx = indexAtY(listState, change.position.y) ?: return@detectDragGestures
                                        val logsNow = filteredLogsRef.value
                                        val (lo, hi) = if (anchor <= idx) anchor to idx else idx to anchor
                                        val hiSafe = (hi + 1).coerceAtMost(logsNow.size)
                                        if (lo >= logsNow.size) return@detectDragGestures
                                        callbacks.selectRange(logsNow.subList(lo, hiSafe).map { it.id })
                                        callbacks.setAutoFollow(false)
                                    },
                                )
                            },
                    ) {
                    // Focusing the substring-copy text field would otherwise bring-into-view and jump
                    // the list; suppress that auto-scroll so double-click stays put. Explicit jumps
                    // (find / crash nav) use animateScrollToItem and are unaffected.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollBringIntoViewSpec) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredLogs, key = { _, log -> log.id }) { index, log ->
                            val highlightColor = if (highlightFilters.isEmpty()) {
                                null
                            } else {
                                highlightColors.getOrPut(log.id) {
                                    highlightFilters.firstOrNull { it.matches(log) }
                                        ?.let { Color(it.highlightColor.toInt()) } ?: Color.Unspecified
                                }.takeIf { it != Color.Unspecified }
                            }
                            // Δt vs the previous visible row; only computed when the column is shown.
                            val deltaText = if (LogColumn.DELTA in state.visibleColumns) {
                                logDelta(filteredLogs.getOrNull(index - 1), log)
                            } else {
                                ""
                            }
                            LogRow(
                                log = log,
                                deltaText = deltaText,
                                visibleColumns = state.visibleColumns,
                                columnWidths = state.columnWidths,
                                isDarkMode = isDark,
                                isSelected = log.id in selectedIds,
                                isBookmarked = log.id in bookmarkedIds,
                                findQuery = findQuery,
                                highlightColor = highlightColor,
                                fontSize = state.fontSize.sp,
                                fontFamily = resolvedFontFamily,
                                fontWeight = state.fontWeight,
                                softWrap = state.softWrap,
                                onSelect = { multi, range ->
                                    activeCell = null
                                    runCatching { focusRequester.requestFocus() }
                                    if (range) {
                                        val anchor = callbacks.getSelectionAnchor()
                                        val ids = filteredLogs.map { it.id }
                                        val a = anchor?.let { ids.indexOf(it) } ?: -1
                                        val b = ids.indexOf(log.id)
                                        if (a < 0 || b < 0) {
                                            callbacks.toggleSelection(log.id, multi)
                                        } else {
                                            val (lo, hi) = if (a <= b) a to b else b to a
                                            callbacks.selectRange(ids.subList(lo, hi + 1))
                                        }
                                    } else {
                                        callbacks.toggleSelection(log.id, multi)
                                    }
                                },
                                onAddFilter = callbacks.addFilter,
                                onToggleBookmark = { callbacks.toggleBookmark(log.id) },
                                selectedCount = selectedIds.size,
                                onCopySelected = copySelected,
                                onCopyColumnSelected = copyColumnSelected,
                                selectedMessages = {
                                    logs.filter { it.id in selectedIds }
                                        .joinToString("") { it.message }
                                },
                                activeCell = activeCell,
                                onActivateCell = { column ->
                                    activeCell = log.id to column
                                    // Entering substring-copy mode: drop the row selection so the
                                    // highlighted text field isn't fighting the selected-row background.
                                    callbacks.clearSelection()
                                    callbacks.setAutoFollow(false)
                                },
                                onExitSelection = {
                                    activeCell = null
                                    runCatching { focusRequester.requestFocus() }
                                },
                                isExpanded = (log.id in expandedIds) != state.expandMultilineByDefault,
                                onToggleExpand = { callbacks.toggleExpand(log.id) },
                                isCrash = log.id in crashIds,
                                scrubbing = isScrubbing,
                            )
                        }
                    }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.03f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    if (!state.autoFollow) {
                        IntelliJVerticalScrollbar(
                            state = listState,
                            isDark = isDark,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp),
                            interactionSource = scrollbarInteraction,
                        )
                    }
                    }
                    }
                }

                if (findVisible) {
                    FindBar(
                        query = findQuery,
                        onQueryChange = { findQuery = it; currentMatchIdx = 0 },
                        matchCount = findMatchIndices.size,
                        currentIdx = currentMatchIdx,
                        onNext = { jumpToMatch(currentMatchIdx + 1) },
                        onPrev = { jumpToMatch(currentMatchIdx - 1) },
                        onClose = { findVisible = false; findQuery = "" },
                        onCopySelected = copySelected,
                        focusRequester = findFocusRequester,
                        onFocusChanged = { textInputFocused = it },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp)
                            .widthIn(min = 360.dp),
                    )
                }

                // Floating navigators, top-right over the logs, stacked; they drop below the find
                // bar when that's open so nothing overlaps. The crash pill counts and walks only
                // crashes that survive the current state.filters; the bookmark pill walks all bookmarks
                // (with "Always show bookmarks" on they are visible regardless of state.filters).
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 12.dp)
                        .padding(top = if (findVisible) 48.dp else 4.dp),
                ) {
                    if (visibleCrashes.isNotEmpty()) {
                        CrashNavigator(
                            count = visibleCrashes.size,
                            current = callbacks.crashOrdinal(selectedIds.lastOrNull() ?: -1),
                            onPrev = { jumpToCrash(forward = false) },
                            onNext = { jumpToCrash(forward = true) },
                        )
                    }
                    if (bookmarkedIds.isNotEmpty()) {
                        BookmarkNavigator(
                            count = bookmarkedIds.size,
                            current = callbacks.bookmarkOrdinal(selectedIds.lastOrNull() ?: -1),
                            onPrev = { jumpToBookmark(forward = false) },
                            onNext = { jumpToBookmark(forward = true) },
                        )
                    }
                }

                Toast(
                    text = toastText,
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp),
                )
            }

            Divider(Orientation.Horizontal)

            if (state.quickFilterVisible) {
                QuickFilterBar(
                    initialQuery = state.searchQuery,
                    error = state.searchQueryError,
                    history = state.searchHistory,
                    onSearchChange = callbacks.updateSearchQuery,
                    onCommit = callbacks.commitSearchQuery,
                    onClearHistory = callbacks.clearSearchHistory,
                    onFocusChanged = { textInputFocused = it },
                )
                Divider(Orientation.Horizontal)
            }

            FilterBar(
                filters = state.filters,
                quickFilterVisible = state.quickFilterVisible,
                onToggleQuickFilter = {
                    // Hiding the bar keeps the active filter; the bar reseeds from it when shown again.
                    callbacks.setQuickFilterVisible(!state.quickFilterVisible)
                },
                onOpenFilters = {
                    filtersDialogInitialId = null
                    filtersDialogAddOnOpen = false
                    showFiltersDialog = true
                },
                onToggleEnabled = callbacks.setFilterEnabled,
                onRemove = callbacks.removeFilter,
                onEdit = { id ->
                    filtersDialogInitialId = id
                    filtersDialogAddOnOpen = false
                    showFiltersDialog = true
                },
                onAddNew = {
                    filtersDialogInitialId = null
                    filtersDialogAddOnOpen = true
                    showFiltersDialog = true
                },
                onMove = callbacks.reorderFilters,
            )
        }

        if (showFiltersDialog) {
            FiltersDialog(
                filters = state.filters,
                onDismiss = { showFiltersDialog = false },
                onSave = callbacks.replaceFilters,
                initialSelectedId = filtersDialogInitialId,
                addOnOpen = filtersDialogAddOnOpen,
                listWidth = state.filtersListWidth,
                onListWidthChange = callbacks.setFiltersListWidth,
                appOptions = appOptions,
            )
        }

        if (showShortcutsDialog) {
            ShortcutsDialog(onDismiss = { showShortcutsDialog = false })
        }
    }
}

private fun indexAtY(state: LazyListState, y: Float): Int? {
    val visible = state.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    if (y < visible.first().offset) return visible.first().index
    if (y >= visible.last().offset + visible.last().size) return visible.last().index
    return visible.firstOrNull { y >= it.offset && y < it.offset + it.size }?.index
}

/**
 * Stateful entry point: owns the [MainViewModel], collects its [ScreenState] and exposes its data and
 * actions to the stateless [AppContent].
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun App(
    viewModel: MainViewModel = viewModel { MainViewModel() },
    // Wraps the top bar; on macOS main() passes a WindowDraggableArea (plus a traffic-lights
    // inset) so the bar doubles as the title bar when the native one is hidden.
    topBarDecoration: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    val state by viewModel.state.collectAsState()
    val callbacks = remember(viewModel) {
        AppCallbacks(
            setAutoFollow = viewModel::setAutoFollow,
            toggleSelection = viewModel::toggleSelection,
            selectRange = viewModel::selectRange,
            ensureExpanded = viewModel::ensureExpanded,
            toggleExpand = viewModel::toggleExpand,
            toggleBookmark = viewModel::toggleBookmark,
            showToast = viewModel::showToast,
            setQuickFilterVisible = viewModel::setQuickFilterVisible,
            clearSelection = viewModel::clearSelection,
            addFilter = viewModel::addFilter,
            removeFilter = viewModel::removeFilter,
            replaceFilters = viewModel::replaceFilters,
            reorderFilters = viewModel::reorderFilters,
            setFilterEnabled = viewModel::setFilterEnabled,
            toggleRecording = viewModel::toggleRecording,
            launchScrcpy = viewModel::launchScrcpy,
            togglePause = viewModel::togglePause,
            toggleLogBuffer = viewModel::toggleLogBuffer,
            toggleColumn = viewModel::toggleColumn,
            toggleCapture = viewModel::toggleCapture,
            toggleAutoFollow = viewModel::toggleAutoFollow,
            setTheme = viewModel::setTheme,
            setSoftWrap = viewModel::setSoftWrap,
            setScreenshotDir = viewModel::setScreenshotDir,
            setRecordingSize = viewModel::setRecordingSize,
            setRecordingShowTaps = viewModel::setRecordingShowTaps,
            setRecordingDir = viewModel::setRecordingDir,
            setRecordingBitrateMbps = viewModel::setRecordingBitrateMbps,
            setOpenScreenshotAfterCapture = viewModel::setOpenScreenshotAfterCapture,
            setFontWeight = viewModel::setFontWeight,
            setFontSize = viewModel::setFontSize,
            setFontFamily = viewModel::setFontFamily,
            setFiltersListWidth = viewModel::setFiltersListWidth,
            setExpandMultilineByDefault = viewModel::setExpandMultilineByDefault,
            setAlwaysShowBookmarks = viewModel::setAlwaysShowBookmarks,
            setAdbPath = viewModel::setAdbPath,
            selectDevice = viewModel::selectDevice,
            refreshDevices = viewModel::refreshDevices,
            openRecentFile = viewModel::openRecentFile,
            loadLogsFromFile = viewModel::loadLogsFromFile,
            commitSearchQuery = viewModel::commitSearchQuery,
            clearSearchHistory = viewModel::clearSearchHistory,
            clearRecentFiles = viewModel::clearRecentFiles,
            clearLogs = viewModel::clearLogs,
            updateSearchQuery = viewModel::updateSearchQuery,
            updateColumnWidth = viewModel::updateColumnWidth,
            crashOrdinal = viewModel::crashOrdinal,
            bookmarkOrdinal = viewModel::bookmarkOrdinal,
            findNextBookmark = viewModel::findNextBookmark,
            findPreviousBookmark = viewModel::findPreviousBookmark,
            findNextCrash = viewModel::findNextCrash,
            findPreviousCrash = viewModel::findPreviousCrash,
            getSelectionAnchor = { viewModel.selectionAnchor },
        )
    }
    AppContent(
        state = state,
        logs = viewModel.logs,
        filteredLogs = viewModel.filteredLogs,
        selectedIds = viewModel.selectedIds,
        bookmarkedIds = viewModel.bookmarkedIds,
        crashIds = viewModel.crashIds,
        expandedIds = viewModel.expandedIds,
        pidToPackage = viewModel.pidToPackage,
        visibleCrashes = viewModel.visibleCrashes,
        recomputeTick = { viewModel.recomputeTick },
        recordingSaved = viewModel.recordingSaved,
        toast = viewModel.toast,
        callbacks = callbacks,
        topBarDecoration = topBarDecoration,
    )
}

package com.dv.loglens

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.foundation.rememberScrollbarAdapter
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
import com.dv.loglens.ui.CrashNavigator
import com.dv.loglens.ui.FindBar
import com.dv.loglens.ui.LogHeaders
import com.dv.loglens.ui.ShortcutsDialog
import com.dv.loglens.ui.LogRow
import com.dv.loglens.ui.TopBar
import com.dv.loglens.ui.logKeyboardShortcuts
import com.dv.loglens.ui.theme.islandsComponentStyling
import com.dv.loglens.ui.theme.islandsEditor
import com.dv.loglens.ui.theme.islandsPanel
import com.dv.loglens.ui.theme.islandsThemeDefinition
import com.dv.loglens.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
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
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    val themeMode by viewModel.themeMode.collectAsState()
    val visibleColumns by viewModel.visibleColumns.collectAsState()
    val columnWidths by viewModel.columnWidths.collectAsState()
    val autoFollow by viewModel.autoFollow.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val loadedFile by viewModel.loadedFile.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val fontWeight by viewModel.fontWeight.collectAsState()
    val softWrap by viewModel.softWrap.collectAsState()
    val filtersListWidth by viewModel.filtersListWidth.collectAsState()
    val alwaysShowBookmarks by viewModel.alwaysShowBookmarks.collectAsState()
    val quickFilterVisible by viewModel.quickFilterVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchQueryError by viewModel.searchQueryError.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val screenshotDir by viewModel.screenshotDir.collectAsState()
    val openScreenshotAfterCapture by viewModel.openScreenshotAfterCapture.collectAsState()
    val adbPath by viewModel.adbPath.collectAsState()
    val recordingDir by viewModel.recordingDir.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingShowTaps by viewModel.recordingShowTaps.collectAsState()
    val recordingBitrateMbps by viewModel.recordingBitrateMbps.collectAsState()
    val recordingSize by viewModel.recordingSize.collectAsState()

    // Open a finished recording in the default player, reusing the screenshot "open after capture" toggle.
    LaunchedEffect(Unit) {
        viewModel.recordingSaved.collect { file ->
            if (openScreenshotAfterCapture) {
                runCatching { ProcessBuilder("xdg-open", file.absolutePath).start() }
            }
        }
    }
    val logBuffers by viewModel.logBuffers.collectAsState()
    val resolvedFontFamily = remember(fontFamily, fontWeight) {
        SystemFonts.resolve(fontFamily, FontWeightPreset.fromName(fontWeight).weight)
    }
    val selectedIds = viewModel.selectedIds
    val bookmarkedIds = viewModel.bookmarkedIds
    val logs = viewModel.logs
    val pidToPackage = viewModel.pidToPackage
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
            val text = viewModel.logs.filter { it.id in viewModel.selectedIds }
                .joinToString("\n") { it.content }
            copyToClipboard(text)
        }
    }
    val copyColumnSelected: (LogColumn) -> Unit = remember {
        { column ->
            val text = viewModel.logs.filter { it.id in viewModel.selectedIds }
                .joinToString("\n") { it.cell(column) }
            copyToClipboard(text)
        }
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val findFocusRequester = remember { FocusRequester() }

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val islandsTheme = remember(isDark) { islandsThemeDefinition(isDark) }
    IntUiTheme(theme = islandsTheme, styling = islandsComponentStyling(isDark)) {
        val listState = rememberLazyListState()

        // Maintained incrementally in the ViewModel (filtered per-batch during capture) instead of
        // re-scanning the whole log list on every recomposition.
        val filteredLogs = viewModel.filteredLogs

        val highlightFilters = remember(filters) {
            filters.filter {
                it.enabled && (it.mode == FilterMode.HIGHLIGHT || it.mode == FilterMode.SHOW_AND_HIGHLIGHT)
            }
        }

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
            viewModel.toggleSelection(log.id, multiSelect = false)
            viewModel.setAutoFollow(false)
            // Expand a folded match so the matching line isn't hidden behind the collapsed first line.
            if ('\n' in log.message) viewModel.expandedIds.add(log.id)
        }

        // Jump to a log by id (used by crash navigation): align its first line to the top of the
        // viewport so a long folded trace reads from its start, then select and expand it.
        fun jumpToLogId(id: Int) {
            val rowIdx = filteredLogs.indexOfFirst { it.id == id }
            if (rowIdx < 0) return
            scope.launch { listState.animateScrollToItem(rowIdx) }
            viewModel.toggleSelection(id, multiSelect = false)
            viewModel.setAutoFollow(false)
            if ('\n' in filteredLogs[rowIdx].message) viewModel.expandedIds.add(id)
        }

        fun jumpToCrash(forward: Boolean) {
            if (viewModel.crashIds.isEmpty()) return
            val currentId = viewModel.selectedIds.lastOrNull()
                ?: filteredLogs.getOrNull(listState.firstVisibleItemIndex)?.id
                ?: -1
            val target = if (forward) viewModel.findNextCrash(currentId) else viewModel.findPreviousCrash(currentId)
            target?.let { jumpToLogId(it) }
        }

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }

        LaunchedEffect(findVisible) {
            if (findVisible) findFocusRequester.requestFocus()
            else runCatching { focusRequester.requestFocus() }
        }

        var isAutoScrolling by remember { mutableStateOf(false) }

        LaunchedEffect(filteredLogs.size, autoFollow) {
            if (autoFollow && filteredLogs.isNotEmpty() && !listState.isScrollInProgress) {
                isAutoScrolling = true
                listState.scrollToItem(filteredLogs.size - 1)
                isAutoScrolling = false
            }
        }

        LaunchedEffect(listState) {
            var prevIndex = listState.firstVisibleItemIndex
            var prevOffset = listState.firstVisibleItemScrollOffset
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (idx, off) ->
                    val scrolledUp = idx < prevIndex || (idx == prevIndex && off < prevOffset)
                    // Only a real user gesture (isScrollInProgress) cancels auto-follow. A filter
                    // recompute shrinks the list and drops the index too, but that is not a scroll.
                    if (scrolledUp && viewModel.autoFollow.value && !isAutoScrolling &&
                        listState.isScrollInProgress
                    ) {
                        viewModel.setAutoFollow(false)
                    }
                    prevIndex = idx
                    prevOffset = off
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
                    onToggleBookmark = viewModel::toggleBookmark,
                    findNextBookmark = viewModel::findNextBookmark,
                    findPreviousBookmark = viewModel::findPreviousBookmark,
                    onSelect = { id -> viewModel.toggleSelection(id, multiSelect = false) },
                    onDisableAutoFollow = { viewModel.setAutoFollow(false) },
                    onFocusSearch = {
                        findVisible = true
                        runCatching { findFocusRequester.requestFocus() }
                    },
                    findActive = findVisible,
                    onFindNext = { jumpToMatch(currentMatchIdx + 1) },
                    onFindPrev = { jumpToMatch(currentMatchIdx - 1) },
                    onCloseFind = { findVisible = false; findQuery = "" },
                    onCopySelected = copySelected,
                    onClearLogs = viewModel::clearLogs,
                    onRestartCapture = viewModel::restartCapture,
                )
        ) {
            TopBar(
                autoFollow = autoFollow,
                themeMode = themeMode,
                devices = devices,
                selectedDevice = selectedDevice,
                onSelectDevice = viewModel::selectDevice,
                onRefreshDevices = viewModel::refreshDevices,
                onClear = viewModel::clearLogs,
                isCapturing = isCapturing,
                onToggleCapture = viewModel::toggleCapture,
                isRecording = isRecording,
                onToggleRecording = viewModel::toggleRecording,
                onTakeScreenshot = {
                    val serial = selectedDevice
                    val dir = File(screenshotDir.ifBlank { "${System.getProperty("user.home")}/Pictures/android" })
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Date())
                    val target = File(dir, "scr_$stamp.png")
                    scope.launch(Dispatchers.IO) {
                        if (ScreenshotService.capture(serial, target)) {
                            runCatching { ImageIO.read(target)?.let(::copyToClipboard) }
                            if (openScreenshotAfterCapture) {
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
                        FilePicker.openFile("Open logs")?.let(viewModel::loadLogsFromFile)
                    }
                },
                recentFiles = recentFiles,
                onOpenRecent = viewModel::openRecentFile,
                onClearRecent = viewModel::clearRecentFiles,
                loadedFile = loadedFile,
                onToggleAutoFollow = viewModel::toggleAutoFollow,
                onSetTheme = viewModel::setTheme,
                fontSize = fontSize,
                onSetFontSize = viewModel::setFontSize,
                fontFamily = fontFamily,
                onSetFontFamily = viewModel::setFontFamily,
                fontWeight = fontWeight,
                onSetFontWeight = viewModel::setFontWeight,
                softWrap = softWrap,
                onSetSoftWrap = viewModel::setSoftWrap,
                alwaysShowBookmarks = alwaysShowBookmarks,
                onSetAlwaysShowBookmarks = viewModel::setAlwaysShowBookmarks,
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
                logBuffers = logBuffers,
                onToggleLogBuffer = viewModel::toggleLogBuffer,
                screenshotDir = screenshotDir,
                onSetScreenshotDir = viewModel::setScreenshotDir,
                openScreenshotAfterCapture = openScreenshotAfterCapture,
                onSetOpenScreenshotAfterCapture = viewModel::setOpenScreenshotAfterCapture,
                adbPath = adbPath,
                onSetAdbPath = viewModel::setAdbPath,
                recordingDir = recordingDir,
                onSetRecordingDir = viewModel::setRecordingDir,
                recordingShowTaps = recordingShowTaps,
                onSetRecordingShowTaps = viewModel::setRecordingShowTaps,
                recordingBitrateMbps = recordingBitrateMbps,
                onSetRecordingBitrateMbps = viewModel::setRecordingBitrateMbps,
                recordingSize = recordingSize,
                onSetRecordingSize = viewModel::setRecordingSize,
                onShowShortcuts = { showShortcutsDialog = true },
            )

            Box(modifier = Modifier.weight(1f)) {
                val thumbBase = if (isDark) Color.White else Color.Black
                val scrollbarStyle = ScrollbarStyle(
                    minimalHeight = 24.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(50),
                    hoverDurationMillis = 200,
                    unhoverColor = thumbBase.copy(alpha = 0.20f),
                    hoverColor = thumbBase.copy(alpha = 0.45f),
                )
                var dragAnchorIdx by remember { mutableStateOf<Int?>(null) }
                val filteredLogsRef = rememberUpdatedState(filteredLogs)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(islandsEditor(isDark)),
                ) {
                    Column(Modifier.fillMaxSize()) {
                    LogHeaders(
                        visibleColumns = visibleColumns,
                        columnWidths = columnWidths,
                        isDarkMode = isDark,
                        onResize = viewModel::updateColumnWidth,
                        onToggleColumn = viewModel::toggleColumn,
                        currentWidthOf = { col -> viewModel.columnWidths.value[col] ?: 100f },
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
                                        viewModel.selectRange(logsNow.subList(lo, hiSafe).map { it.id })
                                        viewModel.setAutoFollow(false)
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
                            val hl = highlightFilters.firstOrNull { it.matches(log) }
                            LogRow(
                                log = log,
                                visibleColumns = visibleColumns,
                                columnWidths = columnWidths,
                                isDarkMode = isDark,
                                isSelected = log.id in selectedIds,
                                isBookmarked = log.id in bookmarkedIds,
                                findQuery = findQuery,
                                highlightColor = hl?.let { Color(it.highlightColor.toInt()) },
                                fontSize = fontSize.sp,
                                fontFamily = resolvedFontFamily,
                                fontWeight = fontWeight,
                                softWrap = softWrap,
                                onSelect = { multi, range ->
                                    activeCell = null
                                    runCatching { focusRequester.requestFocus() }
                                    if (range) {
                                        val anchor = viewModel.selectionAnchor
                                        val ids = filteredLogs.map { it.id }
                                        val a = anchor?.let { ids.indexOf(it) } ?: -1
                                        val b = ids.indexOf(log.id)
                                        if (a < 0 || b < 0) {
                                            viewModel.toggleSelection(log.id, multi)
                                        } else {
                                            val (lo, hi) = if (a <= b) a to b else b to a
                                            viewModel.selectRange(ids.subList(lo, hi + 1))
                                        }
                                    } else {
                                        viewModel.toggleSelection(log.id, multi)
                                    }
                                },
                                onAddFilter = viewModel::addFilter,
                                onToggleBookmark = { viewModel.toggleBookmark(log.id) },
                                selectedCount = selectedIds.size,
                                onCopySelected = copySelected,
                                onCopyColumnSelected = copyColumnSelected,
                                selectedMessages = {
                                    viewModel.logs.filter { it.id in viewModel.selectedIds }
                                        .joinToString("") { it.message }
                                },
                                activeCell = activeCell,
                                onActivateCell = { column ->
                                    activeCell = log.id to column
                                    // Entering substring-copy mode: drop the row selection so the
                                    // highlighted text field isn't fighting the selected-row background.
                                    viewModel.clearSelection()
                                    viewModel.setAutoFollow(false)
                                },
                                onExitSelection = {
                                    activeCell = null
                                    runCatching { focusRequester.requestFocus() }
                                },
                                isExpanded = log.id in viewModel.expandedIds,
                                onToggleExpand = { viewModel.toggleExpand(log.id) },
                                isCrash = log.id in viewModel.crashIds,
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
                    if (!autoFollow) {
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            style = scrollbarStyle,
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
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp)
                            .widthIn(min = 360.dp),
                    )
                }

                // Floating crash/ANR navigator, top-right over the logs; drops below the find bar
                // when that's open so the two don't overlap.
                if (viewModel.crashIds.isNotEmpty()) {
                    CrashNavigator(
                        count = viewModel.crashIds.size,
                        current = viewModel.crashOrdinal(selectedIds.lastOrNull() ?: -1),
                        onPrev = { jumpToCrash(forward = false) },
                        onNext = { jumpToCrash(forward = true) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 12.dp)
                            .padding(top = if (findVisible) 48.dp else 4.dp),
                    )
                }
            }

            Divider(Orientation.Horizontal)

            if (quickFilterVisible) {
                QuickFilterBar(
                    initialQuery = searchQuery,
                    error = searchQueryError,
                    history = searchHistory,
                    onSearchChange = viewModel::updateSearchQuery,
                    onCommit = viewModel::commitSearchQuery,
                    onClearHistory = viewModel::clearSearchHistory,
                )
                Divider(Orientation.Horizontal)
            }

            FilterBar(
                filters = filters,
                quickFilterVisible = quickFilterVisible,
                onToggleQuickFilter = {
                    // Hiding the bar keeps the active filter; the bar reseeds from it when shown again.
                    viewModel.setQuickFilterVisible(!quickFilterVisible)
                },
                onOpenFilters = {
                    filtersDialogInitialId = null
                    filtersDialogAddOnOpen = false
                    showFiltersDialog = true
                },
                onToggleEnabled = viewModel::setFilterEnabled,
                onRemove = viewModel::removeFilter,
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
                onMove = viewModel::reorderFilters,
            )
        }

        if (showFiltersDialog) {
            FiltersDialog(
                filters = filters,
                onDismiss = { showFiltersDialog = false },
                onSave = viewModel::replaceFilters,
                initialSelectedId = filtersDialogInitialId,
                addOnOpen = filtersDialogAddOnOpen,
                listWidth = filtersListWidth,
                onListWidthChange = viewModel::setFiltersListWidth,
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

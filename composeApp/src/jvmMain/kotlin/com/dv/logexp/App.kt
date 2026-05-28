package com.dv.logexp

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FontWeightPreset
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.service.SystemFonts
import com.dv.logexp.ui.*
import com.dv.logexp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.theme.textAreaStyle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    val themeMode by viewModel.themeMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val visibleColumns by viewModel.visibleColumns.collectAsState()
    val columnWidths by viewModel.columnWidths.collectAsState()
    val autoFollow by viewModel.autoFollow.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val fontWeight by viewModel.fontWeight.collectAsState()
    val resolvedFontFamily = remember(fontFamily, fontWeight) {
        SystemFonts.resolve(fontFamily, FontWeightPreset.fromName(fontWeight).weight)
    }
    val selectedIds = viewModel.selectedIds
    val bookmarkedIds = viewModel.bookmarkedIds
    val logs = viewModel.logs

    var showFiltersDialog by remember { mutableStateOf(false) }
    var filtersDialogInitialId by remember { mutableStateOf<String?>(null) }
    var filtersDialogAddOnOpen by remember { mutableStateOf(false) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var currentMatchIdx by remember { mutableStateOf(0) }

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val findFocusRequester = remember { FocusRequester() }
    var shiftHeld by remember { mutableStateOf(false) }

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    IntUiTheme(isDark = isDark) {
        val listState = rememberLazyListState()

        val filteredLogs = remember(logs.size, searchQuery, appFilter, filters) {
            val activeFilters = filters.filter { it.enabled }
            val showFilters = activeFilters.filter {
                it.mode == FilterMode.SHOW || it.mode == FilterMode.SHOW_AND_HIGHLIGHT
            }
            val hideFilters = activeFilters.filter { it.mode == FilterMode.HIDE }
            logs.filter { log ->
                val matchesQuery = searchQuery.isBlank()
                        || log.content.contains(searchQuery, ignoreCase = true)
                val matchesApp = appFilter.isBlank()
                        || log.appName.contains(appFilter, ignoreCase = true)
                        || log.pid == appFilter
                if (!matchesQuery || !matchesApp) return@filter false
                if (hideFilters.any { it.matches(log) }) return@filter false
                if (showFilters.isNotEmpty() && showFilters.none { it.matches(log) }) return@filter false
                true
            }
        }

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
            viewModel.toggleSelection(filteredLogs[rowIdx].id, multiSelect = false)
            viewModel.setAutoFollow(false)
        }

        LaunchedEffect(findVisible) {
            if (findVisible) findFocusRequester.requestFocus()
            else focusRequester.requestFocus()
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
                    if (scrolledUp && viewModel.autoFollow.value && !isAutoScrolling) {
                        viewModel.setAutoFollow(false)
                    }
                    prevIndex = idx
                    prevOffset = off
                }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    shiftHeld = event.isShiftPressed
                    false
                }
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
                )
        ) {
            TopBar(
                selectedCount = selectedIds.size,
                autoFollow = autoFollow,
                visibleColumns = visibleColumns,
                themeMode = themeMode,
                devices = devices,
                selectedDevice = selectedDevice,
                onSelectDevice = viewModel::selectDevice,
                onRefreshDevices = viewModel::refreshDevices,
                onClear = viewModel::clearLogs,
                onCopySelected = {
                    val text = logs.filter { it.id in selectedIds }.joinToString("\n") { it.content }
                    clipboardManager.setText(AnnotatedString(text))
                },
                onToggleAutoFollow = viewModel::toggleAutoFollow,
                onToggleColumn = viewModel::toggleColumn,
                onToggleTheme = viewModel::toggleTheme,
                fontSize = fontSize,
                onSetFontSize = viewModel::setFontSize,
                fontFamily = fontFamily,
                onSetFontFamily = viewModel::setFontFamily,
                fontWeight = fontWeight,
                onSetFontWeight = viewModel::setFontWeight,
            )

            Divider(Orientation.Horizontal)

            LogHeaders(
                visibleColumns = visibleColumns,
                columnWidths = columnWidths,
                isDarkMode = isDark,
                onResize = viewModel::updateColumnWidth,
                onHideColumn = viewModel::toggleColumn,
                currentWidthOf = { col -> viewModel.columnWidths.value[col] ?: 100f },
            )

            Divider(Orientation.Horizontal)

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .background(
                            JewelTheme.textAreaStyle.colors.background,
                            RoundedCornerShape(16.dp),
                        ),
                ) {
                    SelectionContainer {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(filteredLogs, key = { _, log -> log.id }) { index, log ->
                                val hl = highlightFilters.firstOrNull { it.matches(log) }
                                val rowContent = @Composable {
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
                                    onSelect = { multi, range ->
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
                                )
                                }
                                if (shiftHeld) DisableSelection { rowContent() } else rowContent()
                            }
                        }
                    }
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

                if (findVisible) {
                    FindBar(
                        query = findQuery,
                        onQueryChange = { findQuery = it; currentMatchIdx = 0 },
                        matchCount = findMatchIndices.size,
                        currentIdx = currentMatchIdx,
                        onNext = { jumpToMatch(currentMatchIdx + 1) },
                        onPrev = { jumpToMatch(currentMatchIdx - 1) },
                        onClose = { findVisible = false; findQuery = "" },
                        focusRequester = findFocusRequester,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp)
                            .widthIn(min = 360.dp),
                    )
                }
            }

            Divider(Orientation.Horizontal)

            if (filters.isNotEmpty()) {
                FilterChipsBar(
                    filters = filters,
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
                )
                Divider(Orientation.Horizontal)
            }

            BottomBar(
                initialAppFilter = appFilter,
                logCount = filteredLogs.size,
                totalLogCount = logs.size,
                onSearchChange = viewModel::updateSearchQuery,
                onAppFilterChange = viewModel::updateAppFilter,
            )
        }

        if (showFiltersDialog) {
            FiltersDialog(
                filters = filters,
                onDismiss = { showFiltersDialog = false },
                onSave = viewModel::replaceFilters,
                initialSelectedId = filtersDialogInitialId,
                addOnOpen = filtersDialogAddOnOpen,
            )
        }
    }
}

package com.dv.logexp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.ui.BottomBar
import com.dv.logexp.ui.FilterChipsBar
import com.dv.logexp.ui.FiltersDialog
import com.dv.logexp.ui.LogHeaders
import com.dv.logexp.ui.LogRow
import com.dv.logexp.ui.TopBar
import com.dv.logexp.ui.logKeyboardShortcuts
import com.dv.logexp.viewmodel.MainViewModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
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
    val selectedIds = viewModel.selectedIds
    val bookmarkedIds = viewModel.bookmarkedIds
    val logs = viewModel.logs

    var showFiltersDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

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
                )
        ) {
            TopBar(
                selectedCount = selectedIds.size,
                autoFollow = autoFollow,
                visibleColumns = visibleColumns,
                themeMode = themeMode,
                activeFilterCount = filters.count { it.enabled },
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
                onOpenFilters = { showFiltersDialog = true },
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
                VerticallyScrollableContainer(
                    listState as ScrollableState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .background(
                            JewelTheme.textAreaStyle.colors.background,
                            RoundedCornerShape(16.dp),
                        ),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredLogs, key = { _, log -> log.id }) { _, log ->
                            val hl = highlightFilters.firstOrNull { it.matches(log) }
                            LogRow(
                                log = log,
                                visibleColumns = visibleColumns,
                                columnWidths = columnWidths,
                                isDarkMode = isDark,
                                isSelected = log.id in selectedIds,
                                isBookmarked = log.id in bookmarkedIds,
                                highlightColor = hl?.let { Color(it.highlightColor.toInt()) },
                                onSelect = { multi -> viewModel.toggleSelection(log.id, multi) },
                                onAddFilter = viewModel::addFilter,
                            )
                        }
                    }
                }
            }

            Divider(Orientation.Horizontal)

            if (filters.isNotEmpty()) {
                FilterChipsBar(
                    filters = filters,
                    onToggleEnabled = viewModel::setFilterEnabled,
                    onRemove = viewModel::removeFilter,
                )
                Divider(Orientation.Horizontal)
            }

            BottomBar(
                initialAppFilter = appFilter,
                logCount = filteredLogs.size,
                onSearchChange = viewModel::updateSearchQuery,
                onAppFilterChange = viewModel::updateAppFilter,
            )
        }

        if (showFiltersDialog) {
            FiltersDialog(
                filters = filters,
                onDismiss = { showFiltersDialog = false },
                onSave = viewModel::replaceFilters,
            )
        }
    }
}

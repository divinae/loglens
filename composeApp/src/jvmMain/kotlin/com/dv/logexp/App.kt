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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.ui.BottomBar
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
    val isCapturing by viewModel.isCapturing.collectAsState()
    val selectedIds = viewModel.selectedIds
    val bookmarkedIds = viewModel.bookmarkedIds
    val logs = viewModel.logs

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

        val filteredLogs = remember(logs.size, searchQuery, appFilter) {
            logs.filter { log ->
                val matchesQuery = searchQuery.isBlank()
                    || log.content.contains(searchQuery, ignoreCase = true)
                val matchesApp = appFilter.isBlank()
                    || log.appName.contains(appFilter, ignoreCase = true)
                    || log.pid == appFilter
                matchesQuery && matchesApp
            }
        }

        var isAutoScrolling by remember { mutableStateOf(false) }

        LaunchedEffect(filteredLogs.size, autoFollow) {
            if (autoFollow && filteredLogs.isNotEmpty() && !listState.isScrollInProgress) {
                isAutoScrolling = true
                listState.animateScrollToItem(filteredLogs.size - 1)
                isAutoScrolling = false
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress && autoFollow && !isAutoScrolling) {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisibleIndex < filteredLogs.size - 5) {
                    viewModel.setAutoFollow(false)
                }
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
                isCapturing = isCapturing,
                selectedCount = selectedIds.size,
                autoFollow = autoFollow,
                visibleColumns = visibleColumns,
                themeMode = themeMode,
                onStart = {
                    viewModel.refreshPackages()
                    viewModel.startCapture()
                },
                onClear = viewModel::clearLogs,
                onCopySelected = {
                    val text = logs.filter { it.id in selectedIds }.joinToString("\n") { it.content }
                    clipboardManager.setText(AnnotatedString(text))
                },
                onToggleAutoFollow = viewModel::toggleAutoFollow,
                onToggleColumn = viewModel::toggleColumn,
                onToggleTheme = viewModel::toggleTheme,
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
                SelectionContainer {
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
                                LogRow(
                                    log = log,
                                    visibleColumns = visibleColumns,
                                    columnWidths = columnWidths,
                                    isDarkMode = isDark,
                                    isSelected = log.id in selectedIds,
                                    isBookmarked = log.id in bookmarkedIds,
                                    onSelect = { multi -> viewModel.toggleSelection(log.id, multi) },
                                )
                            }
                        }
                    }
                }
            }

            Divider(Orientation.Horizontal)

            BottomBar(
                initialAppFilter = appFilter,
                logCount = filteredLogs.size,
                onSearchChange = viewModel::updateSearchQuery,
                onAppFilterChange = viewModel::updateAppFilter,
            )
        }
    }
}

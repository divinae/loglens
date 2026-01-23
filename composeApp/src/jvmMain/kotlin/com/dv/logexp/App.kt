package com.dv.logexp

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.theme.textAreaStyle
import java.awt.Cursor

data class LogEntry(
    val id: Int,
    val content: String,
    val time: String = "",
    val pid: String = "",
    val tid: String = "",
    val level: String = "",
    val tag: String = "",
    val message: String = "",
    val appName: String = ""
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    val themeMode by viewModel.themeMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val visibleColumns by viewModel.visibleColumns.collectAsState()
    val columnWidths by viewModel.columnWidths.collectAsState()
    val autoFollow by viewModel.autoFollow.collectAsState()
    val selectedIds = viewModel.selectedIds
    val clipboardManager = LocalClipboardManager.current

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    IntUiTheme(isDark = isDark) {
        val logs = viewModel.logs
        val isCapturing by viewModel.isCapturing.collectAsState()
        val listState = rememberLazyListState()

        val filteredLogs = remember(logs.size, searchQuery, appFilter) {
            logs.filter { log ->
                val matchesQuery =
                    searchQuery.isBlank() || log.content.contains(searchQuery, ignoreCase = true)
                val matchesApp = appFilter.isBlank() || log.appName.contains(
                    appFilter,
                    ignoreCase = true
                ) || log.pid == appFilter
                matchesQuery && matchesApp
            }
        }

        LaunchedEffect(filteredLogs.size, autoFollow) {
            if (autoFollow && filteredLogs.isNotEmpty() && !listState.isScrollInProgress) {
                listState.animateScrollToItem(filteredLogs.size - 1)
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress && autoFollow) {
                val lastVisibleIndex =
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisibleIndex < filteredLogs.size - 5) {
                    viewModel.setAutoFollow(false)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DefaultButton(
                    onClick = {
                        viewModel.refreshPackages()
                        viewModel.startCapture()
                    },
                    enabled = !isCapturing
                ) {
                    Text(if (isCapturing) "Capturing..." else "Start")
                }

                DefaultButton(onClick = { viewModel.clearLogs() }) {
                    Text("Clear")
                }

                if (selectedIds.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        val textToCopy = logs.filter { selectedIds.contains(it.id) }
                            .joinToString("\n") { it.content }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    }) {
                        Text("Copy Selected (${selectedIds.size})")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoFollow,
                        onCheckedChange = { viewModel.toggleAutoFollow() }
                    )
                    Text("Auto-Follow", modifier = Modifier.padding(start = 4.dp))
                }

                SettingsMenu(
                    visibleColumns = visibleColumns,
                    onToggleColumn = viewModel::toggleColumn,
                    themeMode = themeMode,
                    onToggleTheme = viewModel::toggleTheme
                )
            }

            Divider(Orientation.Horizontal)

            // Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogColumn.entries.forEach { column ->
                    if (visibleColumns.contains(column)) {
                        if (column == LogColumn.MESSAGE) {
                            Text(
                                text = column.displayName,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val currentWidth = columnWidths[column] ?: 100f
                            Row(
                                modifier = Modifier.width(currentWidth.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = column.displayName,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(16.dp)
                                        .pointerHoverIcon(
                                            PointerIcon(
                                                Cursor.getPredefinedCursor(
                                                    Cursor.E_RESIZE_CURSOR
                                                )
                                            )
                                        )
                                        .pointerInput(column) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val latestWidth =
                                                    viewModel.columnWidths.value[column] ?: 100f
                                                viewModel.updateColumnWidth(
                                                    column,
                                                    latestWidth + dragAmount.x
                                                )
                                            }
                                        }
                                        .background(if (isDark) Color.Gray else Color.LightGray)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Divider(Orientation.Horizontal)

            // Log List
            Box(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    VerticallyScrollableContainer(
                        listState as ScrollableState,
                        modifier =
                            Modifier.fillMaxSize()
                                .background(JewelTheme.textAreaStyle.colors.background)
                                .border(
                                    Stroke.Alignment.Outside,
                                    1.dp,
                                    JewelTheme.globalColors.borders.normal
                                ),
//                        style = style,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredLogs, key = { it.id }) { log ->
                                val isSelected = selectedIds.contains(log.id)
                                LogRow(
                                    log = log,
                                    visibleColumns = visibleColumns,
                                    columnWidths = columnWidths,
                                    isDarkMode = isDark,
                                    isSelected = isSelected,
                                    onSelect = { multi -> viewModel.toggleSelection(log.id, multi) }
                                )
                            }
                        }
                    }
                }
            }

            Divider(Orientation.Horizontal)

            // Bottom Bar
            Row(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val state1 = rememberTextFieldState("")
                TextField(
                    state = state1,
                    placeholder = { Text("Search logs...") },
                    modifier = Modifier.weight(1f),
                )

//                TextField(
//                    value = searchQuery,
//                    onValueChange = { viewModel.updateSearchQuery(it) },
//                    modifier = Modifier.weight(1f),
//                    placeholder = { Text("Search logs...") }
//                )
//
//                TextField(
//                    value = appFilter,
//                    onValueChange = { viewModel.updateAppFilter(it) },
//                    modifier = Modifier.width(150.dp),
//                    placeholder = { Text("App/PID...") }
//                )

                Text(
                    text = "Logs: ${filteredLogs.size}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsMenu(
    visibleColumns: Set<LogColumn>,
    onToggleColumn: (LogColumn) -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        DefaultButton(onClick = { expanded = true }) {
            Text("Settings")
        }

//        DropdownMenu(
//            expanded = expanded,
//            onDismissRequest = { expanded = false }
//        ) {
//            DropdownMenuItem(onClick = {
//                onToggleTheme()
//                expanded = false
//            }) {
//                Text("Theme: ${themeMode.name}")
//            }
//
//            Divider(Orientation.Horizontal)
//
//            LogColumn.entries.forEach { column ->
//                DropdownMenuItem(onClick = { onToggleColumn(column) }) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Checkbox(
//                            checked = visibleColumns.contains(column),
//                            onCheckedChange = { onToggleColumn(column) }
//                        )
//                        Text(column.displayName, modifier = Modifier.padding(start = 8.dp))
//                    }
//                }
//            }
//        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LogRow(
    log: LogEntry,
    visibleColumns: Set<LogColumn>,
    columnWidths: Map<LogColumn, Float>,
    isDarkMode: Boolean,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val color = getLogColor(log.level, isDarkMode)
    val backgroundColor = if (isSelected) {
        if (isDarkMode) Color(0xFF264B77) else Color(0xFFD6EAF8)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Press) {
                val isMulti =
                    it.keyboardModifiers.isCtrlPressed || it.keyboardModifiers.isMetaPressed
                onSelect(isMulti)
            }
            .padding(vertical = 1.dp)
            .padding(horizontal = 8.dp)
    ) {
        LogColumn.entries.forEach { column ->
            if (visibleColumns.contains(column)) {
                val text = when (column) {
                    LogColumn.TIME -> log.time
                    LogColumn.PID -> log.pid
                    LogColumn.TID -> log.tid
                    LogColumn.LEVEL -> log.level
                    LogColumn.TAG -> log.tag
                    LogColumn.MESSAGE -> log.message
                }

                val isWrap = column == LogColumn.MESSAGE || column == LogColumn.TAG

                if (column == LogColumn.MESSAGE) {
                    LogCell(text, color, Modifier.weight(1f), wrapText = isWrap)
                } else {
                    val width = columnWidths[column] ?: 100f
                    LogCell(text, color, Modifier.width(width.dp), wrapText = isWrap)
                }
            }
        }
    }
}

@Composable
fun LogCell(text: String, color: Color, modifier: Modifier, wrapText: Boolean) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = if (wrapText) Int.MAX_VALUE else 1,
        overflow = if (wrapText) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

fun getLogColor(level: String, isDarkMode: Boolean): Color {
    return when (level.trim()) {
        "E", "F" -> if (isDarkMode) Color(0xFFFF5252) else Color(0xFFC62828)
        "W" -> if (isDarkMode) Color(0xFFFFD740) else Color(0xFFE65100)
        "I" -> if (isDarkMode) Color(0xFF69F0AE) else Color(0xFF2E7D32)
        "D" -> if (isDarkMode) Color(0xFF40C4FF) else Color(0xFF1565C0)
        "V" -> if (isDarkMode) Color.LightGray else Color(0xFF424242)
        else -> if (isDarkMode) Color.LightGray else Color(0xFF212121)
    }
}

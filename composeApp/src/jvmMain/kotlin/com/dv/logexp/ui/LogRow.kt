package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.dv.logexp.model.FieldPattern
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FilterRule
import com.dv.logexp.model.FontWeightPreset
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.LogEntry
import com.dv.logexp.ui.theme.ROW_SELECTED_DARK
import com.dv.logexp.ui.theme.ROW_SELECTED_LIGHT
import com.dv.logexp.ui.theme.logLevelColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LogRow(
    log: LogEntry,
    visibleColumns: Set<LogColumn>,
    columnWidths: Map<LogColumn, Float>,
    isDarkMode: Boolean,
    isSelected: Boolean,
    isBookmarked: Boolean,
    findQuery: String = "",
    highlightColor: Color? = null,
    fontSize: TextUnit = 12.sp,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontWeight: String = "NORMAL",
    onSelect: (multi: Boolean, range: Boolean) -> Unit,
    onAddFilter: (FilterRule) -> Unit = {},
    onToggleBookmark: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val color = logLevelColor(log.level, isDarkMode)
    val resolvedWeight = remember(fontWeight) { FontWeight(FontWeightPreset.fromName(fontWeight).weight) }
    val bookmarkTint = JewelTheme.globalColors.text.info.copy(alpha = 0.10f)
    val backgroundColor = when {
        isSelected && isDarkMode -> ROW_SELECTED_DARK
        isSelected -> ROW_SELECTED_LIGHT
        highlightColor != null -> highlightColor
        isBookmarked -> bookmarkTint
        else -> Color.Transparent
    }
    val findHighlightColor = if (isDarkMode) Color(0xFF5A4A1F) else Color(0xFFFFF59D)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) {
                    if (!isSelected) onSelect(false, false)
                    return@onPointerEvent
                }
                val multi = it.keyboardModifiers.isCtrlPressed || it.keyboardModifiers.isMetaPressed
                val range = it.keyboardModifiers.isShiftPressed
                onSelect(multi, range)
            }
            .padding(vertical = 1.dp)
            .padding(horizontal = 8.dp)
    ) {
        Box(Modifier.width(6.dp).align(Alignment.CenterVertically)) {
            if (isBookmarked) {
                Box(
                    Modifier.size(6.dp).background(JewelTheme.globalColors.text.info, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(2.dp))

        LogColumn.entries.forEach { column ->
            if (column !in visibleColumns) return@forEach
            val text = when (column) {
                LogColumn.DATE -> log.date
                LogColumn.TIME -> log.time
                LogColumn.PID -> log.pid
                LogColumn.TID -> log.tid
                LogColumn.PROCESS -> log.appName
                LogColumn.LEVEL -> log.level
                LogColumn.TAG -> log.tag
                LogColumn.MESSAGE -> log.message
            }

            val cellModifier = if (column == LogColumn.MESSAGE) {

                Modifier.weight(1f)
            } else {
                Modifier.width((columnWidths[column] ?: 100f).dp)
            }
            val wrap = column == LogColumn.MESSAGE

            FilterableCell(
                column = column,
                value = text,
                onAddFilter = onAddFilter,
                onCopyCell = { clipboard.setText(AnnotatedString(text)) },
                onCopyRow = { clipboard.setText(AnnotatedString(log.content)) },
                isBookmarked = isBookmarked,
                onToggleBookmark = onToggleBookmark,
                modifier = cellModifier,
            ) {
                LogCell(text, color, Modifier, wrapText = wrap, fontSize = fontSize, fontFamily = fontFamily, fontWeight = resolvedWeight, findQuery = findQuery, findColor = findHighlightColor)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FilterableCell(
    column: LogColumn,
    value: String,
    onAddFilter: (FilterRule) -> Unit,
    onCopyCell: () -> Unit,
    onCopyRow: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val filterItems = filterMenuItems(column, value, onAddFilter)
    val copyItems = buildList {
        if (value.isNotEmpty()) add(FilterMenuItem("Copy ${columnLabel(column)}", onCopyCell))
        add(FilterMenuItem("Copy row", onCopyRow))
    }
    val bookmarkItem = FilterMenuItem(
        if (isBookmarked) "Remove bookmark" else "Add bookmark",
        onToggleBookmark,
    )
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        modifier = modifier.onPointerEvent(PointerEventType.Press) { event ->
            if (event.buttons.isSecondaryPressed) {
                val pos = event.changes.first().position
                menuOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                showMenu = true
            }
        }
    ) {
        content()
        if (showMenu) {
            PopupMenu(
                onDismissRequest = { showMenu = false; true },
                popupPositionProvider = CursorPositionProvider(menuOffset),
            ) {
                selectableItem(
                    selected = false,
                    onClick = { bookmarkItem.action(); showMenu = false },
                ) {
                    Text(bookmarkItem.label)
                }
                passiveItem {
                    Divider(
                        orientation = Orientation.Horizontal,
                        thickness = 1.dp,
                        color = JewelTheme.globalColors.borders.normal,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                copyItems.forEach { item ->
                    selectableItem(
                        selected = false,
                        onClick = { item.action(); showMenu = false },
                    ) {
                        Text(item.label)
                    }
                }
                if (copyItems.isNotEmpty() && filterItems.isNotEmpty()) {
                    passiveItem {
                        Divider(
                            orientation = Orientation.Horizontal,
                            thickness = 1.dp,
                            color = JewelTheme.globalColors.borders.normal,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                filterItems.forEach { item ->
                    selectableItem(
                        selected = false,
                        onClick = { item.action(); showMenu = false },
                    ) {
                        Text(item.label)
                    }
                }
            }
        }
    }
}

private data class FilterMenuItem(val label: String, val action: () -> Unit)

private fun filterMenuItems(
    column: LogColumn,
    value: String,
    onAddFilter: (FilterRule) -> Unit,
): List<FilterMenuItem> {
    if (value.isBlank()) return emptyList()
    val builder = ruleBuilder(column, value) ?: return emptyList()
    val label = labelFor(column, value)
    return listOf(
        FilterMenuItem("Show only $label") { onAddFilter(builder(FilterMode.SHOW)) },
        FilterMenuItem("Hide $label") { onAddFilter(builder(FilterMode.HIDE)) },
        FilterMenuItem("Highlight $label") { onAddFilter(builder(FilterMode.HIGHLIGHT)) },
    )
}

private fun ruleBuilder(column: LogColumn, value: String): ((FilterMode) -> FilterRule)? {
    val name = "${columnLabel(column)}: ${value.take(40)}"
    return when (column) {
        LogColumn.MESSAGE -> { mode -> FilterRule(name = name, mode = mode, message = FieldPattern(value)) }
        LogColumn.TAG -> { mode -> FilterRule(name = name, mode = mode, tag = FieldPattern(value, caseSensitive = true)) }
        LogColumn.PROCESS -> { mode -> FilterRule(name = name, mode = mode, app = FieldPattern(value, caseSensitive = true)) }
        LogColumn.PID -> { mode -> FilterRule(name = name, mode = mode, pid = FieldPattern(value, caseSensitive = true)) }
        LogColumn.LEVEL -> { mode -> FilterRule(name = name, mode = mode, levels = setOf(value)) }
        LogColumn.DATE, LogColumn.TIME, LogColumn.TID -> null
    }
}

private fun columnLabel(column: LogColumn): String = when (column) {
    LogColumn.MESSAGE -> "Message"
    LogColumn.TAG -> "Tag"
    LogColumn.PROCESS -> "App"
    LogColumn.PID -> "PID"
    LogColumn.LEVEL -> "Level"
    else -> column.displayName
}

private fun labelFor(column: LogColumn, value: String): String {
    val short = if (value.length > 30) value.take(30) + "..." else value
    return "${columnLabel(column)}=\"$short\""
}

@Composable
private fun LogCell(
    text: String,
    color: Color,
    modifier: Modifier,
    wrapText: Boolean,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    findQuery: String,
    findColor: Color,
) {
    val annotated = remember(text, findQuery, findColor) {
        highlightMatches(text, findQuery, findColor)
    }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        maxLines = if (wrapText) Int.MAX_VALUE else 1,
        overflow = if (wrapText) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

private fun highlightMatches(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val q = query.lowercase()
    var i = lower.indexOf(q)
    if (i < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        while (i >= 0) {
            addStyle(SpanStyle(background = color), i, i + q.length)
            i = lower.indexOf(q, i + q.length)
        }
    }
}

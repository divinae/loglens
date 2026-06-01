package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.FieldPattern
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FilterRule
import com.dv.logexp.model.LogColumn
import com.dv.logexp.ui.theme.HIGHLIGHT_COLORS
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private data class FilterMenuItem(val label: String, val icon: IconKey, val action: () -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FilterableCell(
    column: LogColumn,
    value: String,
    onAddFilter: (FilterRule) -> Unit,
    onCopyCell: () -> Unit,
    copyCellLabel: String,
    onCopyRow: () -> Unit,
    copyRowLabel: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    jsonFontFamily: FontFamily,
    jsonFontSize: TextUnit,
    jsonCandidate: () -> String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val filterBuilder = if (value.isNotBlank()) ruleBuilder(column, value) else null
    val filterLabel = labelFor(column, value)
    val copyItems = buildList {
        if (value.isNotEmpty()) {
            add(FilterMenuItem(copyCellLabel, AllIconsKeys.Actions.Copy, onCopyCell))
        }
        add(FilterMenuItem(copyRowLabel, AllIconsKeys.Actions.Copy, onCopyRow))
    }
    val bookmarkItem = FilterMenuItem(
        if (isBookmarked) "Remove bookmark" else "Add bookmark",
        if (isBookmarked) AllIconsKeys.Actions.Cancel else AllIconsKeys.Nodes.Bookmark,
        onToggleBookmark,
    )
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var jsonDialog by remember { mutableStateOf<String?>(null) }
    jsonDialog?.let { json ->
        JsonViewerDialog(
            json = json,
            fontFamily = jsonFontFamily,
            fontSize = jsonFontSize,
            onDismiss = { jsonDialog = null },
        )
    }
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
                    iconKey = bookmarkItem.icon,
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
                        iconKey = item.icon,
                        onClick = { item.action(); showMenu = false },
                    ) {
                        Text(item.label)
                    }
                }
                val formattedJson = formatJson(jsonCandidate())
                if (formattedJson != null) {
                    selectableItem(
                        selected = false,
                        iconKey = AllIconsKeys.Actions.PrettyPrint,
                        onClick = { jsonDialog = formattedJson; showMenu = false },
                    ) {
                        Text("Format JSON")
                    }
                }
                if (copyItems.isNotEmpty() && filterBuilder != null) {
                    passiveItem {
                        Divider(
                            orientation = Orientation.Horizontal,
                            thickness = 1.dp,
                            color = JewelTheme.globalColors.borders.normal,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                if (filterBuilder != null) {
                    selectableItem(
                        selected = false,
                        iconKey = AllIconsKeys.Actions.Show,
                        onClick = { onAddFilter(filterBuilder(FilterMode.SHOW)); showMenu = false },
                    ) {
                        Text("Show only $filterLabel")
                    }
                    selectableItem(
                        selected = false,
                        iconKey = AllIconsKeys.Actions.Cancel,
                        onClick = { onAddFilter(filterBuilder(FilterMode.HIDE)); showMenu = false },
                    ) {
                        Text("Hide $filterLabel")
                    }
                    passiveItem {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                key = AllIconsKeys.Actions.Highlighting,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("Highlight", modifier = Modifier.padding(start = 4.dp, end = 4.dp))
                            HIGHLIGHT_COLORS.forEach { (color, _) ->
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .background(Color(color.toInt()), CircleShape)
                                        .border(1.dp, Color.Gray, CircleShape)
                                        .clickable {
                                            onAddFilter(
                                                filterBuilder(FilterMode.HIGHLIGHT).copy(highlightColor = color)
                                            )
                                            showMenu = false
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ruleBuilder(column: LogColumn, value: String): ((FilterMode) -> FilterRule)? {
    val shortValue = if (value.length > 20) value.take(20) + "..." else value
    val name = "${shortLabel(column)} $shortValue"
    return when (column) {
        LogColumn.MESSAGE -> { mode -> FilterRule(name = name, mode = mode, message = FieldPattern(value)) }
        LogColumn.TAG -> { mode ->
            FilterRule(name = name, mode = mode, tag = FieldPattern(value, caseSensitive = true))
        }

        LogColumn.PROCESS -> { mode ->
            FilterRule(name = name, mode = mode, app = FieldPattern(value, caseSensitive = true))
        }

        LogColumn.PID -> { mode ->
            FilterRule(name = name, mode = mode, pid = FieldPattern(value, caseSensitive = true))
        }

        LogColumn.LEVEL -> { mode -> FilterRule(name = name, mode = mode, levels = setOf(value)) }
        LogColumn.INDEX, LogColumn.DATE, LogColumn.TIME, LogColumn.TID -> null
    }
}

internal fun columnLabel(column: LogColumn): String = when (column) {
    LogColumn.MESSAGE -> "Message"
    LogColumn.TAG -> "Tag"
    LogColumn.PROCESS -> "App"
    LogColumn.PID -> "PID"
    LogColumn.LEVEL -> "Level"
    else -> column.displayName
}

/** Short prefix used for auto-generated filter names. */
private fun shortLabel(column: LogColumn): String = when (column) {
    LogColumn.MESSAGE -> "msg"
    LogColumn.TAG -> "tag"
    LogColumn.PROCESS -> "app"
    LogColumn.PID -> "pid"
    LogColumn.LEVEL -> "lvl"
    else -> column.displayName
}

private fun labelFor(column: LogColumn, value: String): String {
    val short = if (value.length > 30) value.take(30) + "..." else value
    return "${columnLabel(column)}=\"$short\""
}

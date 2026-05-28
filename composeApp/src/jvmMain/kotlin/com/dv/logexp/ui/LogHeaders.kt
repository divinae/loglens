package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.logexp.model.LogColumn
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LogHeaders(
    visibleColumns: Set<LogColumn>,
    columnWidths: Map<LogColumn, Float>,
    isDarkMode: Boolean,
    onResize: (LogColumn, Float) -> Unit,
    onToggleColumn: (LogColumn) -> Unit,
    currentWidthOf: (LogColumn) -> Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        LogColumn.entries.forEach { column ->
            if (column !in visibleColumns) return@forEach

            val widthDp = if (column == LogColumn.MESSAGE) null else (columnWidths[column] ?: 100f)
            var showContextMenu by remember { mutableStateOf(false) }
            var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
            val headerModifier =
                if (widthDp == null) Modifier.weight(1f) else Modifier.width(widthDp.dp)

            Box(modifier = headerModifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Secondary) {
                                val pos = event.changes.first().position
                                menuOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                                showContextMenu = true
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = column.displayName,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (widthDp != null) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .pointerHoverIcon(
                                    PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
                                )
                                .pointerInput(column) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onResize(column, currentWidthOf(column) + dragAmount.x)
                                    }
                                }
                                .background(if (isDarkMode) Color.Gray else Color.LightGray)
                        )
                    }
                }

                if (showContextMenu) {
                    PopupMenu(
                        onDismissRequest = {
                            showContextMenu = false
                            true
                        },
                        popupPositionProvider = CursorPositionProvider(menuOffset),
                    ) {
                        LogColumn.entries.forEach { col ->
                            val isVisible = col in visibleColumns
                            selectableItem(
                                selected = isVisible,
                                onClick = {
                                    onToggleColumn(col)
                                    showContextMenu = false
                                },
                            ) {
                                Text(if (isVisible) "✓ ${col.displayName}" else "    ${col.displayName}")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

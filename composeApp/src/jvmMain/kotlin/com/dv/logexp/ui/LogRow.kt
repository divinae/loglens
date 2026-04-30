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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.LogEntry
import com.dv.logexp.ui.theme.ROW_SELECTED_DARK
import com.dv.logexp.ui.theme.ROW_SELECTED_LIGHT
import com.dv.logexp.ui.theme.logLevelColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
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
    onSelect: (Boolean) -> Unit,
) {
    val color = logLevelColor(log.level, isDarkMode)
    val backgroundColor = when {
        !isSelected -> Color.Transparent
        isDarkMode -> ROW_SELECTED_DARK
        else -> ROW_SELECTED_LIGHT
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Press) {
                val multi = it.keyboardModifiers.isCtrlPressed || it.keyboardModifiers.isMetaPressed
                onSelect(multi)
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

            if (column == LogColumn.MESSAGE) {
                LogCell(text, color, Modifier.weight(1f), wrapText = true)
            } else {
                val width = columnWidths[column] ?: 100f
                LogCell(text, color, Modifier.width(width.dp), wrapText = false)
            }
        }
    }
}

@Composable
private fun LogCell(text: String, color: Color, modifier: Modifier, wrapText: Boolean) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = if (wrapText) Int.MAX_VALUE else 1,
        overflow = if (wrapText) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

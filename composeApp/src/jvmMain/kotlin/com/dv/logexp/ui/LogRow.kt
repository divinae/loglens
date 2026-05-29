package com.dv.logexp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.logexp.model.FilterRule
import com.dv.logexp.model.FontWeightPreset
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.LogEntry
import com.dv.logexp.model.cell
import com.dv.logexp.ui.theme.ROW_SELECTED_DARK
import com.dv.logexp.ui.theme.ROW_SELECTED_LIGHT
import com.dv.logexp.ui.theme.islandsPopup
import com.dv.logexp.ui.theme.logLevelColor
import com.dv.logexp.util.copyToClipboard
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
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
    softWrap: Boolean = true,
    onSelect: (multi: Boolean, range: Boolean) -> Unit,
    onAddFilter: (FilterRule) -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    selectedCount: Int = 0,
    onCopySelected: () -> Unit = {},
    onCopyColumnSelected: (LogColumn) -> Unit = {},
    selectedMessages: () -> String = { "" },
) {
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
    var messageOverflows by remember { mutableStateOf(false) }
    val popupBg = islandsPopup(isDarkMode)
    val borderColor = JewelTheme.globalColors.borders.normal
    val textColor = JewelTheme.globalColors.text.normal
    val tooltipStyle = remember(popupBg, borderColor, textColor) {
        TooltipStyle(
            colors = TooltipColors(
                background = popupBg,
                content = textColor,
                border = borderColor,
                shadow = Color(0x33000000),
            ),
            metrics = TooltipMetrics.defaults(
                showDelay = 800.milliseconds,
                regularDisappearDelay = 10_000.milliseconds,
            ),
        )
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val tooltipMaxWidth = with(density) {
        (windowInfo.containerSize.width.toDp() - 60.dp).coerceAtLeast(200.dp)
    }

    Tooltip(
        enabled = !softWrap && messageOverflows,
        style = tooltipStyle,
        tooltip = {
            Text(
                log.message,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                modifier = Modifier.widthIn(max = tooltipMaxWidth),
            )
        },
    ) {
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
            Box(Modifier.width(12.dp).align(Alignment.Top).padding(top = 2.dp)) {
                if (isBookmarked) {
                    Icon(
                        key = AllIconsKeys.Nodes.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.width(2.dp))

            LogColumn.entries.forEach { column ->
                if (column !in visibleColumns) return@forEach
                val text = log.cell(column)

                val cellModifier = if (column == LogColumn.MESSAGE) {

                    Modifier.weight(1f)
                } else {
                    Modifier.width((columnWidths[column] ?: 100f).dp)
                }
                val wrap = column == LogColumn.MESSAGE && softWrap

                val copyMulti = isSelected && selectedCount > 1
                FilterableCell(
                    column = column,
                    value = text,
                    onAddFilter = onAddFilter,
                    onCopyCell = {
                        if (copyMulti) onCopyColumnSelected(column)
                        else copyToClipboard(text)
                    },
                    copyCellLabel = if (copyMulti) {
                        "Copy ${columnLabel(column)} ($selectedCount rows)"
                    } else {
                        "Copy ${columnLabel(column)}"
                    },
                    onCopyRow = {
                        if (copyMulti) onCopySelected()
                        else copyToClipboard(log.content)
                    },
                    copyRowLabel = if (copyMulti) "Copy $selectedCount rows" else "Copy row",
                    isBookmarked = isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    jsonFontFamily = fontFamily,
                    jsonFontSize = fontSize,
                    jsonCandidate = {
                        if (copyMulti && column == LogColumn.MESSAGE) selectedMessages() else text
                    },
                    modifier = cellModifier,
                ) {
                    LogCell(
                        text,
                        color,
                        Modifier,
                        wrapText = wrap,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        fontWeight = resolvedWeight,
                        findQuery = findQuery,
                        findColor = findHighlightColor,
                        onTextLayout = if (column == LogColumn.MESSAGE) {
                            { result -> messageOverflows = result.hasVisualOverflow }
                        } else null,
                    )
                }
            }
        }
    }
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
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
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
        onTextLayout = { onTextLayout?.invoke(it) },
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

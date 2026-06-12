package com.dv.loglens.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.FontWeightPreset
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.LogEntry
import com.dv.loglens.model.LogQuery
import com.dv.loglens.model.cell
import com.dv.loglens.ui.theme.IslandsDark
import com.dv.loglens.ui.theme.IslandsLight
import com.dv.loglens.ui.theme.ROW_HOVER_DARK
import com.dv.loglens.ui.theme.ROW_HOVER_LIGHT
import com.dv.loglens.ui.theme.ROW_SELECTED_DARK
import com.dv.loglens.ui.theme.ROW_SELECTED_LIGHT
import com.dv.loglens.ui.theme.islandsPopup
import com.dv.loglens.ui.theme.logLevelColor
import com.dv.loglens.util.copyToClipboard
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
    findQuery: LogQuery = LogQuery.EMPTY,
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
    activeCell: Pair<Int, LogColumn>? = null,
    onActivateCell: (LogColumn) -> Unit = {},
    onExitSelection: () -> Unit = {},
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    isCrash: Boolean = false,
    deltaText: String = "",
    scrubbing: Boolean = false,
) {
    val color = logLevelColor(log.level, isDarkMode)
    // Only offer collapse for records worth it: a crash dump, or enough folded lines that hiding them
    // helps. Short multi-line messages (e.g. a 2-line warning) render normally without a fold link.
    val foldable = isCrash || log.message.count { it == '\n' } >= COLLAPSE_MIN_NEWLINES
    val resolvedWeight = remember(fontWeight) { FontWeight(FontWeightPreset.fromName(fontWeight).weight) }
    var hovered by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isSelected && isDarkMode -> ROW_SELECTED_DARK
        isSelected -> ROW_SELECTED_LIGHT
        highlightColor != null -> highlightColor
        hovered && isDarkMode -> ROW_HOVER_DARK
        hovered -> ROW_HOVER_LIGHT
        else -> Color.Transparent
    }
    val findHighlightColor = if (isDarkMode) Color(0xFF5A4A1F) else Color(0xFFFFF59D)
    var messageOverflows by remember { mutableStateOf(false) }
    // Press position of a right-click that landed on an empty gutter, anchoring the fallback menu.
    var rowMenuOffset by remember { mutableStateOf<IntOffset?>(null) }
    // Owned by the row, not the popup: the menu's onDismiss unmounts CellContextMenu, which would
    // tear down a dialog hosted inside it before it renders.
    var rowJsonDialog by remember { mutableStateOf<String?>(null) }
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

    @Composable
    fun rowBody() {
        // While the scrollbar thumb is being scrubbed the row skips its expensive interactive
        // machinery (per-cell context-menu cells, tooltips, find/link annotation) and renders plain
        // text in the exact same layout — every scrollbar jump composes a fresh viewport of ~dozens
        // of rows in one frame, and those extras are what makes that composition slow. Cheap handlers
        // (row selection, expand link) stay on unconditionally so the row remains clickable no matter
        // what. Layout metrics (paddings, fonts, wrap) are identical, so the viewport doesn't shift
        // when the drag ends and the full rows come back.
        val interactiveModifier = Modifier
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Press) {
                    // Right-click always selects the row (even when a cell consumed the press to show
                    // its own menu); only open the row's fallback menu for empty gutters.
                    if (it.buttons.isSecondaryPressed) {
                        if (!isSelected) onSelect(false, false)
                        if (it.changes.firstOrNull()?.isConsumed != true) {
                            val pos = it.changes.firstOrNull()?.position
                            if (pos != null) rowMenuOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                        }
                        return@onPointerEvent
                    }
                    // A cell double-click consumes the press to enter selection mode; don't also toggle the row.
                    if (it.changes.firstOrNull()?.isConsumed == true) return@onPointerEvent
                    val multi = it.keyboardModifiers.isCtrlPressed || it.keyboardModifiers.isMetaPressed
                    val range = it.keyboardModifiers.isShiftPressed
                    onSelect(multi, range)
                }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .then(interactiveModifier)
                .padding(vertical = 1.dp)
                .padding(horizontal = 8.dp)
        ) {
            rowMenuOffset?.let { off ->
                val copyMulti = isSelected && selectedCount > 1
                CellContextMenu(
                    column = LogColumn.MESSAGE,
                    value = log.message,
                    onAddFilter = onAddFilter,
                    onCopyCell = {
                        if (copyMulti) onCopyColumnSelected(LogColumn.MESSAGE) else copyToClipboard(log.message)
                    },
                    copyCellLabel = if (copyMulti) "Copy Message ($selectedCount rows)" else "Copy Message",
                    onCopyRow = { if (copyMulti) onCopySelected() else copyToClipboard(log.content) },
                    copyRowLabel = if (copyMulti) "Copy $selectedCount rows" else "Copy row",
                    isBookmarked = isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    jsonCandidate = { if (copyMulti) selectedMessages() else log.message },
                    onShowJson = { rowJsonDialog = it },
                    offset = off,
                    onDismiss = { rowMenuOffset = null },
                )
            }
            rowJsonDialog?.let { json ->
                JsonViewerDialog(
                    json = json,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    onDismiss = { rowJsonDialog = null },
                )
            }
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
                // Δt is relative to the previous row, computed by the caller; every other column is
                // a plain field of this entry.
                val text = if (column == LogColumn.DELTA) deltaText else log.cell(column)

                val cellModifier = if (column == LogColumn.MESSAGE) {
                    Modifier.weight(1f).fillMaxHeight()
                } else {
                    Modifier.width((columnWidths[column] ?: 100f).dp).fillMaxHeight()
                }
                val wrap = column == LogColumn.MESSAGE && softWrap
                val cellActive = activeCell?.first == log.id && activeCell.second == column

                val copyMulti = isSelected && selectedCount > 1

                // The cell body, shared by the scrub fast path and the full interactive cell below.
                val cellContent: @Composable () -> Unit = {
                    // A foldable record (crash dump / multiline print) collapses to one ellipsized
                    // line with the whole message flattened — not just the first line, so key fields
                    // past the first newline (e.g. a request URL) stay visible and clickable.
                    // Expanding shows the real layout regardless of the soft-wrap setting.
                    val cellFoldable = column == LogColumn.MESSAGE && foldable
                    val hasHidden = '\n' in text
                    val collapsed = cellFoldable && hasHidden && !isExpanded
                    val shown =
                        if (collapsed) text.take(COLLAPSE_PREVIEW_MAX_CHARS).replace('\n', ' ') else text
                    val wrapEffective = if (cellFoldable && hasHidden) isExpanded else wrap

                    @Composable
                    fun body() {
                        if (cellActive) {
                            SelectableLogCell(
                                text = shown,
                                color = color,
                                wrapText = wrapEffective,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                fontWeight = resolvedWeight,
                                onExit = onExitSelection,
                            )
                        } else {
                            LogCell(
                                shown,
                                color,
                                Modifier,
                                wrapText = wrapEffective,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                fontWeight = resolvedWeight,
                                // Find/URL spans are colour-only (no layout impact), so the scrub fast
                                // path can skip both scans without changing the row height.
                                findQuery = if (scrubbing) LogQuery.EMPTY else findQuery,
                                findColor = findHighlightColor,
                                // Only the message column gets clickable URLs; other columns skip
                                // the scan entirely.
                                linkColor = if (column == LogColumn.MESSAGE && !scrubbing) urlLinkColor(isDarkMode) else null,
                                onTextLayout = if (column == LogColumn.MESSAGE && !scrubbing) {
                                    { result -> messageOverflows = result.hasVisualOverflow }
                                } else null,
                            )
                        }
                    }

                    // Only the message column, when not wrapping and its single line is ellipsized,
                    // gets a hover tooltip with the full text. Gating on overflow keeps the Tooltip
                    // node (hover tracking + popup) off the many rows whose message already fits — a
                    // real scroll saving, especially with soft-wrap off where far more rows are on screen.
                    @Composable
                    fun bodyMaybeTooltip() {
                        if (column == LogColumn.MESSAGE && !softWrap && messageOverflows && !scrubbing) {
                            Tooltip(
                                style = tooltipStyle,
                                tooltip = {
                                    Text(
                                        log.message,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSize,
                                        modifier = Modifier.widthIn(max = tooltipMaxWidth),
                                    )
                                },
                            ) { body() }
                        } else {
                            body()
                        }
                    }

                    val showRepeat = column == LogColumn.MESSAGE && log.repeatCount > 1
                    if (cellFoldable || showRepeat) {
                        Row(verticalAlignment = Alignment.Top) {
                            if (isCrash) {
                                Text(
                                    "⚠",
                                    color = crashColor(isDarkMode),
                                    fontSize = fontSize,
                                    modifier = Modifier.padding(end = 3.dp),
                                )
                            }
                            // weight(1f) so body fills and the badges sit at the right edge.
                            Box(Modifier.weight(1f)) { bodyMaybeTooltip() }
                            if (showRepeat) {
                                // Pill badge for back-to-back duplicates coalesced into this one row.
                                val pill = linkColor(isDarkMode)
                                Text(
                                    "×${log.repeatCount}",
                                    color = pill,
                                    fontSize = (fontSize.value - 1).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .background(pill.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            if (collapsed) {
                                Text(
                                    "+${log.message.count { it == '\n' }}",
                                    color = color.copy(alpha = 0.5f),
                                    fontSize = fontSize,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            // Expand/collapse toggle at the far right; only for folded records, so
                            // single-line logs reserve no space. Same font + top alignment as the
                            // text so it lines up exactly; horizontal padding widens the hit target
                            // without changing the row height.
                            if (hasHidden) {
                                Text(
                                    if (isExpanded) "collapse" else "expand",
                                    color = linkColor(isDarkMode),
                                    fontSize = fontSize,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .onPointerEvent(PointerEventType.Press) { e ->
                                            e.changes.firstOrNull()?.consume()
                                            onToggleExpand()
                                        }
                                        .padding(start = 6.dp, end = 4.dp),
                                )
                            }
                        }
                    } else {
                        bodyMaybeTooltip()
                    }
                }

                if (scrubbing) {
                    // Fast path: same Box wrapper as FilterableCell (identical layout), none of its
                    // pointer/menu machinery.
                    Box(cellModifier) { cellContent() }
                } else {
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
                        onActivate = { onActivateCell(column) },
                        modifier = cellModifier,
                    ) { cellContent() }
                }
            }
        }
    }

    rowBody()
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
    findQuery: LogQuery,
    findColor: Color,
    linkColor: Color? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val annotated = remember(text, findQuery, findColor, linkColor) {
        buildLogCellAnnotated(text, findQuery, findColor, linkColor)
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

/**
 * The cell rendered while it's the active "selection mode" cell: a read-only [BasicTextField] so the
 * user can drag-select a substring and copy it (Ctrl+C, handled natively by the field). No
 * SelectionContainer is involved — selection state is scoped to this one field — so the prior
 * SelectionContainer-over-LazyColumn hierarchy crash cannot recur. Find highlighting is dropped while
 * active and returns when the cell reverts to [LogCell].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SelectableLogCell(
    text: String,
    color: Color,
    wrapText: Boolean,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    onExit: () -> Unit,
) {
    // Start with everything selected; the user can re-drag to narrow it.
    var tfv by remember(text) { mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Inherit Jewel's default text metrics (lineHeight, letterSpacing) just like the Text in LogCell,
    // then override only the per-row fields — otherwise BasicTextField's bare TextStyle uses a
    // different line height and the cell visibly grows/shifts the instant it goes active.
    val baseStyle = JewelTheme.defaultTextStyle
    val cellStyle = remember(baseStyle, color, fontSize, fontFamily, fontWeight) {
        baseStyle.merge(
            TextStyle(color = color, fontSize = fontSize, fontFamily = fontFamily, fontWeight = fontWeight),
        )
    }
    BasicTextField(
        value = tfv,
        // readOnly: the text never changes, so adopt only the caret/selection from edits.
        onValueChange = { tfv = tfv.copy(selection = it.selection) },
        readOnly = true,
        singleLine = !wrapText,
        maxLines = if (wrapText) Int.MAX_VALUE else 1,
        textStyle = cellStyle,
        cursorBrush = SolidColor(color),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .border(1.dp, color.copy(alpha = 0.4f))
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    onExit()
                    true
                } else {
                    false
                }
            },
    )
}

// Minimum folded newline count for a non-crash record to get the collapse affordance; below this a
// multi-line message just renders in full, so ordinary 2–3 line logs don't sprout an expand link.
private const val COLLAPSE_MIN_NEWLINES = 3

// Cap on the flattened collapsed preview, so a record with a huge payload doesn't allocate and
// lay out megabytes for a single ellipsized line.
private const val COLLAPSE_PREVIEW_MAX_CHARS = 500

/** Colour of the crash/ANR marker glyph; a saturated red that reads on both themes. */
private fun crashColor(isDarkMode: Boolean): Color =
    if (isDarkMode) Color(0xFFFF6B68) else Color(0xFFCC0000)

/** URLs inside messages follow the debug-level palette colour; the underline marks the link. */
private fun urlLinkColor(isDarkMode: Boolean): Color =
    if (isDarkMode) IslandsDark.logDebug else IslandsLight.logDebug

/** Standard hyperlink blue, tuned per theme for the expand/collapse link and message URLs. */
private fun linkColor(isDarkMode: Boolean): Color =
    if (isDarkMode) Color(0xFF589DF6) else Color(0xFF2470B3)

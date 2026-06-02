package com.dv.loglens.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dv.loglens.model.FilterMode
import com.dv.loglens.model.FilterRule
import com.dv.loglens.ui.theme.islandsPopup
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import kotlin.time.Duration.Companion.milliseconds

// FilterBar sits at the bottom, so anchor tooltips above the trigger to avoid covering it
// and to keep them on-screen.
@OptIn(ExperimentalFoundationApi::class)
private val abovePlacement = TooltipPlacement.ComponentRect(
    anchor = Alignment.TopCenter,
    alignment = Alignment.TopCenter,
    offset = DpOffset(0.dp, (-4).dp),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilterBar(
    filters: List<FilterRule>,
    quickFilterVisible: Boolean,
    onToggleQuickFilter: () -> Unit,
    onOpenFilters: () -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAddNew: () -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Long-press drag-to-reorder state. Slot bounds (left, right) are measured on an untransformed
    // outer Box so the drag translation never pollutes them.
    val slotBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(0f) }
    var draggedInitialCenter by remember { mutableStateOf(0f) }
    // Snapshot of every chip's center at drag start; target index is derived from this fixed
    // arrangement so reordering can't oscillate or stall after a single hop.
    var startCenters by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    // Live list ref so the long-lived gesture closure sees reordered indices, not the stale capture.
    val currentFilters by rememberUpdatedState(filters)
    Row(
        modifier = modifier.fillMaxWidth().padding(all = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OpenFiltersButton(onClick = onOpenFilters)
      Row(
        modifier = Modifier
            .weight(1f)
            .horizontalScroll(scrollState)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val dy = event.changes.first().scrollDelta.y
                if (dy != 0f) scope.launch { scrollState.scrollBy(dy * 60f) }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        filters.forEach { rule ->
            // Stable identity across reorder so the running drag gesture isn't cancelled when the
            // chip changes position (a positional forEach would restart pointerInput on every move).
            key(rule.id) {
                val state = remember(rule.id) { ContextMenuState() }
                val isDragging = rule.id == draggingId
                // Outer Box: measures the untransformed layout slot. Inner Box: visual drag + gesture.
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .onGloballyPositioned { coords ->
                            val left = coords.positionInParent().x
                            slotBounds[rule.id] = left to (left + coords.size.width)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = if (rule.id == draggingId) {
                                    val b = slotBounds[rule.id]
                                    if (b != null) draggedInitialCenter + dragDelta - (b.first + b.second) / 2f
                                    else 0f
                                } else 0f
                            }
                            .pointerInput(rule.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val b = slotBounds[rule.id]
                                        draggingId = rule.id
                                        dragDelta = 0f
                                        draggedInitialCenter = if (b != null) (b.first + b.second) / 2f else 0f
                                        startCenters = slotBounds.entries
                                            .map { (id, r) -> id to (r.first + r.second) / 2f }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount.x
                                        val from = currentFilters.indexOfFirst { it.id == draggingId }
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        val center = draggedInitialCenter + dragDelta
                                        val target = startCenters.count { (id, c) -> id != draggingId && c < center }
                                        if (target != from) onMove(from, target)
                                    },
                                    onDragEnd = { draggingId = null; dragDelta = 0f },
                                    onDragCancel = { draggingId = null; dragDelta = 0f },
                                )
                            },
                    ) {
                        ContextMenuArea(
                            items = {
                                listOf(
                                    ContextMenuItem("Edit filter") { onEdit(rule.id) },
                                    ContextMenuItem("Delete filter") { onRemove(rule.id) },
                                )
                            },
                            state = state,
                        ) {
                            FilterChip(rule = rule, onClick = { onToggleEnabled(rule.id, !rule.enabled) })
                        }
                    }
                }
            }
        }
        AddFilterChip(onClick = onAddNew)
      }
      QuickFilterToggleButton(visible = quickFilterVisible, onClick = onToggleQuickFilter)
    }
}

/** Draws a translucent hover overlay on top of the existing background, clipped to [shape]. */
@Composable
private fun Modifier.hoverOverlay(shape: Shape): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    return this
        .hoverable(interaction)
        .then(
            if (hovered) {
                Modifier.background(JewelTheme.globalColors.text.normal.copy(alpha = 0.08f), shape)
            } else {
                Modifier
            }
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OpenFiltersButton(onClick: () -> Unit) {
    val inactiveTint = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
    val popupBg = islandsPopup(JewelTheme.isDark)
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
                showDelay = 150.milliseconds,
                regularDisappearDelay = 10_000.milliseconds,
                placement = abovePlacement,
            ),
        )
    }
    IconActionButton(
        key = AllIconsKeys.General.Filter,
        contentDescription = "Filters",
        onClick = onClick,
        colorFilter = ColorFilter.tint(inactiveTint),
        tooltipStyle = tooltipStyle,
        tooltipPlacement = abovePlacement,
    ) {
        Text("Filters")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickFilterToggleButton(visible: Boolean, onClick: () -> Unit) {
    // Muted tint in both states — the toggle should not change colour when the bar is visible.
    val inactiveTint = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
    val popupBg = islandsPopup(JewelTheme.isDark)
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
                showDelay = 150.milliseconds,
                regularDisappearDelay = 10_000.milliseconds,
                placement = abovePlacement,
            ),
        )
    }
    // Plain (non-toggle) button so it never shows a selected highlight when the bar is visible.
    IconActionButton(
        key = if (visible) AllIconsKeys.General.ArrowDown else AllIconsKeys.General.ArrowUp,
        contentDescription = if (visible) "Hide quick filter bar" else "Show quick filter bar",
        onClick = onClick,
        colorFilter = ColorFilter.tint(inactiveTint),
        tooltipStyle = tooltipStyle,
        tooltipPlacement = abovePlacement,
    ) {
        Text(if (visible) "Hide quick filter bar" else "Show quick filter bar")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddFilterChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val popupBg = islandsPopup(JewelTheme.isDark)
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
                showDelay = 150.milliseconds,
                regularDisappearDelay = 10_000.milliseconds,
                placement = abovePlacement,
            ),
        )
    }
    Tooltip(tooltip = { Text("Add filter") }, style = tooltipStyle) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .hoverOverlay(shape)
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", fontSize = 14.sp, color = JewelTheme.globalColors.text.normal)
        }
    }
}

@Composable
private fun FilterChip(rule: FilterRule, onClick: () -> Unit) {
    val hasColor = rule.mode == FilterMode.HIGHLIGHT || rule.mode == FilterMode.SHOW_AND_HIGHLIGHT
    val shape = RoundedCornerShape(12.dp)
    val enabledAccent = JewelTheme.globalColors.text.info
    val background = if (rule.enabled) enabledAccent.copy(alpha = 0.05f) else Color.Transparent
    val borderColor = if (rule.enabled) enabledAccent else Color.Gray.copy(alpha = 0.5f)
    val textColor = if (rule.enabled) JewelTheme.globalColors.text.normal
    else JewelTheme.globalColors.text.disabled

    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(background, shape)
            .hoverOverlay(shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val modeLabel = when (rule.mode) {
            FilterMode.SHOW -> "S"
            FilterMode.HIDE -> "H"
            FilterMode.HIGHLIGHT -> null
            FilterMode.SHOW_AND_HIGHLIGHT -> "S"
        }
        if (hasColor) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(rule.highlightColor.toInt()), CircleShape)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
        }
        if (modeLabel != null) Text(modeLabel, fontSize = 10.sp, color = textColor)
        Text(rule.name, fontSize = 12.sp, color = textColor)
    }
}

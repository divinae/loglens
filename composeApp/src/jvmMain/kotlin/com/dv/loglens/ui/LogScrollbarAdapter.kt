package com.dv.loglens.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinux
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxDark
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxLight
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible

/**
 * A [ScrollbarAdapter] that measures the list in *item* units rather than estimated pixels.
 *
 * The default `rememberScrollbarAdapter` works in pixels: with variable row heights it only knows the
 * average of the currently visible rows, so dragging the thumb makes it iteratively scroll-measure-
 * re-estimate to home in on a pixel offset — composing several viewports per drag frame. Mapping the
 * thumb straight to an item index instead means one `scrollToItem` per drag event (~one viewport per
 * frame). The trade-off: within a single very tall row the thumb advances by one item-step rather
 * than tracking pixels, which is unnoticeable for a log list of mostly single-line rows.
 */
@OptIn(ExperimentalFoundationApi::class)
private class ItemBasedScrollbarAdapter(private val state: LazyListState) : ScrollbarAdapter {

    override val contentSize: Double
        get() = state.layoutInfo.totalItemsCount.toDouble()

    override val scrollOffset: Double
        get() {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return 0.0
            val hiddenTop = if (first.size <= 0) 0.0
            else ((info.viewportStartOffset - first.offset).toDouble() / first.size).coerceIn(0.0, 1.0)
            return state.firstVisibleItemIndex + hiddenTop
        }

    override val viewportSize: Double
        get() {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return 0.0
            val first = items.first()
            val last = items.last()
            val hiddenTop = if (first.size <= 0) 0.0
            else ((info.viewportStartOffset - first.offset).toDouble() / first.size).coerceIn(0.0, 1.0)
            val hiddenBottom = if (last.size <= 0) 0.0
            else ((last.offset + last.size - info.viewportEndOffset).toDouble() / last.size).coerceIn(0.0, 1.0)
            return (items.size - hiddenTop - hiddenBottom).coerceAtLeast(0.0)
        }

    // Coalesce drag events: the scrollbar fires scrollTo on every pointer move, often several per
    // frame. Each scrollToItem composes a fresh viewport, so honouring every intermediate target
    // composes several viewports per drag frame (the scrollbar-drag jank). Instead, while one scroll
    // is in flight, newer calls just update the target and return; the in-flight loop then jumps
    // straight to the latest, skipping the intermediate positions nobody sees.
    private var latestTarget = Double.NaN
    private var scrolling = false

    override suspend fun scrollTo(scrollOffset: Double) {
        latestTarget = scrollOffset
        if (scrolling) return
        scrolling = true
        try {
            var done = Double.NaN
            while (latestTarget != done) {
                done = latestTarget
                val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
                val target = done.coerceIn(0.0, maxOffset)
                val index = target.toInt()
                val fraction = target - index
                val items = state.layoutInfo.visibleItemsInfo
                val estItemPx = if (items.isEmpty()) 0 else items.sumOf { it.size } / items.size
                state.scrollToItem(index, (fraction * estItemPx).toInt())
            }
        } finally {
            scrolling = false
        }
    }
}

/** Remembered [ItemBasedScrollbarAdapter] for [state]; see the class doc for why it beats the default. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberItemBasedScrollbarAdapter(state: LazyListState): ScrollbarAdapter =
    remember(state) { ItemBasedScrollbarAdapter(state) }

/**
 * IntelliJ vertical scrollbar — Jewel's native component, so the look and behaviour match the IDE:
 * an always-visible track that tints on hover and click-on-track jumps the thumb to the clicked spot
 * ([AlwaysVisible] + JumpToSpot, the Windows/Linux defaults). Jewel's Linux default thumb is square
 * with no inset, so we round it ([CornerSize] 100) and inset it with track padding so the rounded
 * pill is visible; the track is kept slim (8dp).
 *
 * Note: Jewel's scrollbar drives a [LazyListState] directly and builds its own pixel-based adapter,
 * so this does not use [rememberItemBasedScrollbarAdapter].
 */
@Composable
fun IntelliJVerticalScrollbar(
    state: LazyListState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = remember(isDark) {
        val base = if (isDark) ScrollbarStyle.windowsAndLinuxDark() else ScrollbarStyle.windowsAndLinuxLight()
        ScrollbarStyle(
            colors = base.colors,
            metrics = ScrollbarMetrics(thumbCornerSize = CornerSize(100), minThumbLength = 24.dp),
            trackClickBehavior = base.trackClickBehavior,
            scrollbarVisibility = AlwaysVisible.windowsAndLinux(
                trackThickness = 9.dp,
                trackPadding = PaddingValues(0.dp),
            ),
        )
    }
    VerticalScrollbar(scrollState = state, modifier = modifier, style = style)
}

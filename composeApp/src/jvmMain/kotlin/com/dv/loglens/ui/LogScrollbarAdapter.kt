package com.dv.loglens.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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

    override suspend fun scrollTo(scrollOffset: Double) {
        val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maxOffset)
        val index = target.toInt()
        val fraction = target - index
        val items = state.layoutInfo.visibleItemsInfo
        val estItemPx = if (items.isEmpty()) 0 else items.sumOf { it.size } / items.size
        state.scrollToItem(index, (fraction * estItemPx).toInt())
    }
}

/** Remembered [ItemBasedScrollbarAdapter] for [state]; see the class doc for why it beats the default. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberItemBasedScrollbarAdapter(state: LazyListState): ScrollbarAdapter =
    remember(state) { ItemBasedScrollbarAdapter(state) }

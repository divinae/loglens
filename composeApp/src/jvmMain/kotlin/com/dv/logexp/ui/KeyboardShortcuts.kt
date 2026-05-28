package com.dv.logexp.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.dv.logexp.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.logKeyboardShortcuts(
    filteredLogs: List<LogEntry>,
    selectedIds: Set<Int>,
    listState: LazyListState,
    scope: CoroutineScope,
    onToggleBookmark: (Int) -> Unit,
    findNextBookmark: (Int) -> Int?,
    findPreviousBookmark: (Int) -> Int?,
    onSelect: (Int) -> Unit,
    onDisableAutoFollow: () -> Unit,
    onFocusSearch: () -> Unit,
): Modifier = this.onKeyEvent { event ->
    when (event.type) {
        KeyEventType.KeyDown -> when {
            event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed) -> {
                onFocusSearch()
                true
            }
            event.key == Key.DirectionDown -> {
                moveSelection(filteredLogs, selectedIds, listState, scope, onSelect, onDisableAutoFollow, delta = 1)
                true
            }
            event.key == Key.DirectionUp -> {
                moveSelection(filteredLogs, selectedIds, listState, scope, onSelect, onDisableAutoFollow, delta = -1)
                true
            }
            else -> false
        }

        KeyEventType.KeyUp -> when (event.key) {
            Key.B -> {
                if (selectedIds.isNotEmpty()) {
                    selectedIds.forEach(onToggleBookmark)
                    true
                } else false
            }

            Key.F3 -> {
                val currentLogId = selectedIds.lastOrNull()
                    ?: filteredLogs.getOrNull(listState.firstVisibleItemIndex)?.id
                    ?: -1

                val targetId = if (event.isShiftPressed) findPreviousBookmark(currentLogId)
                else findNextBookmark(currentLogId)

                targetId?.let { id ->
                    val targetIndex = filteredLogs.indexOfFirst { it.id == id }
                    if (targetIndex != -1) {
                        scope.launch { listState.animateScrollToItem(targetIndex) }
                        onSelect(id)
                    }
                }
                true
            }

            else -> false
        }

        else -> false
    }
}

private fun moveSelection(
    filteredLogs: List<LogEntry>,
    selectedIds: Set<Int>,
    listState: LazyListState,
    scope: CoroutineScope,
    onSelect: (Int) -> Unit,
    onDisableAutoFollow: () -> Unit,
    delta: Int,
) {
    val current = selectedIds.firstOrNull()
    val currentIndex = filteredLogs.indexOfFirst { it.id == current }
    val nextIndex = (currentIndex + delta).coerceIn(0, filteredLogs.size - 1)
    if (nextIndex == currentIndex || nextIndex < 0) return
    onSelect(filteredLogs[nextIndex].id)
    onDisableAutoFollow()
    scope.launch { listState.animateScrollToItem(nextIndex) }
}

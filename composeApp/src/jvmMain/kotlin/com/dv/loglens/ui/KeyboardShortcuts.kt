package com.dv.loglens.ui

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
import com.dv.loglens.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One keyboard shortcut: the key combo to show, and what it does. Single source of truth for the help dialog. */
data class ShortcutSpec(val keys: String, val description: String)

/** All shortcuts the app handles, shown verbatim in the shortcuts help dialog. Keep in sync with the handler below. */
val SHORTCUTS: List<ShortcutSpec> = listOf(
    ShortcutSpec("Ctrl/Cmd + F", "Open find bar / focus search"),
    ShortcutSpec("Ctrl/Cmd + C", "Copy selected lines"),
    ShortcutSpec("Ctrl/Cmd + R", "Clear logs"),
    ShortcutSpec("Esc", "Close find bar"),
    ShortcutSpec("↑ / ↓", "Move selection up / down"),
    ShortcutSpec("B", "Toggle bookmark on selection"),
    ShortcutSpec("F3 / Shift + F3", "Next / previous match (find) or bookmark"),
)

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
    findActive: Boolean,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onCloseFind: () -> Unit,
    onCopySelected: () -> Unit,
    onClearLogs: () -> Unit,
    textInputFocused: Boolean = false,
): Modifier = this.onKeyEvent { event ->
    when (event.type) {
        KeyEventType.KeyDown -> when {
            event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed) -> {
                onFocusSearch()
                true
            }
            event.key == Key.C && (event.isCtrlPressed || event.isMetaPressed) -> {
                if (selectedIds.isNotEmpty()) {
                    onCopySelected()
                    true
                } else false
            }
            event.key == Key.R && (event.isCtrlPressed || event.isMetaPressed) -> {
                onClearLogs()
                true
            }
            event.key == Key.Escape && findActive -> {
                onCloseFind()
                true
            }
            // Plain keys leak out of focused text fields (the field consumes the typed character's
            // KeyDown but the arrows/KeyUp still bubble), so they only act when no field is focused.
            event.key == Key.DirectionDown && !textInputFocused -> {
                moveSelection(filteredLogs, selectedIds, listState, scope, onSelect, onDisableAutoFollow, delta = 1)
                true
            }
            event.key == Key.DirectionUp && !textInputFocused -> {
                moveSelection(filteredLogs, selectedIds, listState, scope, onSelect, onDisableAutoFollow, delta = -1)
                true
            }
            else -> false
        }

        KeyEventType.KeyUp -> when (event.key) {
            Key.B -> {
                // Typing "b" into a filter/find field must not toggle bookmarks: the field eats the
                // KeyDown but this KeyUp still reaches us.
                if (!textInputFocused && selectedIds.isNotEmpty()) {
                    selectedIds.forEach(onToggleBookmark)
                    true
                } else false
            }

            Key.F3 -> {
                if (findActive) {
                    if (event.isShiftPressed) onFindPrev() else onFindNext()
                    return@onKeyEvent true
                }
                val currentLogId = selectedIds.lastOrNull()
                    ?: filteredLogs.getOrNull(listState.firstVisibleItemIndex)?.id
                    ?: -1

                val targetId = if (event.isShiftPressed) findPreviousBookmark(currentLogId)
                else findNextBookmark(currentLogId)

                targetId?.let { id ->
                    val targetIndex = filteredLogs.indexOfFirst { it.id == id }
                    if (targetIndex != -1) {
                        scrollToIfNeeded(listState, scope, targetIndex)
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
    scrollToIfNeeded(listState, scope, nextIndex)
}

private fun scrollToIfNeeded(
    listState: LazyListState,
    scope: CoroutineScope,
    targetIndex: Int,
) {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    val viewportStart = info.viewportStartOffset
    val viewportEnd = info.viewportEndOffset
    val fullyVisible = visible.any { v ->
        v.index == targetIndex && v.offset >= viewportStart && v.offset + v.size <= viewportEnd
    }
    if (fullyVisible) return
    val rowSize = visible.firstOrNull()?.size ?: 20
    val centerOffset = -((viewportEnd - viewportStart - rowSize) / 2)
    scope.launch { listState.animateScrollToItem(targetIndex, centerOffset) }
}

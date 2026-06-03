package com.dv.loglens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun QuickFilterBar(
    initialQuery: String,
    error: String?,
    history: List<String>,
    onSearchChange: (String) -> Unit,
    onCommit: () -> Unit,
    onClearHistory: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier.padding(top = 8.dp).padding(horizontal = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Seed from the view-model query so re-showing a hidden bar keeps the active filter.
        val searchState = rememberTextFieldState(initialQuery)
        // -1 = currently editing (not browsing history); 0 = most recent, growing = older.
        var historyIndex by remember { mutableStateOf(-1) }
        // Text we last injected from history/clear, so genuine user edits can reset the browse position.
        var lastInjected by remember { mutableStateOf<String?>(null) }
        var wasFocused by remember { mutableStateOf(false) }

        LaunchedEffect(searchState.text) {
            val text = searchState.text.toString()
            onSearchChange(text)
            if (text != lastInjected) historyIndex = -1
        }

        fun inject(text: String) {
            lastInjected = text
            searchState.setTextAndPlaceCursorAtEnd(text)
        }

        if (history.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconActionButton(
                    key = AllIconsKeys.Vcs.History,
                    contentDescription = "Filter history",
                    onClick = { expanded = true },
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)),
                )
                if (expanded) {
                    PopupMenu(
                        onDismissRequest = {
                            expanded = false
                            true
                        },
                        horizontalAlignment = Alignment.Start,
                    ) {
                        history.forEach { entry ->
                            selectableItem(selected = false, onClick = { expanded = false; inject(entry) }) {
                                Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        separator()
                        selectableItem(selected = false, onClick = { expanded = false; onClearHistory() }) {
                            Text("Clear history")
                        }
                    }
                }
            }
        }

        TextField(
            state = searchState,
            placeholder = { Text("Filter: text, or tag:Foo level>=W package:com.foo -msg:spam") },
            outline = if (error != null) Outline.Error else Outline.None,
            trailingIcon = if (searchState.text.isNotEmpty()) {
                {
                    IconActionButton(
                        onClick = {
                            inject("")
                            historyIndex = -1
                        },
                        key = AllIconsKeys.Actions.Close,
                        contentDescription = "Clear filter",
                    )
                }
            } else {
                null
            },
            modifier = Modifier.weight(1f)
                .onFocusChanged { focus ->
                    // Record the finished query when focus leaves the field — no Enter needed.
                    if (wasFocused && !focus.isFocused) onCommit()
                    wasFocused = focus.isFocused
                    onFocusChanged(focus.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onCommit()
                            historyIndex = -1
                            true
                        }
                        // Up = older entries.
                        Key.DirectionUp -> {
                            if (historyIndex < history.lastIndex) {
                                historyIndex += 1
                                inject(history[historyIndex])
                                true
                            } else false
                        }
                        // Down = back toward the live (empty) editing state.
                        Key.DirectionDown -> when {
                            historyIndex > 0 -> {
                                historyIndex -= 1
                                inject(history[historyIndex])
                                true
                            }
                            historyIndex == 0 -> {
                                inject("")
                                historyIndex = -1
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                },
        )
    }
}

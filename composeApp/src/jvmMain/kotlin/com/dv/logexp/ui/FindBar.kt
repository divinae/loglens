package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIdx: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState(query)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collectLatest { onQueryChange(it) }
    }

    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground, shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            state = state,
            placeholder = { Text("Find in logs...") },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Escape -> { onClose(); true }
                        Key.Enter, Key.NumPadEnter -> {
                            if (e.isShiftPressed) onPrev() else onNext()
                            true
                        }
                        else -> false
                    }
                },
        )
        Text(
            text = if (matchCount > 0) "${currentIdx + 1}/$matchCount" else "0/0",
            modifier = Modifier.widthIn(min = 64.dp),
        )
        OutlinedButton(onClick = onPrev, enabled = matchCount > 0) { Text("↑") }
        OutlinedButton(onClick = onNext, enabled = matchCount > 0) { Text("↓") }
        OutlinedButton(onClick = onClose) { Text("✕") }
    }
}

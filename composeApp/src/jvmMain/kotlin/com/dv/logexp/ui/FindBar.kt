package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.dv.logexp.ui.theme.islandsPopup
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

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
    onCopySelected: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState(query)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collectLatest { onQueryChange(it) }
    }

    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(islandsPopup(JewelTheme.isDark), shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            state = state,
            placeholder = { Text("Search...") },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (e.key == Key.C && (e.isCtrlPressed || e.isMetaPressed)) {
                        onCopySelected()
                        return@onPreviewKeyEvent true
                    }
                    when (e.key) {
                        Key.Escape -> {
                            onClose(); true
                        }

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
        IconActionButton(
            onClick = onPrev,
            enabled = matchCount > 0,
            key = AllIconsKeys.Actions.PreviousOccurence,
            contentDescription = null,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)),
        )
        IconActionButton(
            onClick = onNext,
            enabled = matchCount > 0,
            key = AllIconsKeys.Actions.NextOccurence,
            contentDescription = null,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)),
        )
        IconActionButton(
            onClick = onClose,
            key = AllIconsKeys.Actions.Close,
            contentDescription = "Close find bar",
            iconModifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)),
        )
    }
}

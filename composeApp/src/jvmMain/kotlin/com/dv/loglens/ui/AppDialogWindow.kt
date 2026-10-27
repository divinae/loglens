package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import com.dv.loglens.ui.theme.IslandsDark
import com.dv.loglens.ui.theme.IslandsLight
import com.dv.loglens.ui.theme.islandsPanel
import com.dv.loglens.util.Wayland
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

// Gap kept between an in-window dialog and the main window's edges.
private val DIALOG_WINDOW_MARGIN = 32.dp

/**
 * A [DialogWindow], except under native Wayland ([Wayland.active]): there a separate Compose window
 * can't draw (see [Wayland]), so the dialog renders in-window over the main content instead, with
 * its own title row. Escape closes it like the window's close button; clicking outside doesn't (a
 * stray click must not discard a dialog's unsaved edits). It's capped to the main window's size,
 * since unlike a separate window it can't extend past it or be resized.
 */
@Composable
fun AppDialogWindow(
    onCloseRequest: () -> Unit,
    state: DialogState,
    title: String,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    if (!Wayland.active) {
        DialogWindow(onCloseRequest = onCloseRequest, state = state, title = title, onKeyEvent = onKeyEvent) {
            content()
        }
        return
    }
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(8.dp)
    val windowSize = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) }
    }
    Dialog(
        onDismissRequest = onCloseRequest,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .size(
                    width = state.size.width.coerceAtMost(windowSize.width - DIALOG_WINDOW_MARGIN),
                    height = state.size.height.coerceAtMost(windowSize.height - DIALOG_WINDOW_MARGIN),
                )
                .clip(shape)
                .border(1.dp, if (isDark) IslandsDark.borderNormal else IslandsLight.borderNormal, shape)
                .background(islandsPanel(isDark))
                .onKeyEvent(onKeyEvent),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

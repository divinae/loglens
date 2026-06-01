package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.logexp.model.LogBuffer
import com.dv.logexp.ui.theme.islandsPanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text

@Composable
fun BuffersDialog(
    logBuffers: Set<LogBuffer>,
    onToggleLogBuffer: (LogBuffer) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDialogState(size = DpSize(320.dp, 320.dp))
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Log Buffers",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(islandsPanel(JewelTheme.isDark)).padding(16.dp)) {
            Text(
                "adb logcat buffers",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LogBuffer.entries.forEach { buffer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = buffer in logBuffers,
                        onCheckedChange = { onToggleLogBuffer(buffer) },
                    )
                    Text(buffer.displayName, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

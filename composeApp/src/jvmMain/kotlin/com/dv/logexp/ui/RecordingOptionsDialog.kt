package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dv.logexp.ui.theme.islandsPanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun RecordingOptionsDialog(
    showTaps: Boolean,
    onSetShowTaps: (Boolean) -> Unit,
    bitrateMbps: Int,
    onSetBitrate: (Int) -> Unit,
    size: String,
    onSetSize: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDialogState(size = DpSize(360.dp, 300.dp))
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Recording options",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(islandsPanel(JewelTheme.isDark)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Screen recording", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            CheckboxRow(
                text = "Show taps",
                checked = showTaps,
                onCheckedChange = onSetShowTaps,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bit rate (Mbps)", modifier = Modifier.padding(end = 8.dp))
                val bitrateState = rememberTextFieldState(if (bitrateMbps > 0) bitrateMbps.toString() else "")
                LaunchedEffect(bitrateState.text) {
                    val parsed = bitrateState.text.toString().toIntOrNull() ?: 0
                    if (parsed != bitrateMbps) onSetBitrate(parsed)
                }
                TextField(
                    state = bitrateState,
                    placeholder = { Text("default") },
                    modifier = Modifier.width(120.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resolution", modifier = Modifier.padding(end = 8.dp))
                val sizeState = rememberTextFieldState(size)
                LaunchedEffect(sizeState.text) {
                    val text = sizeState.text.toString()
                    if (text != size) onSetSize(text)
                }
                TextField(
                    state = sizeState,
                    placeholder = { Text("native (e.g. 1280x720)") },
                    modifier = Modifier.width(180.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                DefaultButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

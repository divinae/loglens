package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SaveOptionsDialog(
    onSave: (filtered: Boolean, asLogcat: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDialogState(size = DpSize(360.dp, 220.dp))
    var filtered by remember { mutableStateOf(true) }
    var asLogcat by remember { mutableStateOf(false) }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Save logs to file",
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
            Text("Save logs to file", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            CheckboxRow(
                text = "Only filtered logs",
                checked = filtered,
                onCheckedChange = { filtered = it },
            )
            CheckboxRow(
                text = "Android Studio .logcat format",
                checked = asLogcat,
                onCheckedChange = { asLogcat = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                DefaultButton(
                    onClick = {
                        onSave(filtered, asLogcat)
                        onDismiss()
                    },
                ) { Text("Save") }
            }
        }
    }
}

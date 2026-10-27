package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.dv.loglens.ui.theme.islandsPanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    val state = rememberDialogState(size = DpSize(440.dp, 400.dp))
    AppDialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Keyboard Shortcuts",
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(SHORTCUTS) { shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = shortcut.keys,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.width(150.dp),
                        )
                        Text(
                            text = shortcut.description,
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

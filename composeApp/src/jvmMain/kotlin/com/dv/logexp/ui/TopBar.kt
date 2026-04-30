package com.dv.logexp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.ThemeMode
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TopBar(
    isCapturing: Boolean,
    selectedCount: Int,
    autoFollow: Boolean,
    visibleColumns: Set<LogColumn>,
    themeMode: ThemeMode,
    onStart: () -> Unit,
    onClear: () -> Unit,
    onCopySelected: () -> Unit,
    onToggleAutoFollow: () -> Unit,
    onToggleColumn: (LogColumn) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DefaultButton(onClick = onStart, enabled = !isCapturing) {
            Text(if (isCapturing) "Capturing..." else "Start")
        }

        DefaultButton(onClick = onClear) { Text("Clear") }

        if (selectedCount > 0) {
            OutlinedButton(onClick = onCopySelected) {
                Text("Copy Selected ($selectedCount)")
            }
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoFollow, onCheckedChange = { onToggleAutoFollow() })
            Text("Auto-Follow", modifier = Modifier.padding(start = 4.dp))
        }

        SettingsMenu(
            visibleColumns = visibleColumns,
            onToggleColumn = onToggleColumn,
            themeMode = themeMode,
            onToggleTheme = onToggleTheme,
        )
    }
}

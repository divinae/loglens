package com.dv.logexp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.ThemeMode
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun SettingsMenu(
    visibleColumns: Set<LogColumn>,
    onToggleColumn: (LogColumn) -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconActionButton(
            key = AllIconsKeys.General.Gear,
            contentDescription = null,
            onClick = { expanded = true }
        )

        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    expanded = false
                    true
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                selectableItem(
                    selected = false,
                    onClick = onToggleTheme,
                ) {
                    Text("Theme: ${themeMode.name}")
                }

                passiveItem {
                    Divider(orientation = Orientation.Horizontal, thickness = 8.dp)
                }

                LogColumn.entries.forEach { column ->
                    selectableItem(
                        selected = column in visibleColumns,
                        onClick = { onToggleColumn(column) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = column in visibleColumns,
                                onCheckedChange = { onToggleColumn(column) },
                            )
                            Text(column.displayName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

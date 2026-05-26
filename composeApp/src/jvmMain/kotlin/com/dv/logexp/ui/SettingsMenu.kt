package com.dv.logexp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.MAX_FONT_SIZE
import com.dv.logexp.model.MIN_FONT_SIZE
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.service.SystemFonts
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val LOGICAL_FONTS = listOf("Monospaced", "SansSerif", "Serif", "Dialog")

@Composable
fun SettingsMenu(
    visibleColumns: Set<LogColumn>,
    onToggleColumn: (LogColumn) -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    fontSize: Float,
    onSetFontSize: (Float) -> Unit,
    fontFamily: String,
    onSetFontFamily: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fontOptions = remember {
        (LOGICAL_FONTS + SystemFonts.list()).distinct()
    }

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

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Font size", modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { onSetFontSize(fontSize - 1f) },
                            enabled = fontSize > MIN_FONT_SIZE,
                        ) { Text("-") }
                        Text(
                            "${fontSize.toInt()}",
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        OutlinedButton(
                            onClick = { onSetFontSize(fontSize + 1f) },
                            enabled = fontSize < MAX_FONT_SIZE,
                        ) { Text("+") }
                    }
                }

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Font", modifier = Modifier.padding(end = 8.dp))
                        val selectedIdx = fontOptions.indexOf(fontFamily).let { if (it < 0) 0 else it }
                        ListComboBox(
                            items = fontOptions,
                            selectedIndex = selectedIdx,
                            onSelectedItemChange = { idx -> onSetFontFamily(fontOptions[idx]) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
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

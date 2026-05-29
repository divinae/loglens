package com.dv.logexp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun MainMenu(onSaveLogs: () -> Unit, onOpenFilters: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        IconActionButton(
            iconModifier = Modifier.size(18.dp),
            key = AllIconsKeys.General.WindowsMenu_20x20,
            contentDescription = "Menu",
            onClick = { expanded = true },
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)),
        )
        if (expanded) {
            PopupMenu(
                onDismissRequest = { expanded = false; true },
                horizontalAlignment = Alignment.Start,
            ) {
                selectableItem(
                    selected = false,
                    iconKey = AllIconsKeys.General.Filter,
                    onClick = {
                        expanded = false
                        onOpenFilters()
                    },
                ) {
                    Text("Filters...")
                }
                selectableItem(
                    selected = false,
                    iconKey = AllIconsKeys.Actions.MenuSaveall,
                    onClick = {
                        expanded = false
                        onSaveLogs()
                    },
                ) {
                    Text("Save logs to file...")
                }
            }
        }
    }
}

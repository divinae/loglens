package com.dv.logexp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun MainMenu(onSaveLogs: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(
            key = AllIconsKeys.General.Menu,
            contentDescription = "Menu",
            modifier = Modifier
                .size(20.dp)
                .clickable { expanded = true },
        )
        if (expanded) {
            PopupMenu(
                onDismissRequest = { expanded = false; true },
                horizontalAlignment = Alignment.Start,
            ) {
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

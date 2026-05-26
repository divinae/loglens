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
import com.dv.logexp.model.Device
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceMenu(
    devices: List<Device>,
    selectedSerial: String?,
    onSelect: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.serial == selectedSerial }
    val label = selected?.displayName ?: when {
        devices.isEmpty() -> "No devices"
        else -> "Select device"
    }

    Box {
        OutlinedButton(onClick = {
            onRefresh()
            expanded = true
        }) {
            Text(label)
        }
        if (expanded) {
            PopupMenu(
                onDismissRequest = { expanded = false; true },
                horizontalAlignment = Alignment.Start,
            ) {
                if (devices.isEmpty()) {
                    passiveItem {
                        Text(
                            "No connected devices",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    devices.forEach { device ->
                        selectableItem(
                            selected = device.serial == selectedSerial,
                            onClick = {
                                onSelect(device.serial)
                                expanded = false
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(device.displayName, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    device.serial,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.dv.logexp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.Device
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun SourceMenu(
    devices: List<Device>,
    selectedSerial: String?,
    loadedFile: String?,
    onSelect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.serial == selectedSerial }
    val label = when {
        loadedFile != null -> loadedFile
        selected != null -> selected.displayName
        devices.isEmpty() -> "No devices"
        else -> "Select source"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = JewelTheme.isDark
    val hoverBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val iconTint = JewelTheme.globalColors.text.normal

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHovered || expanded) hoverBg else Color.Transparent)
                .hoverable(interactionSource)
                .clickable {
                    onRefresh()
                    expanded = true
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                loadedFile != null -> Icon(
                    key = AllIconsKeys.FileTypes.Text,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconTint,
                )
                selected != null -> DeviceIcon(connected = true, tint = iconTint)
                else -> Icon(
                    key = AllIconsKeys.General.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconTint,
                )
            }
            Text(label)
            Icon(
                key = AllIconsKeys.General.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconTint.copy(alpha = 0.7f),
            )
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
                            selected = loadedFile == null && device.serial == selectedSerial,
                            onClick = {
                                onSelect(device.serial)
                                expanded = false
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                    DeviceIcon(connected = device.isOnline, tint = iconTint)
                                }
                                Text(device.displayName)
                                Text(device.serial, color = iconTint.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                separator()

                selectableItem(
                    selected = loadedFile != null,
                    onClick = {
                        onOpenFile()
                        expanded = false
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                key = AllIconsKeys.Actions.MenuOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = iconTint,
                            )
                        }
                        Text("Open file...")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIcon(connected: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(width = 9.dp, height = 14.dp)) {
        val stroke = 1.dp.toPx()
        val r = 1.5.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(stroke),
        )
        drawCircle(
            color = tint,
            radius = 0.6.dp.toPx(),
            center = Offset(size.width / 2, size.height - 2.dp.toPx()),
        )
        if (connected) {
            val dotR = 2.2.dp.toPx()
            drawCircle(
                color = Color(0xFF3FB950),
                radius = dotR,
                center = Offset(dotR - 0.5.dp.toPx(), dotR - 0.5.dp.toPx()),
            )
        }
    }
}

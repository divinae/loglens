package com.dv.logexp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.dv.logexp.model.Device
import com.dv.logexp.model.ThemeMode
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun TopBar(
    autoFollow: Boolean,
    themeMode: ThemeMode,
    devices: List<Device>,
    selectedDevice: String?,
    onSelectDevice: (String?) -> Unit,
    onRefreshDevices: () -> Unit,
    onClear: () -> Unit,
    isCapturing: Boolean,
    onToggleCapture: () -> Unit,
    onSaveLogs: () -> Unit,
    onOpenFilters: () -> Unit,
    onToggleAutoFollow: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    fontSize: Float,
    onSetFontSize: (Float) -> Unit,
    fontFamily: String,
    onSetFontFamily: (String) -> Unit,
    fontWeight: String,
    onSetFontWeight: (String) -> Unit,
    softWrap: Boolean,
    onSetSoftWrap: (Boolean) -> Unit,
    onOpenFind: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainMenu(onSaveLogs = onSaveLogs, onOpenFilters = onOpenFilters)

        DeviceMenu(
            devices = devices,
            selectedSerial = selectedDevice,
            onSelect = onSelectDevice,
            onRefresh = onRefreshDevices,
        )

        IconActionButton(
            key = if (isCapturing) AllIconsKeys.Actions.Pause else AllIconsKeys.Toolwindows.ToolWindowRun,
            contentDescription = if (isCapturing) "Pause log capture" else "Resume log capture",
            onClick = onToggleCapture,
            focusable = false,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)),
        )

        IconActionButton(
            key = AllIconsKeys.General.Delete,
            contentDescription = "Clear logs",
            onClick = onClear,
            focusable = false,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)),
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoFollow, onCheckedChange = { onToggleAutoFollow() })
            Text("Auto-Follow", modifier = Modifier.padding(start = 4.dp))
        }

        IconActionButton(
            key = AllIconsKeys.Actions.Find,
            contentDescription = "Find",
            onClick = onOpenFind,
            focusable = false,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)),
        )

        SettingsMenu(
            themeMode = themeMode,
            onSetTheme = onSetTheme,
            fontSize = fontSize,
            onSetFontSize = onSetFontSize,
            fontFamily = fontFamily,
            onSetFontFamily = onSetFontFamily,
            fontWeight = fontWeight,
            onSetFontWeight = onSetFontWeight,
            softWrap = softWrap,
            onSetSoftWrap = onSetSoftWrap,
        )
    }
}

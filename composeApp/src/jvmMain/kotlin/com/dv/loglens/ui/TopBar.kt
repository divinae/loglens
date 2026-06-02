package com.dv.loglens.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.loglens.model.Device
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.ui.theme.islandsPopup
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration.Companion.milliseconds

// Tooltips anchored below the trigger so they don't cover the button being hovered.
@OptIn(ExperimentalFoundationApi::class)
private val belowPlacement = TooltipPlacement.ComponentRect(
    anchor = Alignment.BottomCenter,
    alignment = Alignment.BottomCenter,
    offset = DpOffset(0.dp, 4.dp),
)

@OptIn(ExperimentalFoundationApi::class)
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
    onTakeScreenshot: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onSaveLogs: (filtered: Boolean, asLogcat: Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    loadedFile: String?,
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
    alwaysShowBookmarks: Boolean,
    onSetAlwaysShowBookmarks: (Boolean) -> Unit,
    onOpenFind: () -> Unit,
    findActive: Boolean,
    logCount: Int,
    totalLogCount: Int,
    logBuffers: Set<LogBuffer>,
    onToggleLogBuffer: (LogBuffer) -> Unit,
    screenshotDir: String,
    onSetScreenshotDir: (String) -> Unit,
    openScreenshotAfterCapture: Boolean,
    onSetOpenScreenshotAfterCapture: (Boolean) -> Unit,
    adbPath: String,
    onSetAdbPath: (String) -> Unit,
    recordingDir: String,
    onSetRecordingDir: (String) -> Unit,
    recordingShowTaps: Boolean,
    onSetRecordingShowTaps: (Boolean) -> Unit,
    recordingBitrateMbps: Int,
    onSetRecordingBitrateMbps: (Int) -> Unit,
    recordingSize: String,
    onSetRecordingSize: (String) -> Unit,
    onShowShortcuts: () -> Unit,
) {
    val activeAccent = Color(0xFF3592FF)
    val inactiveTint = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
    fun activeBorder(active: Boolean): Modifier =
        if (active) Modifier.border(1.dp, activeAccent, RoundedCornerShape(4.dp)) else Modifier

    fun iconTint(active: Boolean): ColorFilter =
        ColorFilter.tint(if (active) activeAccent else inactiveTint)

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val popupBg = islandsPopup(isDark)
    val borderColor = JewelTheme.globalColors.borders.normal
    val textColor = JewelTheme.globalColors.text.normal
    val tooltipStyle = remember(popupBg, borderColor, textColor) {
        TooltipStyle(
            colors = TooltipColors(
                background = popupBg,
                content = textColor,
                border = borderColor,
                shadow = Color(0x33000000),
            ),
            metrics = TooltipMetrics.defaults(
                showDelay = 150.milliseconds,
                regularDisappearDelay = 10_000.milliseconds,
                placement = belowPlacement,
            ),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tooltip(tooltip = { Text("Menu") }, style = tooltipStyle) {
            MainMenu(onSaveLogs = onSaveLogs)
        }

        Tooltip(tooltip = { Text("Select source") }, style = tooltipStyle) {
            SourceMenu(
                devices = devices,
                selectedSerial = selectedDevice,
                loadedFile = loadedFile,
                onSelect = onSelectDevice,
                onRefresh = onRefreshDevices,
                onOpenFile = onOpenLogs,
            )
        }

        // Live controls only make sense with a device source selected, not for a file or no source.
        if (loadedFile == null && selectedDevice != null) {
            IconActionButton(
                key = if (isCapturing) AllIconsKeys.Actions.Pause else AllIconsKeys.Toolwindows.ToolWindowRun,
                contentDescription = if (isCapturing) "Pause log capture" else "Resume log capture",
                onClick = onToggleCapture,
                colorFilter = iconTint(false),
                tooltipStyle = tooltipStyle,
                tooltipPlacement = belowPlacement,
            ) {
                Text(if (isCapturing) "Pause capture" else "Resume capture")
            }

            IconActionButton(
                key = AllIconsKeys.General.Delete,
                contentDescription = "Clear logs",
                onClick = onClear,
                colorFilter = iconTint(false),
                tooltipStyle = tooltipStyle,
                tooltipPlacement = belowPlacement,
            ) {
                Text("Clear logs")
            }
        }

        Spacer(Modifier.weight(1f))

        Box(modifier = Modifier.width(160.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (logCount < totalLogCount) "logs: $logCount/$totalLogCount" else "logs: $logCount",
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f),
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        // Device screenshot — only meaningful with a live device source.
        if (loadedFile == null && selectedDevice != null) {
            IconActionButton(
                key = AllIconsKeys.Actions.ViewAsImage,
                contentDescription = "Screenshot device",
                onClick = onTakeScreenshot,
                colorFilter = iconTint(false),
                tooltipStyle = tooltipStyle,
                tooltipPlacement = belowPlacement,
            ) {
                Text("Screenshot device")
            }

            ToggleableIconActionButton(
                key = if (isRecording) AllIconsKeys.Run.Stop else AllIconsKeys.Profiler.Rec,
                contentDescription = if (isRecording) "Stop recording" else "Record screen",
                value = isRecording,
                onValueChange = { onToggleRecording() },
                modifier = activeBorder(isRecording),
                colorFilter = ColorFilter.tint(if (isRecording) Color(0xFFE5484D) else inactiveTint),
                tooltipStyle = tooltipStyle,
                tooltipPlacement = belowPlacement,
            ) {
                Text(if (isRecording) "Stop recording" else "Record screen")
            }
        }

        ToggleableIconActionButton(
            key = AllIconsKeys.RunConfigurations.Scroll_down,
            contentDescription = if (autoFollow) "Auto-follow on" else "Auto-follow off",
            value = autoFollow,
            onValueChange = { onToggleAutoFollow() },
            modifier = activeBorder(autoFollow),
            colorFilter = iconTint(autoFollow),
            tooltipStyle = tooltipStyle,
            tooltipPlacement = belowPlacement,
        ) {
            Text(if (autoFollow) "Auto-follow on" else "Auto-follow off")
        }

        IconActionButton(
            key = AllIconsKeys.Actions.Find,
            contentDescription = "Find",
            onClick = onOpenFind,
            modifier = activeBorder(findActive),
            colorFilter = iconTint(findActive),
            tooltipStyle = tooltipStyle,
            tooltipPlacement = belowPlacement,
        ) {
            Text("Find")
        }

        Tooltip(tooltip = { Text("Settings") }, style = tooltipStyle) {
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
                alwaysShowBookmarks = alwaysShowBookmarks,
                onSetAlwaysShowBookmarks = onSetAlwaysShowBookmarks,
                logBuffers = logBuffers,
                onToggleLogBuffer = onToggleLogBuffer,
                screenshotDir = screenshotDir,
                onSetScreenshotDir = onSetScreenshotDir,
                openScreenshotAfterCapture = openScreenshotAfterCapture,
                onSetOpenScreenshotAfterCapture = onSetOpenScreenshotAfterCapture,
                adbPath = adbPath,
                onSetAdbPath = onSetAdbPath,
                recordingDir = recordingDir,
                onSetRecordingDir = onSetRecordingDir,
                recordingShowTaps = recordingShowTaps,
                onSetRecordingShowTaps = onSetRecordingShowTaps,
                recordingBitrateMbps = recordingBitrateMbps,
                onSetRecordingBitrateMbps = onSetRecordingBitrateMbps,
                recordingSize = recordingSize,
                onSetRecordingSize = onSetRecordingSize,
                onShowShortcuts = onShowShortcuts,
            )
        }
    }
}

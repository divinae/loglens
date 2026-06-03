package com.dv.loglens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.dv.loglens.model.FontWeightPreset
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.MAX_FONT_SIZE
import com.dv.loglens.model.MIN_FONT_SIZE
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.service.EmbeddedFonts
import com.dv.loglens.service.SystemFonts
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val LOGICAL_FONTS = listOf("Monospaced", "SansSerif", "Serif", "Dialog")

@Composable
fun SettingsMenu(
    themeMode: ThemeMode,
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
    var expanded by remember { mutableStateOf(false) }
    var showBuffers by remember { mutableStateOf(false) }
    var showRecordingOptions by remember { mutableStateOf(false) }
    val fontOptions = remember {
        (EmbeddedFonts.names + LOGICAL_FONTS + SystemFonts.list()).distinct()
    }
    val weightOptions = remember { FontWeightPreset.entries.toList() }

    Box {
        IconActionButton(
            key = AllIconsKeys.General.Gear,
            contentDescription = null,
            onClick = { expanded = true },
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)),
        )

        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    expanded = false
                    true
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Theme", modifier = Modifier.padding(end = 8.dp))
                        val themeOptions = ThemeMode.entries.toList()
                        val selectedIdx = themeOptions.indexOf(themeMode).coerceAtLeast(0)
                        ListComboBox(
                            items = themeOptions.map { it.name },
                            selectedIndex = selectedIdx,
                            onSelectedItemChange = { idx -> onSetTheme(themeOptions[idx]) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                separator()

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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Weight", modifier = Modifier.padding(end = 8.dp))
                        val currentWeight = FontWeightPreset.fromName(fontWeight)
                        val selectedIdx = weightOptions.indexOf(currentWeight).coerceAtLeast(0)
                        ListComboBox(
                            items = weightOptions.map { it.displayName },
                            selectedIndex = selectedIdx,
                            onSelectedItemChange = { idx -> onSetFontWeight(weightOptions[idx].name) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                separator()

                passiveItem {
                    CheckboxRow(
                        text = "Soft wrap",
                        checked = softWrap,
                        onCheckedChange = onSetSoftWrap,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                passiveItem {
                    CheckboxRow(
                        text = "Always show bookmarks",
                        checked = alwaysShowBookmarks,
                        onCheckedChange = onSetAlwaysShowBookmarks,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                separator()

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("adb path", modifier = Modifier.padding(end = 8.dp))
                        val adbState = rememberTextFieldState(adbPath)
                        LaunchedEffect(adbState.text) {
                            val text = adbState.text.toString()
                            if (text != adbPath) onSetAdbPath(text)
                        }
                        TextField(
                            state = adbState,
                            placeholder = { Text("adb (from PATH)") },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Screenshot dir", modifier = Modifier.padding(end = 8.dp))
                        val dirState = rememberTextFieldState(screenshotDir)
                        LaunchedEffect(dirState.text) {
                            val text = dirState.text.toString()
                            if (text != screenshotDir) onSetScreenshotDir(text)
                        }
                        TextField(
                            state = dirState,
                            placeholder = { Text("~/Pictures/android") },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recording dir", modifier = Modifier.padding(end = 8.dp))
                        val recState = rememberTextFieldState(recordingDir)
                        LaunchedEffect(recState.text) {
                            val text = recState.text.toString()
                            if (text != recordingDir) onSetRecordingDir(text)
                        }
                        TextField(
                            state = recState,
                            placeholder = { Text("~/Videos/android") },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }

                passiveItem {
                    CheckboxRow(
                        text = "Open file after capture",
                        checked = openScreenshotAfterCapture,
                        onCheckedChange = onSetOpenScreenshotAfterCapture,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                separator()

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                showBuffers = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Log buffers...") }
                    }
                }

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                showRecordingOptions = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Recording options...") }
                    }
                }

                separator()

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                onShowShortcuts()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Keyboard shortcuts") }
                    }
                }

            }
        }
    }

    if (showBuffers) {
        BuffersDialog(
            logBuffers = logBuffers,
            onToggleLogBuffer = onToggleLogBuffer,
            onDismiss = { showBuffers = false },
        )
    }

    if (showRecordingOptions) {
        RecordingOptionsDialog(
            showTaps = recordingShowTaps,
            onSetShowTaps = onSetRecordingShowTaps,
            bitrateMbps = recordingBitrateMbps,
            onSetBitrate = onSetRecordingBitrateMbps,
            size = recordingSize,
            onSetSize = onSetRecordingSize,
            onDismiss = { showRecordingOptions = false },
        )
    }
}

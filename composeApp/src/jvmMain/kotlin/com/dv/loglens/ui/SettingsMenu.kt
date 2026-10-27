package com.dv.loglens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.dv.loglens.util.FilePicker
import com.dv.loglens.model.FontWeightPreset
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.MAX_FONT_SIZE
import com.dv.loglens.model.MIN_FONT_SIZE
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.service.EmbeddedFonts
import com.dv.loglens.service.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val LOGICAL_FONTS = listOf("Monospaced", "SansSerif", "Serif", "Dialog")

/**
 * A labelled path field with a Browse button. Edits (typed or picked) flow through [onSet]; [pick]
 * runs the (blocking) native dialog off the UI thread and writes the chosen path back into the field.
 */
@Composable
private fun PathSettingRow(
    label: String,
    value: String,
    placeholder: String,
    browseTitle: String,
    onSet: (String) -> Unit,
    pick: (String) -> File?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.weight(1f))
        val state = rememberTextFieldState(value)
        LaunchedEffect(state.text) {
            val text = state.text.toString()
            if (text != value) onSet(text)
        }
        TextField(
            state = state,
            placeholder = { Text(placeholder) },
            modifier = Modifier.width(180.dp),
        )
        val scope = rememberCoroutineScope()
        IconButton(
            onClick = {
                scope.launch {
                    val picked = withContext(Dispatchers.IO) { pick(browseTitle) }
                    picked?.let { state.setTextAndPlaceCursorAtEnd(it.absolutePath) }
                }
            },
            modifier = Modifier.padding(start = 4.dp).size(24.dp),
        ) {
            Icon(AllIconsKeys.General.OpenDisk, contentDescription = browseTitle)
        }
    }
}

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
    expandMultilineByDefault: Boolean,
    onSetExpandMultilineByDefault: (Boolean) -> Unit,
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
    scrcpyPath: String,
    onSetScrcpyPath: (String) -> Unit,
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
    val weightOptions = remember(fontFamily) { SystemFonts.availableWeights(fontFamily) }

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
                        Spacer(Modifier.weight(1f))
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
                        Spacer(Modifier.weight(1f))
                        // Searchable: the installed-font list is long. Each entry previews in its own font.
                        SearchablePicker(
                            options = fontOptions,
                            current = fontFamily,
                            onPick = onSetFontFamily,
                            buttonLabel = fontFamily,
                            searchPlaceholder = "Search fonts…",
                            emptyText = "No matching font",
                            modifier = Modifier.width(220.dp),
                            itemFontFamily = { SystemFonts.resolve(it) },
                        )
                    }
                }

                passiveItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Weight", modifier = Modifier.padding(end = 8.dp))
                        Spacer(Modifier.weight(1f))
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
                        text = "Expand multiline logs by default",
                        checked = expandMultilineByDefault,
                        onCheckedChange = onSetExpandMultilineByDefault,
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
                    PathSettingRow(
                        label = "adb path",
                        value = adbPath,
                        placeholder = "adb (from PATH)",
                        browseTitle = "Select adb executable",
                        onSet = onSetAdbPath,
                        pick = FilePicker::openExecutable,
                    )
                }

                passiveItem {
                    PathSettingRow(
                        label = "scrcpy path",
                        value = scrcpyPath,
                        placeholder = "scrcpy (from PATH)",
                        browseTitle = "Select scrcpy executable",
                        onSet = onSetScrcpyPath,
                        pick = FilePicker::openExecutable,
                    )
                }

                passiveItem {
                    PathSettingRow(
                        label = "Screenshot dir",
                        value = screenshotDir,
                        placeholder = "~/Pictures/android",
                        browseTitle = "Select screenshot directory",
                        onSet = onSetScreenshotDir,
                        pick = FilePicker::openDirectory,
                    )
                }

                passiveItem {
                    PathSettingRow(
                        label = "Recording dir",
                        value = recordingDir,
                        placeholder = "~/Videos/android",
                        browseTitle = "Select recording directory",
                        onSet = onSetRecordingDir,
                        pick = FilePicker::openDirectory,
                    )
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

package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.logexp.model.DEFAULT_FILTERS_LIST_WIDTH
import com.dv.logexp.model.FieldPattern
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FilterRule
import com.dv.logexp.ui.theme.IslandsDark
import com.dv.logexp.ui.theme.IslandsLight
import com.dv.logexp.ui.theme.islandsEditor
import com.dv.logexp.ui.theme.islandsPanel
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val LOG_LEVELS = listOf("V", "D", "I", "W", "E", "F")

private val PRESET_COLORS = listOf(
    0x33FFEB3BL, 0x33FF9800L, 0x33F44336L, 0x33E040FBL,
    0x337C4DFFL, 0x33448AFFL, 0x3300B0FFL, 0x331DE9B6L,
    0x3376FF03L, 0x3390A4AEL,
)

@Composable
fun FiltersDialog(
    filters: List<FilterRule>,
    onDismiss: () -> Unit,
    onSave: (List<FilterRule>) -> Unit,
    initialSelectedId: String? = null,
    addOnOpen: Boolean = false,
    listWidth: Float = DEFAULT_FILTERS_LIST_WIDTH,
    onListWidthChange: (Float) -> Unit = {},
) {
    val state = rememberDialogState(size = DpSize(900.dp, 600.dp))
    var draft by remember { mutableStateOf(filters) }
    var selectedId by remember { mutableStateOf(initialSelectedId ?: draft.firstOrNull()?.id) }
    val selected = draft.firstOrNull { it.id == selectedId }
    val dirty = draft != filters

    LaunchedEffect(Unit) {
        if (addOnOpen) {
            val rule = FilterRule()
            draft = draft + rule
            selectedId = rule.id
        }
    }

    fun update(rule: FilterRule) {
        draft = draft.map { if (it.id == rule.id) rule else it }
    }

    fun add() {
        val rule = FilterRule()
        draft = draft + rule
        selectedId = rule.id
    }

    fun remove(id: String) {
        draft = draft.filterNot { it.id == id }
        if (selectedId == id) selectedId = draft.firstOrNull()?.id
    }

    fun toggle(id: String) {
        draft = draft.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in draft.indices || to !in draft.indices) return
        draft = draft.toMutableList().apply { add(to, removeAt(from)) }
    }

    val islandShape = RoundedCornerShape(8.dp)
    var width by remember { mutableStateOf(listWidth.dp) }
    val density = LocalDensity.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = if (dirty) "Saved Filters *" else "Saved Filters",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(islandsPanel(JewelTheme.isDark))) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                FilterList(
                    filters = draft,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onAdd = ::add,
                    onToggle = ::toggle,
                    onMove = ::move,
                    onRemove = ::remove,
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .clip(islandShape)
                        .background(islandsEditor(JewelTheme.isDark)),
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(8.dp)
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { onListWidthChange(width.value) },
                            ) { change, dragAmount ->
                                change.consume()
                                val delta = with(density) { dragAmount.x.toDp() }
                                width = (width + delta).coerceIn(180.dp, 560.dp)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.3f)
                            .clip(RoundedCornerShape(1.dp))
                            .background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.6f))
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(islandShape)
                        .background(islandsEditor(JewelTheme.isDark)),
                ) {
                    if (selected != null) {
                        FilterEditor(
                            rule = selected,
                            onChange = ::update,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No filter selected. Click + to add one.", fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dirty) Text("Unsaved changes", fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                DefaultButton(onClick = { onSave(draft); onDismiss() }, enabled = dirty) { Text("Save") }
                OutlinedButton(onClick = { draft = filters; selectedId = draft.firstOrNull()?.id }, enabled = dirty) {
                    Text("Discard")
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun FilterList(
    filters: List<FilterRule>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Divider(Orientation.Horizontal)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(filters, key = { _, rule -> rule.id }) { index, rule ->
                val isSelected = rule.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) {
                                if (JewelTheme.isDark) IslandsDark.rowSelected else IslandsLight.rowSelected
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(rule.id) }
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rule.enabled, onCheckedChange = { onToggle(rule.id) })
                    val hasColor = rule.mode == FilterMode.HIGHLIGHT || rule.mode == FilterMode.SHOW_AND_HIGHLIGHT
                    if (hasColor) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(Color(rule.highlightColor.toInt()), CircleShape)
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    }
                    val label = when (rule.mode) {
                        FilterMode.SHOW -> "S"
                        FilterMode.HIDE -> "H"
                        FilterMode.HIGHLIGHT -> ""
                        FilterMode.SHOW_AND_HIGHLIGHT -> "S"
                    }
                    if (label.isNotEmpty()) Text(label, fontSize = 10.sp)
                    Text(rule.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    ReorderButton(AllIconsKeys.General.ArrowUp, enabled = index > 0) { onMove(index, index - 1) }
                    ReorderButton(AllIconsKeys.General.ArrowDown, enabled = index < filters.lastIndex) {
                        onMove(index, index + 1)
                    }
                    DeleteButton { onRemove(rule.id) }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(start= 14.dp)
                        .padding( vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        key = AllIconsKeys.General.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = JewelTheme.globalColors.text.info,
                    )
                    Text("Add filter", fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
                }
            }
        }
    }
}

@Composable
private fun ReorderButton(key: IconKey, enabled: Boolean, onClick: () -> Unit) {
    val gray = JewelTheme.globalColors.text.normal.copy(alpha = if (enabled) 0.8f else 0.18f)
    Box(
        modifier = Modifier
            .size(24.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(key = key, contentDescription = null, modifier = Modifier.size(14.dp), tint = gray)
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.General.Delete,
            contentDescription = "Delete filter",
            modifier = Modifier.size(14.dp),
            tint = Color(0xFFE05252),
        )
    }
}

@Composable
private fun FilterEditor(
    rule: FilterRule,
    onChange: (FilterRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val nameState = rememberTextFieldState(rule.name)
        LaunchedEffect(rule.id) { nameState.setTextAndPlaceCursorAtEnd(rule.name) }
        LaunchedEffect(nameState.text, rule.id) {
            val text = nameState.text.toString()
            if (text != rule.name) onChange(rule.copy(name = text))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Name", modifier = Modifier.width(80.dp))
            TextField(state = nameState, modifier = Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode", modifier = Modifier.width(80.dp))
            FilterMode.entries.forEach { mode ->
                val label = when (mode) {
                    FilterMode.SHOW -> "SHOW"
                    FilterMode.HIDE -> "HIDE"
                    FilterMode.HIGHLIGHT -> "HIGHLIGHT"
                    FilterMode.SHOW_AND_HIGHLIGHT -> "SHOW+HIGHLIGHT"
                }
                RadioButtonRow(
                    selected = rule.mode == mode,
                    onClick = { onChange(rule.copy(mode = mode)) },
                    text = label,
                )
            }
        }

        if (rule.mode == FilterMode.HIGHLIGHT || rule.mode == FilterMode.SHOW_AND_HIGHLIGHT) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color", modifier = Modifier.width(80.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PRESET_COLORS.forEach { c ->
                        val selected = c == rule.highlightColor
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(Color(c.toInt()), RoundedCornerShape(4.dp))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) JewelTheme.globalColors.text.info else Color.Gray,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { onChange(rule.copy(highlightColor = c)) }
                        )
                    }
                }
            }
        }

        Divider(Orientation.Horizontal)
        Text("Conditions (empty = any)", fontSize = 11.sp)

        FieldRow("Message", rule.message) { onChange(rule.copy(message = it)) }
        FieldRow("Tag", rule.tag) { onChange(rule.copy(tag = it)) }
        FieldRow("App", rule.app) { onChange(rule.copy(app = it)) }
        FieldRow("PID", rule.pid) { onChange(rule.copy(pid = it)) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Min level", modifier = Modifier.width(80.dp))
            // levels is stored as the suffix set { selected .. F } so membership matching == level >= selected.
            // Empty set = "Any" (no level constraint).
            val minIndex = LOG_LEVELS.indexOfFirst { it in rule.levels }
            Dropdown(
                menuContent = {
                    selectableItem(
                        selected = minIndex < 0,
                        onClick = { onChange(rule.copy(levels = emptySet())) },
                    ) {
                        Text("Any")
                    }
                    LOG_LEVELS.forEachIndexed { i, lvl ->
                        selectableItem(
                            selected = i == minIndex,
                            onClick = { onChange(rule.copy(levels = LOG_LEVELS.drop(i).toSet())) },
                        ) {
                            Text(levelLabel(lvl))
                        }
                    }
                },
            ) {
                Text(if (minIndex < 0) "Any" else levelLabel(LOG_LEVELS[minIndex]))
            }
        }
    }
}

private fun levelLabel(level: String): String = when (level) {
    "V" -> "Verbose"
    "D" -> "Debug"
    "I" -> "Info"
    "W" -> "Warn"
    "E" -> "Error"
    "F" -> "Fatal"
    else -> level
}

@Composable
private fun FieldRow(label: String, value: FieldPattern, onChange: (FieldPattern) -> Unit) {
    val state = rememberTextFieldState(value.pattern)
    LaunchedEffect(value.pattern) {
        if (state.text.toString() != value.pattern) state.setTextAndPlaceCursorAtEnd(value.pattern)
    }
    LaunchedEffect(state.text) {
        val text = state.text.toString()
        if (text != value.pattern) onChange(value.copy(pattern = text))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(80.dp))
        TextField(state = state, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value.isRegex, onCheckedChange = { onChange(value.copy(isRegex = it)) })
            Text("regex", modifier = Modifier.padding(start = 2.dp), fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value.caseSensitive, onCheckedChange = { onChange(value.copy(caseSensitive = it)) })
            Text("Aa", modifier = Modifier.padding(start = 2.dp), fontSize = 11.sp)
        }
    }
}

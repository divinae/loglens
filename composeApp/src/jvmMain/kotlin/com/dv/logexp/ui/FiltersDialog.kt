package com.dv.logexp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.logexp.model.FieldPattern
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FilterRule
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

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
) {
    val state = rememberDialogState(size = DpSize(900.dp, 600.dp))
    var draft by remember { mutableStateOf(filters) }
    var selectedId by remember { mutableStateOf(draft.firstOrNull()?.id) }
    val selected = draft.firstOrNull { it.id == selectedId }
    val dirty = draft != filters

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

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = if (dirty) "Saved Filters *" else "Saved Filters",
    ) {
        Column(modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            Row(modifier = Modifier.weight(1f)) {
                FilterList(
                    filters = draft,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onAdd = ::add,
                    onToggle = ::toggle,
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                )

                Divider(Orientation.Vertical, modifier = Modifier.fillMaxHeight())

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) {
                        FilterEditor(
                            rule = selected,
                            onChange = ::update,
                            onRemove = { remove(selected.id) },
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No filter selected. Click + to add one.", fontSize = 12.sp)
                        }
                    }
                }
            }

            Divider(Orientation.Horizontal)

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dirty) Text("Unsaved changes", fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(onClick = { draft = filters; selectedId = draft.firstOrNull()?.id }, enabled = dirty) {
                    Text("Discard")
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
                DefaultButton(onClick = { onSave(draft); onDismiss() }, enabled = dirty) { Text("Save") }
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", modifier = Modifier.weight(1f))
            DefaultButton(onClick = onAdd) { Text("+ Add") }
        }
        Divider(Orientation.Horizontal)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filters, key = { it.id }) { rule ->
                val isSelected = rule.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) JewelTheme.globalColors.panelBackground.copy(alpha = 0.6f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(rule.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                }
            }
        }
    }
}

@Composable
private fun FilterEditor(
    rule: FilterRule,
    onChange: (FilterRule) -> Unit,
    onRemove: () -> Unit,
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
            Text("Levels", modifier = Modifier.width(80.dp))
            LOG_LEVELS.forEach { lvl ->
                val checked = lvl in rule.levels
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            val newSet = if (checked) rule.levels - lvl else rule.levels + lvl
                            onChange(rule.copy(levels = newSet))
                        },
                    )
                    Text(lvl, modifier = Modifier.padding(start = 2.dp), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Divider(Orientation.Horizontal)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onRemove) { Text("Delete") }
        }
    }
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

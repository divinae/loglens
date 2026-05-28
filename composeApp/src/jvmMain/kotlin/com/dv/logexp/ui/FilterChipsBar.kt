package com.dv.logexp.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.dv.logexp.model.FilterMode
import com.dv.logexp.model.FilterRule
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FilterChipsBar(
    filters: List<FilterRule>,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val dy = event.changes.first().scrollDelta.y
                if (dy != 0f) scope.launch { scrollState.scrollBy(dy * 60f) }
            }
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { rule ->
            val state = remember(rule.id) { ContextMenuState() }
            ContextMenuArea(
                items = {
                    listOf(
                        ContextMenuItem("Edit filter") { onEdit(rule.id) },
                        ContextMenuItem("Delete filter") { onRemove(rule.id) },
                    )
                },
                state = state,
            ) {
                FilterChip(rule = rule, onClick = { onToggleEnabled(rule.id, !rule.enabled) })
            }
        }
        AddFilterChip(onClick = onAddNew)
    }
}

@Composable
private fun AddFilterChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", fontSize = 14.sp, color = JewelTheme.globalColors.text.normal)
    }
}

@Composable
private fun FilterChip(rule: FilterRule, onClick: () -> Unit) {
    val hasColor = rule.mode == FilterMode.HIGHLIGHT || rule.mode == FilterMode.SHOW_AND_HIGHLIGHT
    val shape = RoundedCornerShape(12.dp)
    val enabledAccent = JewelTheme.globalColors.text.info
    val background = if (rule.enabled) enabledAccent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (rule.enabled) enabledAccent else Color.Gray.copy(alpha = 0.5f)
    val textColor = if (rule.enabled) JewelTheme.globalColors.text.normal
    else JewelTheme.globalColors.text.disabled

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val modeLabel = when (rule.mode) {
            FilterMode.SHOW -> "S"
            FilterMode.HIDE -> "H"
            FilterMode.HIGHLIGHT -> null
            FilterMode.SHOW_AND_HIGHLIGHT -> "S"
        }
        if (hasColor) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(rule.highlightColor.toInt()), CircleShape)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
        }
        if (modeLabel != null) Text(modeLabel, fontSize = 10.sp, color = textColor)
        Text(rule.name, fontSize = 12.sp, color = textColor)
    }
}

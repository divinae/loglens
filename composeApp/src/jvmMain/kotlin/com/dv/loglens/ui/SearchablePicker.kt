package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.dv.loglens.ui.theme.islandsEditor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

// Drops the popup below the anchor, right edges aligned.
private object BelowRightAligned : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset((anchorBounds.right - popupContentSize.width).coerceAtLeast(0), anchorBounds.bottom)
}

/**
 * A button that opens a popup with a search field over a filtered (case-insensitive substring)
 * list of [options]; clicking an option calls [onPick] and closes it. [current] is shown in bold.
 * [itemFontFamily] optionally renders each option in its own family (e.g. a font preview).
 */
@Composable
fun SearchablePicker(
    options: List<String>,
    current: String,
    onPick: (String) -> Unit,
    buttonLabel: String,
    searchPlaceholder: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    popupWidth: Dp = 300.dp,
    itemFontFamily: ((String) -> FontFamily?)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(buttonLabel, fontSize = 11.sp, maxLines = 1)
        }
        if (expanded) {
            Popup(
                popupPositionProvider = BelowRightAligned,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                val searchState = rememberTextFieldState()
                val searchFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
                Column(
                    modifier = Modifier
                        .width(popupWidth)
                        .clip(RoundedCornerShape(6.dp))
                        .background(islandsEditor(JewelTheme.isDark))
                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextField(
                        state = searchState,
                        placeholder = { Text(searchPlaceholder, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                    val query = searchState.text.toString()
                    val shown = if (query.isBlank()) options
                    else options.filter { it.contains(query, ignoreCase = true) }
                    if (shown.isEmpty()) {
                        Text(
                            emptyText,
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.info,
                            modifier = Modifier.padding(4.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(shown, key = { it }) { option ->
                                val itemInteraction = remember { MutableInteractionSource() }
                                val itemHovered by itemInteraction.collectIsHoveredAsState()
                                val family = remember(option) { itemFontFamily?.invoke(option) }
                                Text(
                                    option,
                                    fontSize = if (family != null) 13.sp else 11.sp,
                                    fontFamily = family,
                                    fontWeight = if (option == current) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (itemHovered) JewelTheme.globalColors.text.normal.copy(alpha = 0.10f)
                                            else Color.Transparent
                                        )
                                        .hoverable(itemInteraction)
                                        .clickable { onPick(option); expanded = false }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

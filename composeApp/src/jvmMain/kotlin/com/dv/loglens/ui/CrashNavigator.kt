package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.loglens.ui.theme.islandsPopup
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val crashRed = Color(0xFFE5484D)

/** Hyperlink blue used by the bookmark navigator; matches the in-row bookmark affordance. */
private val bookmarkBlue = Color(0xFF589DF6)

/**
 * Floating panel over the log list that surfaces detected crashes/ANRs and steps between them.
 * Rendered only while [count] > 0; mirrors [FindBar]'s popup styling so the two read as a set.
 */
@Composable
fun CrashNavigator(
    count: Int,
    current: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigatorPill(
        // Show the current crash position once we're on one (e.g. "⚠ 2/5"); otherwise just the total.
        label = if (current >= 0) "⚠ ${current + 1}/$count" else "⚠ $count",
        tint = crashRed,
        prevDescription = "Previous crash / ANR",
        nextDescription = "Next crash / ANR",
        onPrev = onPrev,
        onNext = onNext,
        modifier = modifier,
    )
}

/** [CrashNavigator]'s sibling for bookmarked lines; same shape, bookmark icon and blue accent. */
@Composable
fun BookmarkNavigator(
    count: Int,
    current: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigatorPill(
        label = if (current >= 0) "${current + 1}/$count" else "$count",
        tint = bookmarkBlue,
        prevDescription = "Previous bookmark",
        nextDescription = "Next bookmark",
        onPrev = onPrev,
        onNext = onNext,
        icon = AllIconsKeys.Nodes.Bookmark,
        modifier = modifier,
    )
}

@Composable
private fun NavigatorPill(
    label: String,
    tint: Color,
    prevDescription: String,
    nextDescription: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconKey? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .background(islandsPopup(JewelTheme.isDark), shape)
            .border(1.dp, tint.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                key = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint,
        )
        IconActionButton(
            onClick = onPrev,
            key = AllIconsKeys.Actions.PreviousOccurence,
            contentDescription = prevDescription,
            colorFilter = ColorFilter.tint(tint),
        )
        IconActionButton(
            onClick = onNext,
            key = AllIconsKeys.Actions.NextOccurence,
            contentDescription = nextDescription,
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

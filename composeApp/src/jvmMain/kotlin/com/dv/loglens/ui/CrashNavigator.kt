package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val crashRed = Color(0xFFE5484D)

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
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .background(islandsPopup(JewelTheme.isDark), shape)
            .border(1.dp, crashRed.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Show the current crash position once we're on one (e.g. "⚠ 2/5"); otherwise just the total.
        Text(
            text = if (current >= 0) "⚠ ${current + 1}/$count" else "⚠ $count",
            fontSize = 12.sp,
            color = crashRed,
        )
        IconActionButton(
            onClick = onPrev,
            key = AllIconsKeys.Actions.PreviousOccurence,
            contentDescription = "Previous crash / ANR",
            colorFilter = ColorFilter.tint(crashRed),
        )
        IconActionButton(
            onClick = onNext,
            key = AllIconsKeys.Actions.NextOccurence,
            contentDescription = "Next crash / ANR",
            colorFilter = ColorFilter.tint(crashRed),
        )
    }
}

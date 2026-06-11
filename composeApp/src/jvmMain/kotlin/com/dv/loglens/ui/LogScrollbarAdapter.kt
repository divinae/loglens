package com.dv.loglens.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinux
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxDark
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxLight
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible

/**
 * IntelliJ vertical scrollbar — uses the vendored [VerticalScrollbar] (CoalescingScrollbar.kt, a copy
 * of Jewel's with drag coalescing for smooth fast dragging), so the look and behaviour match the IDE:
 * an always-visible track that tints on hover and click-on-track jumps the thumb to the clicked spot
 * ([AlwaysVisible] + JumpToSpot, the Windows/Linux defaults). Jewel's Linux default thumb is square,
 * so we round it ([CornerSize] 100); the track is kept slim (9dp) with no padding so the thumb fills
 * its width.
 */
@Composable
fun IntelliJVerticalScrollbar(
    state: LazyListState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val style = remember(isDark) {
        val base = if (isDark) ScrollbarStyle.windowsAndLinuxDark() else ScrollbarStyle.windowsAndLinuxLight()
        ScrollbarStyle(
            colors = base.colors,
            metrics = ScrollbarMetrics(thumbCornerSize = CornerSize(100), minThumbLength = 24.dp),
            trackClickBehavior = base.trackClickBehavior,
            scrollbarVisibility = AlwaysVisible.windowsAndLinux(
                trackThickness = 9.dp,
                trackPadding = PaddingValues(0.dp),
            ),
        )
    }
    VerticalScrollbar(scrollState = state, modifier = modifier, style = style, interactionSource = interactionSource)
}

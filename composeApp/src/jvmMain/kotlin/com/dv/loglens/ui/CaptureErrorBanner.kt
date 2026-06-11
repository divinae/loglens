package com.dv.loglens.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dv.loglens.ui.theme.islandsPopup
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Saturated red for the accent bar and warning glyph; readable on both themes. */
private fun errorAccent(isDark: Boolean) = if (isDark) Color(0xFFFF6B68) else Color(0xFFCC0000)

/**
 * An independent floating element that reports a live-capture error, styled like [Toast] but with a
 * red accent and an explicit dismiss button — it stays until the user closes it (or a new capture
 * starts) rather than auto-fading, since a capture failure is worth acting on. A non-null [text]
 * shows it; the last text is retained through the exit animation so it doesn't blank mid-dismiss.
 */
@Composable
fun CaptureErrorBanner(text: String?, isDark: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The ⚠ glyph already signals an error, so drop a redundant leading "error:" from the message.
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text.trim().replaceFirst(Regex("^error:?\\s*", RegexOption.IGNORE_CASE), "")

    val accent = errorAccent(isDark)
    val shape = RoundedCornerShape(10.dp)
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .shadow(6.dp, shape)
                .clip(shape)
                .background(islandsPopup(isDark))
                .border(1.dp, accent.copy(alpha = 0.6f), shape),
        ) {
            // Accent strip running the full height of the banner.
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            ) {
                Text("⚠", color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                // Cap the text width so a long message wraps (instead of pushing the dismiss button
                // off the edge), while a short one still produces a snug, narrow banner.
                Text(
                    lastText,
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 440.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Top)) {
                    Icon(AllIconsKeys.Actions.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

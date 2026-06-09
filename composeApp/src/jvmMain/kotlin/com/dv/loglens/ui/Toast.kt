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
import androidx.compose.ui.unit.dp
import com.dv.loglens.ui.theme.islandsPopup
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Accent for the left bar and check mark; slightly brighter in dark mode. */
private fun toastAccent(isDark: Boolean) = if (isDark) Color(0xFF4A8CF7) else Color(0xFF2C6FE0)

/**
 * Transient notification that drops in from the top of the log list and fades out — styled after an
 * IDE notification: neutral popup fill, an accent bar down the left edge, a check mark, and a soft
 * shadow. Driven by a nullable [text]: a non-null value shows it, null dismisses it. The last shown
 * text is retained through the exit animation so the pill doesn't blank before it disappears. The
 * caller owns the dismiss timer.
 */
@Composable
fun Toast(text: String?, isDark: Boolean, modifier: Modifier = Modifier) {
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text

    val accent = toastAccent(isDark)
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
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape),
        ) {
            // Accent strip running the full height of the toast.
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("✓", color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    lastText,
                    color = JewelTheme.globalColors.text.normal,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

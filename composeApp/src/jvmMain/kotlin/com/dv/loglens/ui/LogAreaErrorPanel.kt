package com.dv.loglens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Saturated red for the icon/title; readable on both themes. */
private fun panelAccent(isDark: Boolean) = if (isDark) Color(0xFFFF6B68) else Color(0xFFCC0000)

/**
 * Centered placeholder shown in the (otherwise empty) log area when a capture or file-load error
 * leaves nothing to display: a large warning glyph, a short [title], and the [message] details.
 */
@Composable
fun LogAreaErrorPanel(title: String, message: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val accent = panelAccent(isDark)
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚠", color = accent, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            message,
            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

package com.dv.logexp.ui.theme

import androidx.compose.ui.graphics.Color

fun logLevelColor(level: String, isDarkMode: Boolean): Color = when (level.trim()) {
    "E", "F" -> if (isDarkMode) Color(0xFFFF5252) else Color(0xFFC62828)
    "W" -> if (isDarkMode) Color(0xFFFFD740) else Color(0xFFE65100)
    "I" -> if (isDarkMode) Color(0xFF69F0AE) else Color(0xFF2E7D32)
    "D" -> if (isDarkMode) Color(0xFF40C4FF) else Color(0xFF1565C0)
    "V" -> if (isDarkMode) Color.LightGray else Color(0xFF424242)
    else -> if (isDarkMode) Color.LightGray else Color(0xFF212121)
}

val ROW_SELECTED_DARK = Color(0xFF264B77)
val ROW_SELECTED_LIGHT = Color(0xFFD6EAF8)

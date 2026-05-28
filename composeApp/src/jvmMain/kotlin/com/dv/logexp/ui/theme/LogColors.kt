package com.dv.logexp.ui.theme

import androidx.compose.ui.graphics.Color

fun logLevelColor(level: String, isDarkMode: Boolean): Color = when (level.trim()) {
    "E", "F" -> if (isDarkMode) Color(0xFFFF6B68) else Color(0xFFFF0000)
    "W" -> if (isDarkMode) Color(0xFFF09400) else Color(0xFFFF681F)
    "I" -> if (isDarkMode) Color(0xFFABC023) else Color(0xFF228B22)
    "D" -> if (isDarkMode) Color(0xFF299999) else Color(0xFF0000DD)
    "V" -> if (isDarkMode) Color(0xFFBBBBBB) else Color(0xFF000000)
    else -> if (isDarkMode) Color(0xFFBBBBBB) else Color(0xFF000000)
}

val ROW_SELECTED_DARK = Color(0xFF264B77)
val ROW_SELECTED_LIGHT = Color(0xFFD6EAF8)

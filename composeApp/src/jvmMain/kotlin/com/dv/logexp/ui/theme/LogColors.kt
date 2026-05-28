package com.dv.logexp.ui.theme

import androidx.compose.ui.graphics.Color

fun logLevelColor(level: String, isDarkMode: Boolean): Color = when (level.trim()) {
    "E", "F" -> if (isDarkMode) IslandsDark.logError else IslandsLight.logError
    "W" -> if (isDarkMode) IslandsDark.logWarn else IslandsLight.logWarn
    "I" -> if (isDarkMode) IslandsDark.logInfo else IslandsLight.logInfo
    "D" -> if (isDarkMode) IslandsDark.logDebug else IslandsLight.logDebug
    "V" -> if (isDarkMode) IslandsDark.logVerbose else IslandsLight.logVerbose
    else -> if (isDarkMode) IslandsDark.logVerbose else IslandsLight.logVerbose
}

val ROW_SELECTED_DARK = IslandsDark.rowSelected
val ROW_SELECTED_LIGHT = IslandsLight.rowSelected

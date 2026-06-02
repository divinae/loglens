package com.dv.loglens.ui.theme

import androidx.compose.ui.graphics.Color

object IslandsDark {
    val panel = Color(0xFF1F2022)
    val editor = Color(0xFF1F2022)
    val popup = Color(0xFF2B2D30)
    val rowSelected = Color(0xFF2E436E)
    val borderNormal = Color(0xFF393B40)
    val borderFocused = Color(0xFF4E5157)
    val textNormal = Color(0xFFDFE1E5)
    val textDisabled = Color(0xFF7A7E85)
    val textInfo = Color(0xFF5D8CC7)
    val textError = Color(0xFFFF6B68)

    val logError = Color(0xFFFF6B68)
    val logWarn = Color(0xFFE8A33D)
    val logInfo = Color(0xFF6FAF3D)
    val logDebug = Color(0xFF5DBED0)
    val logVerbose = Color(0xFFBBBBBB)
}

object IslandsLight {
    val panel = Color(0xFFE9EAEB)
    val editor = Color(0xFFFFFFFF)
    val popup = Color(0xFFF7F8F9)
    val rowSelected = Color(0xFFD0E3FF)
    val borderNormal = Color(0xFFD3D5DB)
    val borderFocused = Color(0xFFB6B8BD)
    val textNormal = Color(0xFF1F2125)
    val textDisabled = Color(0xFF8C8F95)
    val textInfo = Color(0xFF3574F0)
    val textError = Color(0xFFC7222D)

    val logError = Color(0xFFC7222D)
    val logWarn = Color(0xFFB26000)
    val logInfo = Color(0xFF2E7D32)
    val logDebug = Color(0xFF0000DD)
    val logVerbose = Color(0xFF000000)
}

fun islandsPanel(isDark: Boolean) = if (isDark) IslandsDark.panel else IslandsLight.panel
fun islandsEditor(isDark: Boolean) = if (isDark) IslandsDark.editor else IslandsLight.editor
fun islandsPopup(isDark: Boolean) = if (isDark) IslandsDark.popup else IslandsLight.popup

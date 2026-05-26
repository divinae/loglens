package com.dv.logexp.service

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.asComposeFontFamily
import java.awt.Font
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalTextApi::class)
object SystemFonts {
    private val cachedNames: List<String> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .toList()
                .distinct()
                .sorted()
        }.getOrElse { emptyList() }
    }

    fun list(): List<String> = cachedNames

    fun resolve(name: String): FontFamily = runCatching {
        Font(name, Font.PLAIN, 12).asComposeFontFamily()
    }.getOrElse { FontFamily.Monospace }
}

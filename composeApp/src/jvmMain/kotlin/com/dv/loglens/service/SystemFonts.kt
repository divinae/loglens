package com.dv.loglens.service

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import java.awt.GraphicsEnvironment

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

    fun resolve(name: String, weight: Int = 400): FontFamily = when (name) {
        "Monospaced" -> FontFamily.Monospace
        "SansSerif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Dialog", "DialogInput" -> FontFamily.Default
        else -> EmbeddedFonts.resolve(name) ?: resolveNamed(name, weight) ?: FontFamily.Monospace
    }

    private fun resolveNamed(name: String, weight: Int): FontFamily? = runCatching {
        val skTypeface = FontMgr.default.matchFamilyStyle(
            name,
            FontStyle(weight.coerceIn(100, 900), 5, FontSlant.UPRIGHT),
        ) ?: return@runCatching null
        FontFamily(Typeface(skTypeface))
    }.getOrNull()
}

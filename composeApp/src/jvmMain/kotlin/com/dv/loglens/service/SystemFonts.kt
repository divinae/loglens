package com.dv.loglens.service

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import com.dv.loglens.model.FontWeightPreset
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

    /**
     * The weight presets [name] actually has a face for, so the picker doesn't offer weights that
     * would silently render as Normal (e.g. Maple Mono installs only Regular and Bold). Logical
     * families map to platform fonts with Regular and Bold.
     */
    fun availableWeights(name: String): List<FontWeightPreset> {
        if (EmbeddedFonts.resolve(name) != null) return FontWeightPreset.entries
        val logical = setOf("Monospaced", "SansSerif", "Serif", "Dialog", "DialogInput")
        if (name in logical) return listOf(FontWeightPreset.NORMAL, FontWeightPreset.BOLD)
        val weights = runCatching {
            FontMgr.default.matchFamily(name).use { set ->
                (0 until set.count()).map { set.getStyle(it) }
                    .filter { it.slant == FontSlant.UPRIGHT }
                    .map { it.weight }
                    .toSet()
            }
        }.getOrDefault(emptySet())
        // Normal stays selectable regardless: it's the fallback the renderer uses anyway.
        return FontWeightPreset.entries.filter { it == FontWeightPreset.NORMAL || it.weight in weights }
    }

    private fun resolveNamed(name: String, weight: Int): FontFamily? = runCatching {
        val skTypeface = FontMgr.default.matchFamilyStyle(
            name,
            FontStyle(weight.coerceIn(100, 900), 5, FontSlant.UPRIGHT),
        ) ?: return@runCatching null
        FontFamily(Typeface(skTypeface))
    }.getOrNull()
}

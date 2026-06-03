package com.dv.loglens.service

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Fonts bundled in the app jar (under resources/fonts), so the UI has a known-good monospace face
 * regardless of what's installed on the host. JetBrains Mono ships under the SIL OFL (see the
 * licence copied next to the .ttf files).
 */
object EmbeddedFonts {
    const val JETBRAINS_MONO = "JetBrains Mono"

    /** Display names of embedded families, surfaced at the top of the font picker. */
    val names: List<String> = listOf(JETBRAINS_MONO)

    private val jetBrainsMono: FontFamily by lazy {
        FontFamily(
            Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal, FontStyle.Normal),
            Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium, FontStyle.Normal),
            Font("fonts/JetBrainsMono-SemiBold.ttf", FontWeight.SemiBold, FontStyle.Normal),
            Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold, FontStyle.Normal),
        )
    }

    /** The embedded [FontFamily] for [name], or null if it isn't one we bundle. */
    fun resolve(name: String): FontFamily? = if (name == JETBRAINS_MONO) jetBrainsMono else null
}

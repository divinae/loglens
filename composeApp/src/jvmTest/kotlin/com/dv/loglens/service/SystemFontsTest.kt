package com.dv.loglens.service

import com.dv.loglens.model.FontWeightPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemFontsTest {

    @Test
    fun embeddedJetBrainsMonoOffersEveryWeight() {
        assertEquals(FontWeightPreset.entries, SystemFonts.availableWeights(EmbeddedFonts.JETBRAINS_MONO))
    }

    @Test
    fun logicalFamiliesOfferNormalAndBold() {
        assertEquals(
            listOf(FontWeightPreset.NORMAL, FontWeightPreset.BOLD),
            SystemFonts.availableWeights("Monospaced"),
        )
    }

    @Test
    fun unknownFamilyOffersOnlyNormal() {
        assertEquals(listOf(FontWeightPreset.NORMAL), SystemFonts.availableWeights("No Such Font 1f3a9c"))
    }

    // Host-specific; prints instead of asserting so it never fails on CI.
    @Test
    fun printInstalledMapleMono() {
        println("Maple Mono weights: " + SystemFonts.availableWeights("Maple Mono"))
    }
}

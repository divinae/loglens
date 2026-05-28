package com.dv.logexp.model

enum class FontWeightPreset(val displayName: String, val weight: Int) {
    NORMAL("Normal", 400),
    MEDIUM("Medium", 500),
    SEMI_BOLD("SemiBold", 600),
    BOLD("Bold", 700);

    companion object {
        fun fromName(name: String?): FontWeightPreset =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

const val DEFAULT_FONT_WEIGHT: String = "NORMAL"

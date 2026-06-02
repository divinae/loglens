package com.dv.loglens.ui.theme

/** A selectable highlight color: ARGB [value] paired with a display [name]. */
data class HighlightColor(val value: Long, val name: String)

/** Preset highlight colors shared between the filters dialog and the log context menu. */
val HIGHLIGHT_COLORS: List<HighlightColor> = listOf(
    HighlightColor(0x33FFEB3BL, "Yellow"),
    HighlightColor(0x33FF9800L, "Orange"),
    HighlightColor(0x33F44336L, "Red"),
    HighlightColor(0x33E040FBL, "Magenta"),
    HighlightColor(0x337C4DFFL, "Purple"),
    HighlightColor(0x33448AFFL, "Blue"),
    HighlightColor(0x3300B0FFL, "Cyan"),
    HighlightColor(0x331DE9B6L, "Teal"),
    HighlightColor(0x3376FF03L, "Green"),
    HighlightColor(0x3390A4AEL, "Gray"),
)

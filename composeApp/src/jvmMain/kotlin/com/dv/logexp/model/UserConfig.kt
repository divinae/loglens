package com.dv.logexp.model

import kotlinx.serialization.Serializable

@Serializable
data class UserConfig(
    val visibleColumns: Set<LogColumn> = DEFAULT_VISIBLE_COLUMNS,
    val columnWidths: Map<LogColumn, Float> = DEFAULT_COLUMN_WIDTHS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Float = DEFAULT_FONT_SIZE,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val fontWeight: String = DEFAULT_FONT_WEIGHT,
    val softWrap: Boolean = true,
    val filtersListWidth: Float = DEFAULT_FILTERS_LIST_WIDTH,
    val alwaysShowBookmarks: Boolean = true,
    val quickFilterVisible: Boolean = true,
)

const val DEFAULT_FILTERS_LIST_WIDTH: Float = 280f

val DEFAULT_VISIBLE_COLUMNS: Set<LogColumn> = setOf(
    LogColumn.DATE,
    LogColumn.TIME,
    LogColumn.PROCESS,
    LogColumn.LEVEL,
    LogColumn.TAG,
    LogColumn.MESSAGE
)

val DEFAULT_COLUMN_WIDTHS: Map<LogColumn, Float> = mapOf(
    LogColumn.DATE to 60f,
    LogColumn.TIME to 100f,
    LogColumn.PID to 60f,
    LogColumn.TID to 60f,
    LogColumn.PROCESS to 120f,
    LogColumn.LEVEL to 30f,
    LogColumn.TAG to 120f
)

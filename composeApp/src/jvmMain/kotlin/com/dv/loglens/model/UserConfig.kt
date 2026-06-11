package com.dv.loglens.model

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
    // Foldable multiline records (crash dumps, multiline prints) start expanded instead of collapsed.
    val expandMultilineByDefault: Boolean = false,
    val filtersListWidth: Float = DEFAULT_FILTERS_LIST_WIDTH,
    val alwaysShowBookmarks: Boolean = true,
    val quickFilterVisible: Boolean = true,
    val logBuffers: Set<LogBuffer> = DEFAULT_LOG_BUFFERS,
    val searchQuery: String = "",
    val screenshotDir: String = "",
    val openScreenshotAfterCapture: Boolean = true,
    val adbPath: String = "",
    val recordingDir: String = "",
    val recordingShowTaps: Boolean = false,
    val recordingBitrateMbps: Int = 0,
    val recordingSize: String = "",
    // Absolute paths of recently opened log files, most-recent first.
    val recentFiles: List<String> = emptyList(),
    // Window bounds in dp. x/y < 0 means "unplaced" → center on first launch.
    val windowX: Float = -1f,
    val windowY: Float = -1f,
    val windowWidth: Float = DEFAULT_WINDOW_WIDTH,
    val windowHeight: Float = DEFAULT_WINDOW_HEIGHT,
)

const val DEFAULT_WINDOW_WIDTH: Float = 1200f
const val DEFAULT_WINDOW_HEIGHT: Float = 800f

/** Window geometry in dp; x/y < 0 means unplaced (center). */
data class WindowBounds(
    val x: Float = -1f,
    val y: Float = -1f,
    val width: Float = DEFAULT_WINDOW_WIDTH,
    val height: Float = DEFAULT_WINDOW_HEIGHT,
)

const val DEFAULT_FILTERS_LIST_WIDTH: Float = 280f

val DEFAULT_VISIBLE_COLUMNS: Set<LogColumn> = setOf(
    LogColumn.INDEX,
    LogColumn.DATE,
    LogColumn.TIME,
    LogColumn.PROCESS,
    LogColumn.LEVEL,
    LogColumn.TAG,
    LogColumn.MESSAGE
)

val DEFAULT_COLUMN_WIDTHS: Map<LogColumn, Float> = mapOf(
    LogColumn.INDEX to 60f,
    LogColumn.DATE to 60f,
    LogColumn.TIME to 100f,
    LogColumn.DELTA to 70f,
    LogColumn.PID to 60f,
    LogColumn.TID to 60f,
    LogColumn.PROCESS to 120f,
    LogColumn.LEVEL to 30f,
    LogColumn.TAG to 120f
)

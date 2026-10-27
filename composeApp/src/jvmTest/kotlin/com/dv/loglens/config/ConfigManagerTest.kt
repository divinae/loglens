package com.dv.loglens.config

import com.dv.loglens.model.DEFAULT_COLUMN_WIDTHS
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.UserConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigManagerTest {

    private val dir = Files.createTempDirectory("loglens-config").toFile()
    private val manager = ConfigManager(configDir = dir)

    @Test
    fun saveThenLoadRoundTripsAndLeavesNoTempFiles() {
        val config = UserConfig(fontSize = 15f)
        manager.save(config)
        assertEquals(config.fontSize, manager.load().fontSize)
        assertEquals(listOf("config.json"), dir.list()!!.toList())
    }

    @Test
    fun zeroColumnWidthsFallBackToDefaults() {
        File(dir, "config.json").writeText("""{"columnWidths": {"TIME": 0.0, "TAG": 198.0}}""")
        val widths = manager.load().columnWidths
        assertEquals(DEFAULT_COLUMN_WIDTHS[LogColumn.TIME], widths[LogColumn.TIME])
        assertEquals(198f, widths[LogColumn.TAG])
    }
}

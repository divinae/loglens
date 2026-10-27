package com.dv.loglens.config

import com.dv.loglens.model.DEFAULT_COLUMN_WIDTHS
import com.dv.loglens.model.MIN_COLUMN_WIDTH
import com.dv.loglens.model.UserConfig
import com.dv.loglens.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ConfigManager(
    private val configDir: File = File(System.getProperty("user.home"), ".config/loglens"),
    private val configFile: File = File(configDir, "config.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): UserConfig = try {
        if (configFile.exists()) normalize(json.decodeFromString<UserConfig>(configFile.readText()))
        else UserConfig()
    } catch (e: Exception) {
        Log.w("failed to load config; using defaults", e)
        UserConfig()
    }

    /**
     * Synchronized because saves race: background saves on Dispatchers.IO and the shutdown hook's
     * final save. Written to a temp file and moved over the config, so a save cut short by the JVM
     * exiting leaves the previous config intact instead of a truncated one.
     */
    @Synchronized
    fun save(config: UserConfig) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            val tmp = Files.createTempFile(configDir.toPath(), "config.json.", ".tmp")
            try {
                Files.writeString(tmp, json.encodeToString(config))
                Files.move(
                    tmp,
                    configFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                Files.deleteIfExists(tmp)
            }
        } catch (e: Exception) {
            Log.w("failed to save config", e)
        }
    }

    // A width under the minimum (0 was written for hidden columns by an external config writer)
    // would make a visible column invisible; fall back to the default width.
    private fun normalize(config: UserConfig) = config.copy(
        columnWidths = DEFAULT_COLUMN_WIDTHS + config.columnWidths.filterValues { it >= MIN_COLUMN_WIDTH },
    )
}

package com.dv.loglens.config

import com.dv.loglens.model.UserConfig
import kotlinx.serialization.json.Json
import java.io.File

class ConfigManager(
    private val configDir: File = File(System.getProperty("user.home"), ".config/loglens"),
    private val configFile: File = File(configDir, "config.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): UserConfig = try {
        if (configFile.exists()) json.decodeFromString<UserConfig>(configFile.readText())
        else UserConfig()
    } catch (e: Exception) {
        System.err.println("loglens: failed to load config (${e.message}); using defaults")
        UserConfig()
    }

    fun save(config: UserConfig) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            configFile.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            System.err.println("loglens: failed to save config: ${e.message}")
        }
    }
}

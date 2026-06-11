package com.dv.loglens.config

import com.dv.loglens.model.UserConfig
import com.dv.loglens.util.Log
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
        Log.w("failed to load config; using defaults", e)
        UserConfig()
    }

    fun save(config: UserConfig) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            configFile.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            Log.w("failed to save config", e)
        }
    }
}

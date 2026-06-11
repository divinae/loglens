package com.dv.loglens.config

import com.dv.loglens.util.Log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persists the most-recent quick-filter queries (most-recent first) to disk. */
class SearchHistoryStore(
    private val configDir: File = File(System.getProperty("user.home"), ".config/loglens"),
    private val file: File = File(configDir, "search-history.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(String.serializer())

    fun load(): List<String> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText())
        else emptyList()
    } catch (e: Exception) {
        Log.w("failed to load search history", e)
        emptyList()
    }

    fun save(history: List<String>) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            file.writeText(json.encodeToString(serializer, history))
        } catch (e: Exception) {
            Log.w("failed to save search history", e)
        }
    }
}

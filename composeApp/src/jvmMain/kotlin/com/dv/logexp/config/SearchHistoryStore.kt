package com.dv.logexp.config

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persists the most-recent quick-filter queries (most-recent first) to disk. */
class SearchHistoryStore(
    private val configDir: File = File(System.getProperty("user.home"), ".config/logexp"),
    private val file: File = File(configDir, "search-history.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(String.serializer())

    fun load(): List<String> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText())
        else emptyList()
    } catch (e: Exception) {
        System.err.println("logexp: failed to load search history (${e.message})")
        emptyList()
    }

    fun save(history: List<String>) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            file.writeText(json.encodeToString(serializer, history))
        } catch (e: Exception) {
            System.err.println("logexp: failed to save search history: ${e.message}")
        }
    }
}

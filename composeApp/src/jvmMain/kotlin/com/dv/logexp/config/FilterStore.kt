package com.dv.logexp.config

import com.dv.logexp.model.FilterRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class FilterStore(
    private val configDir: File = File(System.getProperty("user.home"), ".config/logexp"),
    private val file: File = File(configDir, "filters.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(FilterRule.serializer())

    fun load(): List<FilterRule> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText())
        else emptyList()
    } catch (e: Exception) {
        System.err.println("logexp: failed to load filters (${e.message})")
        emptyList()
    }

    fun save(filters: List<FilterRule>) {
        try {
            if (!configDir.exists()) configDir.mkdirs()
            file.writeText(json.encodeToString(serializer, filters))
        } catch (e: Exception) {
            System.err.println("logexp: failed to save filters: ${e.message}")
        }
    }
}

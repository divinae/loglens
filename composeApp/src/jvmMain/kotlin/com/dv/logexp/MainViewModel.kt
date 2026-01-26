package com.dv.logexp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

enum class LogColumn(val displayName: String) {
    TIME("Time"),
    PID("PID"),
    TID("TID"),
    LEVEL("Level"),
    TAG("Tag"),
    MESSAGE("Message")
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class MainViewModel : ViewModel() {
    val logs = mutableStateListOf<LogEntry>()
    val selectedIds = mutableStateSetOf<Int>()
    val bookmarkedIds = mutableStateSetOf<Int>()
    val pidToPackage = mutableStateMapOf<String, String>()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _appFilter = MutableStateFlow("")
    val appFilter = _appFilter.asStateFlow()

    private val _autoFollow = MutableStateFlow(true)
    val autoFollow = _autoFollow.asStateFlow()

    private val _visibleColumns = MutableStateFlow(setOf(LogColumn.LEVEL, LogColumn.TAG, LogColumn.MESSAGE))
    val visibleColumns = _visibleColumns.asStateFlow()

    private val _columnWidths = MutableStateFlow(
        mapOf(
            LogColumn.TIME to 160f,
            LogColumn.PID to 60f,
            LogColumn.TID to 60f,
            LogColumn.LEVEL to 30f,
            LogColumn.TAG to 120f
        )
    )
    val columnWidths = _columnWidths.asStateFlow()

    init {
        startCapture()
        refreshPackages()
    }

    fun findNextBookmark(currentId: Int): Int? {
        val sortedBookmarks = bookmarkedIds.sorted()
        if (sortedBookmarks.isEmpty()) return null

        val currentIndex = sortedBookmarks.indexOfFirst { it > currentId }
        return if (currentIndex != -1) {
            sortedBookmarks[currentIndex]
        } else {
            sortedBookmarks.first()
        }
    }

    fun toggleBookmark(id: Int) {
        if (bookmarkedIds.contains(id)) {
            bookmarkedIds.remove(id)
        } else {
            bookmarkedIds.add(id)
        }
    }

    fun setAutoFollow(enabled: Boolean) {
        _autoFollow.value = enabled
    }

    fun toggleAutoFollow() {
        _autoFollow.value = !_autoFollow.value
    }

    fun updateAppFilter(filter: String) {
        _appFilter.value = filter
    }

    fun refreshPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("adb", "shell", "ps", "-A", "-o", "PID,NAME").start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"), 2)
                    if (parts.size == 2 && parts[0].all { it.isDigit() }) {
                        pidToPackage[parts[0]] = parts[1]
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun toggleSelection(id: Int, multiSelect: Boolean) {
        if (!multiSelect) {
            val wasSelected = selectedIds.contains(id)
            selectedIds.clear()
            if (!wasSelected) selectedIds.add(id)
        } else {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
        }
    }

    fun toggleColumn(column: LogColumn) {
        val current = _visibleColumns.value
        if (current.contains(column)) {
            _visibleColumns.value = current - column
        } else {
            _visibleColumns.value = current + column
        }
    }

    fun updateColumnWidth(column: LogColumn, width: Float) {
        val current = _columnWidths.value.toMutableMap()
        current[column] = width.coerceAtLeast(10f)
        _columnWidths.value = current
    }

    fun toggleTheme() {
        _themeMode.value = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startCapture() {
        if (_isCapturing.value) return

        _isCapturing.value = true
        viewModelScope.launch {
            startLogcatCapture { newLog ->
                launch(Dispatchers.Main) {
                    logs.add(parseLog(logs.size, newLog))
                }
            }
        }
    }

    fun clearLogs() {
        logs.clear()
        selectedIds.clear()
    }

    private val logPattern = Pattern.compile("^([\\d-]+\\s[\\d:.]+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+(.*?):?\\s(.*)$")

    private fun parseLog(id: Int, rawLine: String): LogEntry {
        if (rawLine.startsWith("---------")) {
            return LogEntry(id = id, content = rawLine, message = rawLine, level = "I")
        }
        
        val matcher = logPattern.matcher(rawLine.trim())
        return if (matcher.find()) {
            val pid = matcher.group(2)
            LogEntry(
                id = id,
                content = rawLine,
                time = matcher.group(1),
                pid = pid,
                tid = matcher.group(3),
                level = matcher.group(4),
                tag = matcher.group(5).trim(),
                message = matcher.group(6),
                appName = pidToPackage[pid] ?: ""
            )
        } else {
            LogEntry(id = id, content = rawLine, message = rawLine, level = "D")
        }
    }

    private suspend fun startLogcatCapture(onLogReceived: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("adb", "logcat", "-v", "threadtime").start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                if (!process.isAlive && process.exitValue() != 0) {
                   onLogReceived("Error: Process adb exited with code ${process.exitValue()}")
                   return@withContext
                }

                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        onLogReceived(line)
                    }
                }
            } catch (e: Exception) {
                onLogReceived("Error: ${e.message}. Sprawdź czy ADB jest w PATH.")
            } finally {
                _isCapturing.value = false
            }
        }
    }
}

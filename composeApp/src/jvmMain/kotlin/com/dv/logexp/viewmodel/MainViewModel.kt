package com.dv.logexp.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dv.logexp.config.ConfigManager
import com.dv.logexp.model.DEFAULT_COLUMN_WIDTHS
import com.dv.logexp.model.DEFAULT_VISIBLE_COLUMNS
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.LogEntry
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.model.UserConfig
import com.dv.logexp.parser.LogParser
import com.dv.logexp.service.LogcatService
import com.dv.logexp.service.PackageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val logcatService: LogcatService = LogcatService(),
) : ViewModel() {

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

    private val _visibleColumns = MutableStateFlow(DEFAULT_VISIBLE_COLUMNS)
    val visibleColumns = _visibleColumns.asStateFlow()

    private val _columnWidths = MutableStateFlow(DEFAULT_COLUMN_WIDTHS)
    val columnWidths = _columnWidths.asStateFlow()

    init {
        applyConfig(configManager.load())

        viewModelScope.launch {
            while (isActive) {
                refreshPackages()
                delay(5000)
            }
        }

        startCapture()
    }

    private fun applyConfig(config: UserConfig) {
        _visibleColumns.value = config.visibleColumns
        _columnWidths.value = config.columnWidths
        _appFilter.value = config.appFilter
        _themeMode.value = config.themeMode
    }

    private fun persistConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.save(
                UserConfig(
                    visibleColumns = _visibleColumns.value,
                    columnWidths = _columnWidths.value,
                    appFilter = _appFilter.value,
                    themeMode = _themeMode.value,
                )
            )
        }
    }

    fun findNextBookmark(currentId: Int): Int? {
        val sorted = bookmarkedIds.sorted()
        return sorted.firstOrNull { it > currentId } ?: sorted.firstOrNull()
    }

    fun findPreviousBookmark(currentId: Int): Int? {
        val sorted = bookmarkedIds.sortedDescending()
        return sorted.firstOrNull { it < currentId } ?: sorted.firstOrNull()
    }

    fun toggleBookmark(id: Int) {
        if (!bookmarkedIds.add(id)) bookmarkedIds.remove(id)
    }

    fun setAutoFollow(enabled: Boolean) {
        _autoFollow.value = enabled
    }

    fun toggleAutoFollow() {
        _autoFollow.value = !_autoFollow.value
    }

    fun updateAppFilter(filter: String) {
        _appFilter.value = filter
        persistConfig()
    }

    fun refreshPackages() {
        viewModelScope.launch {
            val newMap = PackageService.fetchPidMap()
            if (newMap.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                pidToPackage.clear()
                pidToPackage.putAll(newMap)
            }
        }
    }

    fun toggleSelection(id: Int, multiSelect: Boolean) {
        if (!multiSelect) {
            val wasSelected = selectedIds.contains(id)
            selectedIds.clear()
            if (!wasSelected) selectedIds.add(id)
        } else {
            if (!selectedIds.add(id)) selectedIds.remove(id)
        }
    }

    fun toggleColumn(column: LogColumn) {
        val current = _visibleColumns.value
        _visibleColumns.value = if (column in current) current - column else current + column
        persistConfig()
    }

    fun updateColumnWidth(column: LogColumn, width: Float) {
        _columnWidths.value = _columnWidths.value.toMutableMap().apply {
            this[column] = width.coerceAtLeast(10f)
        }
        persistConfig()
    }

    fun toggleTheme() {
        _themeMode.value = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        persistConfig()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startCapture() {
        if (_isCapturing.value) return
        _isCapturing.value = true

        viewModelScope.launch {
            try {
                logcatService.stream().collect { line ->
                    withContext(Dispatchers.Main) {
                        logs.add(LogParser.parse(logs.size, line, pidToPackage))
                    }
                }
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun clearLogs() {
        logs.clear()
        selectedIds.clear()
    }

    override fun onCleared() {
        logcatService.stop()
        super.onCleared()
    }
}

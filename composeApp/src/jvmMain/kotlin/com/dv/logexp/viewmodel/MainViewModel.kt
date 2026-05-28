package com.dv.logexp.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dv.logexp.config.ConfigManager
import com.dv.logexp.config.FilterStore
import com.dv.logexp.model.DEFAULT_COLUMN_WIDTHS
import com.dv.logexp.model.DEFAULT_FONT_FAMILY
import com.dv.logexp.model.DEFAULT_FONT_SIZE
import com.dv.logexp.model.DEFAULT_FONT_WEIGHT
import com.dv.logexp.model.DEFAULT_VISIBLE_COLUMNS
import com.dv.logexp.model.Device
import com.dv.logexp.model.FilterRule
import com.dv.logexp.model.LogColumn
import com.dv.logexp.model.LogEntry
import com.dv.logexp.model.MAX_FONT_SIZE
import com.dv.logexp.model.MIN_FONT_SIZE
import com.dv.logexp.model.ThemeMode
import com.dv.logexp.model.UserConfig
import com.dv.logexp.parser.LogParser
import com.dv.logexp.service.DeviceService
import com.dv.logexp.service.LogcatService
import com.dv.logexp.service.PackageService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val filterStore: FilterStore = FilterStore(),
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

    private val _fontSize = MutableStateFlow(DEFAULT_FONT_SIZE)
    val fontSize = _fontSize.asStateFlow()

    private val _fontFamily = MutableStateFlow(DEFAULT_FONT_FAMILY)
    val fontFamily = _fontFamily.asStateFlow()

    private val _fontWeight = MutableStateFlow(DEFAULT_FONT_WEIGHT)
    val fontWeight = _fontWeight.asStateFlow()

    private val _softWrap = MutableStateFlow(true)
    val softWrap = _softWrap.asStateFlow()

    private val _filters = MutableStateFlow<List<FilterRule>>(emptyList())
    val filters = _filters.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<String?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private var captureJob: Job? = null
    private var captureGen: Long = 0

    init {
        applyConfig(configManager.load())
        _filters.value = filterStore.load()

        viewModelScope.launch {
            while (isActive) {
                refreshDevices()
                refreshPackages()
                delay(5000)
            }
        }

        startCapture()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            val list = DeviceService.list()
            withContext(Dispatchers.Main) {
                _devices.value = list
                val current = _selectedDevice.value
                if (current != null && list.none { it.serial == current }) {
                    _selectedDevice.value = null
                    restartCapture(clearLogs = false)
                } else if (current == null && list.size == 1 && list[0].isOnline) {
                    _selectedDevice.value = list[0].serial
                    restartCapture(clearLogs = true)
                }
            }
        }
    }

    fun selectDevice(serial: String?) {
        if (_selectedDevice.value == serial) return
        _selectedDevice.value = serial
        restartCapture(clearLogs = true)
    }

    private fun restartCapture(clearLogs: Boolean) {
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _isCapturing.value = false
        if (clearLogs) {
            logs.clear()
            selectedIds.clear()
        }
        startCapture()
    }

    private fun applyConfig(config: UserConfig) {
        _visibleColumns.value = config.visibleColumns
        _columnWidths.value = config.columnWidths
        _appFilter.value = config.appFilter
        _themeMode.value = config.themeMode
        _fontSize.value = config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _fontFamily.value = config.fontFamily
        _fontWeight.value = config.fontWeight
        _softWrap.value = config.softWrap
    }

    private fun persistConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.save(
                UserConfig(
                    visibleColumns = _visibleColumns.value,
                    columnWidths = _columnWidths.value,
                    appFilter = _appFilter.value,
                    themeMode = _themeMode.value,
                    fontSize = _fontSize.value,
                    fontFamily = _fontFamily.value,
                    fontWeight = _fontWeight.value,
                    softWrap = _softWrap.value,
                )
            )
        }
    }

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (_fontSize.value == clamped) return
        _fontSize.value = clamped
        persistConfig()
    }

    fun setFontFamily(family: String) {
        if (_fontFamily.value == family) return
        _fontFamily.value = family
        persistConfig()
    }

    fun setFontWeight(weight: String) {
        if (_fontWeight.value == weight) return
        _fontWeight.value = weight
        persistConfig()
    }

    fun setSoftWrap(enabled: Boolean) {
        if (_softWrap.value == enabled) return
        _softWrap.value = enabled
        persistConfig()
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
            val newMap = PackageService.fetchPidMap(_selectedDevice.value)
            if (newMap.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                pidToPackage.clear()
                pidToPackage.putAll(newMap)
            }
        }
    }

    var selectionAnchor: Int? = null
        private set

    fun toggleSelection(id: Int, multiSelect: Boolean) {
        if (!multiSelect) {
            val wasSelected = selectedIds.contains(id)
            selectedIds.clear()
            if (!wasSelected) selectedIds.add(id)
        } else {
            if (!selectedIds.add(id)) selectedIds.remove(id)
        }
        selectionAnchor = id
    }

    fun selectRange(ids: Collection<Int>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
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

    fun setTheme(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        persistConfig()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startCapture() {
        if (_isCapturing.value) return
        _isCapturing.value = true
        val gen = ++captureGen

        captureJob = viewModelScope.launch {
            try {
                logcatService.stream(_selectedDevice.value).collect { line ->
                    withContext(Dispatchers.Main) {
                        logs.add(LogParser.parse(logs.size, line, pidToPackage))
                    }
                }
            } finally {
                if (gen == captureGen) _isCapturing.value = false
            }
        }
    }

    fun stopCapture() {
        captureGen++
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _isCapturing.value = false
    }

    fun toggleCapture() {
        if (_isCapturing.value) stopCapture() else startCapture()
    }

    fun clearLogs() {
        logs.clear()
        selectedIds.clear()
    }

    fun replaceFilters(newFilters: List<FilterRule>) {
        _filters.value = newFilters
        persistFilters()
    }

    fun addFilter(rule: FilterRule) {
        _filters.value = _filters.value + rule
        persistFilters()
    }

    fun removeFilter(id: String) {
        val current = _filters.value
        if (current.none { it.id == id }) return
        _filters.value = current.filterNot { it.id == id }
        persistFilters()
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        val current = _filters.value
        if (current.none { it.id == id && it.enabled != enabled }) return
        _filters.value = current.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistFilters()
    }

    private fun persistFilters() {
        val snapshot = _filters.value
        viewModelScope.launch(Dispatchers.IO) { filterStore.save(snapshot) }
    }

    override fun onCleared() {
        logcatService.stop()
        super.onCleared()
    }
}

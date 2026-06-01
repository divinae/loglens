package com.dv.logexp.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dv.logexp.config.ConfigManager
import com.dv.logexp.config.FilterStore
import com.dv.logexp.model.DEFAULT_COLUMN_WIDTHS
import com.dv.logexp.model.DEFAULT_FILTERS_LIST_WIDTH
import com.dv.logexp.model.DEFAULT_FONT_FAMILY
import com.dv.logexp.model.DEFAULT_FONT_SIZE
import com.dv.logexp.model.DEFAULT_FONT_WEIGHT
import com.dv.logexp.model.DEFAULT_VISIBLE_COLUMNS
import com.dv.logexp.model.Device
import com.dv.logexp.model.FilterMode
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

private const val FLUSH_INTERVAL_MS = 40L

class MainViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val filterStore: FilterStore = FilterStore(),
    private val logcatService: LogcatService = LogcatService(),
) : ViewModel() {

    val logs = mutableStateListOf<LogEntry>()

    /**
     * The filtered view the UI renders. Maintained incrementally: a full rebuild only runs when the
     * filter criteria change ([recomputeFiltered]); during capture each new batch is filtered alone and
     * appended, so we never re-scan the (unbounded) [logs] list on every flush.
     */
    val filteredLogs = mutableStateListOf<LogEntry>()

    val selectedIds = mutableStateSetOf<Int>()
    val bookmarkedIds = mutableStateSetOf<Int>()
    val pidToPackage = mutableStateMapOf<String, String>()

    // Cached partition of the active filters, refreshed whenever the filter set changes so per-log
    // matching doesn't re-scan/re-categorise the rule list.
    private var showFilters: List<FilterRule> = emptyList()
    private var hideFilters: List<FilterRule> = emptyList()

    // Bookmarks kept pre-sorted so next/prev navigation doesn't sort the whole set on each keypress.
    private var sortedBookmarks: List<Int> = emptyList()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

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

    private val _filtersListWidth = MutableStateFlow(DEFAULT_FILTERS_LIST_WIDTH)
    val filtersListWidth = _filtersListWidth.asStateFlow()

    private val _alwaysShowBookmarks = MutableStateFlow(true)
    val alwaysShowBookmarks = _alwaysShowBookmarks.asStateFlow()

    private val _quickFilterVisible = MutableStateFlow(true)
    val quickFilterVisible = _quickFilterVisible.asStateFlow()

    private val _filters = MutableStateFlow<List<FilterRule>>(emptyList())
    val filters = _filters.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<String?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private var captureJob: Job? = null
    private var captureGen: Long = 0

    /** Monotonic, thread-safe id source (parsing runs off the main thread; restarts may briefly overlap). */
    private val nextLogId = AtomicInteger(0)

    /** Plain snapshot of [pidToPackage] for the background parser, which must not read Compose state. */
    @Volatile
    private var pidSnapshot: Map<String, String> = emptyMap()

    init {
        applyConfig(configManager.load())
        _filters.value = filterStore.load()
        recomputeFiltered()

        viewModelScope.launch {
            while (isActive) {
                refreshDevices()
                refreshPackages()
                // Poll quickly until the pid→package map is known, then back off — otherwise
                // process-name filters stay empty for up to a full interval after start.
                delay(if (pidToPackage.isEmpty()) 1000 else 5000)
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
                    refreshPackages()
                }
            }
        }
    }

    fun selectDevice(serial: String?) {
        if (_selectedDevice.value == serial) return
        _selectedDevice.value = serial
        restartCapture(clearLogs = true)
        if (serial != null) refreshPackages()
    }

    /** Public entry for the Ctrl+R shortcut: clear logs and resume capturing. */
    fun restartCapture() = restartCapture(clearLogs = true)

    private fun restartCapture(clearLogs: Boolean) {
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _isCapturing.value = false
        if (clearLogs) {
            logs.clear()
            filteredLogs.clear()
            selectedIds.clear()
        }
        startCapture()
    }

    private fun applyConfig(config: UserConfig) {
        _visibleColumns.value = config.visibleColumns
        _columnWidths.value = config.columnWidths
        _themeMode.value = config.themeMode
        _fontSize.value = config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _fontFamily.value = config.fontFamily
        _fontWeight.value = config.fontWeight
        _softWrap.value = config.softWrap
        _filtersListWidth.value = config.filtersListWidth
        _alwaysShowBookmarks.value = config.alwaysShowBookmarks
        _quickFilterVisible.value = config.quickFilterVisible
    }

    private fun persistConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.save(
                UserConfig(
                    visibleColumns = _visibleColumns.value,
                    columnWidths = _columnWidths.value,
                    themeMode = _themeMode.value,
                    fontSize = _fontSize.value,
                    fontFamily = _fontFamily.value,
                    fontWeight = _fontWeight.value,
                    softWrap = _softWrap.value,
                    filtersListWidth = _filtersListWidth.value,
                    alwaysShowBookmarks = _alwaysShowBookmarks.value,
                    quickFilterVisible = _quickFilterVisible.value,
                )
            )
        }
    }

    fun setFiltersListWidth(width: Float) {
        if (_filtersListWidth.value == width) return
        _filtersListWidth.value = width
        persistConfig()
    }

    fun setAlwaysShowBookmarks(value: Boolean) {
        if (_alwaysShowBookmarks.value == value) return
        _alwaysShowBookmarks.value = value
        recomputeFiltered()
        persistConfig()
    }

    fun setQuickFilterVisible(value: Boolean) {
        if (_quickFilterVisible.value == value) return
        _quickFilterVisible.value = value
        persistConfig()
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

    fun findNextBookmark(currentId: Int): Int? =
        sortedBookmarks.firstOrNull { it > currentId } ?: sortedBookmarks.firstOrNull()

    fun findPreviousBookmark(currentId: Int): Int? =
        sortedBookmarks.lastOrNull { it < currentId } ?: sortedBookmarks.lastOrNull()

    fun toggleBookmark(id: Int) {
        if (!bookmarkedIds.add(id)) bookmarkedIds.remove(id)
        sortedBookmarks = bookmarkedIds.sorted()
        // When bookmarks are pinned past the filters, toggling changes which rows are shown.
        if (_alwaysShowBookmarks.value) recomputeFiltered()
    }

    fun setAutoFollow(enabled: Boolean) {
        _autoFollow.value = enabled
    }

    fun toggleAutoFollow() {
        _autoFollow.value = !_autoFollow.value
    }

    fun refreshPackages() {
        viewModelScope.launch {
            val newMap = PackageService.fetchPidMap(_selectedDevice.value)
            if (newMap.isEmpty() || newMap == pidToPackage) return@launch
            withContext(Dispatchers.Main) {
                pidToPackage.clear()
                pidToPackage.putAll(newMap)
                pidSnapshot = newMap
                // Backfill app names for logs captured before this pid→package mapping was known,
                // otherwise process-name filters silently miss those early logs.
                var changed = false
                for (i in logs.indices) {
                    val entry = logs[i]
                    if (entry.appName.isEmpty()) {
                        val name = newMap[entry.pid]
                        if (!name.isNullOrEmpty()) {
                            logs[i] = entry.copy(appName = name)
                            changed = true
                        }
                    }
                }
                // Backfilled app names can change filter matches, so rebuild the filtered view.
                if (changed) recomputeFiltered()
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
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        recomputeFiltered()
    }

    /** True if [log] should appear in [filteredLogs] under the current criteria. */
    private fun matches(log: LogEntry): Boolean {
        if (_alwaysShowBookmarks.value && log.id in bookmarkedIds) return true
        val query = _searchQuery.value
        if (query.isNotBlank() && !log.content.contains(query, ignoreCase = true)) return false
        if (hideFilters.any { it.matches(log) }) return false
        if (showFilters.isNotEmpty() && showFilters.none { it.matches(log) }) return false
        return true
    }

    /** Refresh the cached active-filter partition; call before any (re)filtering pass. */
    private fun refreshFilterPartition() {
        val active = _filters.value.filter { it.enabled }
        showFilters = active.filter {
            it.mode == FilterMode.SHOW || it.mode == FilterMode.SHOW_AND_HIGHLIGHT
        }
        hideFilters = active.filter { it.mode == FilterMode.HIDE }
    }

    /** Full rebuild of [filteredLogs] from [logs]. Use only when criteria change, not per batch. */
    private fun recomputeFiltered() {
        refreshFilterPartition()
        val rebuilt = logs.filter { matches(it) }
        filteredLogs.clear()
        filteredLogs.addAll(rebuilt)
    }

    fun startCapture() {
        if (_isCapturing.value) return
        _isCapturing.value = true
        val gen = ++captureGen
        val channel = Channel<LogEntry>(Channel.UNLIMITED)

        // Producer: read and parse lines off the main thread.
        val producer = viewModelScope.launch(Dispatchers.Default) {
            try {
                logcatService.stream(_selectedDevice.value).collect { line ->
                    channel.send(LogParser.parse(nextLogId.getAndIncrement(), line, pidSnapshot))
                }
            } finally {
                channel.close()
            }
        }

        // Consumer: batch-flush parsed entries to the observable list on the main thread, so a large
        // backlog collapses into a few list updates per second instead of one recomposition per line.
        captureJob = viewModelScope.launch {
            try {
                while (isActive) {
                    val first = channel.receiveCatching().getOrNull() ?: break
                    val batch = ArrayList<LogEntry>()
                    batch.add(first)
                    while (true) {
                        batch.add(channel.tryReceive().getOrNull() ?: break)
                    }
                    if (gen == captureGen) {
                        logs.addAll(batch)
                        // Filter only the new batch and append — no full re-scan of the growing list.
                        val matched = batch.filter { matches(it) }
                        if (matched.isNotEmpty()) filteredLogs.addAll(matched)
                    }
                    delay(FLUSH_INTERVAL_MS)
                }
            } finally {
                producer.cancel()
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
        filteredLogs.clear()
        selectedIds.clear()
    }

    fun replaceFilters(newFilters: List<FilterRule>) {
        _filters.value = newFilters
        recomputeFiltered()
        persistFilters()
    }

    fun addFilter(rule: FilterRule) {
        _filters.value = _filters.value + rule
        recomputeFiltered()
        persistFilters()
    }

    fun removeFilter(id: String) {
        val current = _filters.value
        if (current.none { it.id == id }) return
        _filters.value = current.filterNot { it.id == id }
        recomputeFiltered()
        persistFilters()
    }

    fun reorderFilters(from: Int, to: Int) {
        val current = _filters.value
        if (from == to || from !in current.indices || to !in current.indices) return
        _filters.value = current.toMutableList().apply { add(to, removeAt(from)) }
        // Order can change which SHOW/HIDE rule wins, so the visible set may shift.
        recomputeFiltered()
        persistFilters()
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        val current = _filters.value
        if (current.none { it.id == id && it.enabled != enabled }) return
        _filters.value = current.map { if (it.id == id) it.copy(enabled = enabled) else it }
        recomputeFiltered()
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

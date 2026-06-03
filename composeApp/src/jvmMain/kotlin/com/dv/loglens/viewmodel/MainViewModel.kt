package com.dv.loglens.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dv.loglens.config.ConfigManager
import com.dv.loglens.config.FilterStore
import com.dv.loglens.config.SearchHistoryStore
import com.dv.loglens.model.DEFAULT_COLUMN_WIDTHS
import com.dv.loglens.model.DEFAULT_FILTERS_LIST_WIDTH
import com.dv.loglens.model.DEFAULT_FONT_FAMILY
import com.dv.loglens.model.DEFAULT_FONT_SIZE
import com.dv.loglens.model.DEFAULT_FONT_WEIGHT
import com.dv.loglens.model.DEFAULT_LOG_BUFFERS
import com.dv.loglens.model.DEFAULT_VISIBLE_COLUMNS
import com.dv.loglens.model.Device
import com.dv.loglens.model.FilterMode
import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.LogEntry
import com.dv.loglens.model.LogQuery
import com.dv.loglens.model.MAX_FONT_SIZE
import com.dv.loglens.model.MIN_FONT_SIZE
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.model.UserConfig
import com.dv.loglens.model.WindowBounds
import com.dv.loglens.parser.AndroidStudioLogParser
import com.dv.loglens.parser.BugreportParser
import com.dv.loglens.parser.CrashDetector
import com.dv.loglens.parser.LogParser
import com.dv.loglens.service.Adb
import com.dv.loglens.service.DeviceService
import com.dv.loglens.service.LogcatService
import com.dv.loglens.service.PackageService
import com.dv.loglens.service.ScreenRecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

private const val FLUSH_INTERVAL_MS = 40L

private const val SEARCH_HISTORY_LIMIT = 30

// Cap for slurping a whole file into memory to test/parse as JSON; larger files go line-by-line.
private const val MAX_JSON_LOG_BYTES = 256L * 1024 * 1024
private const val MAX_RECENT_FILES = 10

class MainViewModel(
    private val configManager: ConfigManager = ConfigManager(),
    private val filterStore: FilterStore = FilterStore(),
    private val searchHistoryStore: SearchHistoryStore = SearchHistoryStore(),
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

    /** Ids of entries detected as crash/ANR; drives the row marker. */
    val crashIds = mutableStateSetOf<Int>()

    /**
     * Crash ids currently present in [filteredLogs], in log order; the navigator badge and
     * next/prev jumps use this so filtered-out crashes are skipped. Rebuilt alongside
     * [recomputeFiltered] and maintained incrementally during capture.
     */
    var visibleCrashes by mutableStateOf<List<Int>>(emptyList())
        private set

    /**
     * Ids of multiline entries the user toggled away from the default fold state (UI state, not
     * persisted): expanded ids when the default is collapsed, collapsed ids when
     * [expandMultilineByDefault] is on.
     */
    val expandedIds = mutableStateSetOf<Int>()

    // Cached partition of the active filters, refreshed whenever the filter set changes so per-log
    // matching doesn't re-scan/re-categorise the rule list.
    private var showFilters: List<FilterRule> = emptyList()
    private var hideFilters: List<FilterRule> = emptyList()

    // Bookmarks kept pre-sorted so next/prev navigation doesn't sort the whole set on each keypress.
    private var sortedBookmarks: List<Int> = emptyList()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    /**
     * Display pause: the adb stream keeps running and lines keep buffering in the capture channel,
     * only the UI stops appending. Resuming flushes the backlog, so nothing is lost — unlike
     * stop/start, which re-reads the device ring buffer.
     */
    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    /** Non-null when the current query has a syntax/regex error (UI shows an error outline). */
    private val _searchQueryError = MutableStateFlow<String?>(null)
    val searchQueryError = _searchQueryError.asStateFlow()

    /** Parsed form of [_searchQuery]; rebuilt only when the text changes, not per log line. */
    @Volatile
    private var parsedQuery: LogQuery = LogQuery.EMPTY

    /** Recently committed quick-filter queries, most-recent first. */
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    /** Directory device screenshots are saved to; blank means the default (~/Pictures/android). */
    private val _screenshotDir = MutableStateFlow("")
    val screenshotDir = _screenshotDir.asStateFlow()

    /** Whether to open the screenshot in the default image viewer after capture. */
    private val _openScreenshotAfterCapture = MutableStateFlow(true)
    val openScreenshotAfterCapture = _openScreenshotAfterCapture.asStateFlow()

    /** Path to the adb executable; blank means "adb" from PATH. */
    private val _adbPath = MutableStateFlow("")
    val adbPath = _adbPath.asStateFlow()

    /** Directory device screen recordings are saved to; blank means the default (~/Videos/android). */
    private val _recordingDir = MutableStateFlow("")
    val recordingDir = _recordingDir.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    /** Recording options (parity with Android Studio's recorder). */
    private val _recordingShowTaps = MutableStateFlow(false)
    val recordingShowTaps = _recordingShowTaps.asStateFlow()
    private val _recordingBitrateMbps = MutableStateFlow(0)
    val recordingBitrateMbps = _recordingBitrateMbps.asStateFlow()
    private val _recordingSize = MutableStateFlow("")
    val recordingSize = _recordingSize.asStateFlow()

    /** Emits the saved file when a recording finishes successfully, so the UI can open it. */
    private val _recordingSaved = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val recordingSaved = _recordingSaved.asSharedFlow()

    @Volatile
    private var recordingProcess: Process? = null
    private var recordingJob: Job? = null

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

    private val _expandMultilineByDefault = MutableStateFlow(false)
    val expandMultilineByDefault = _expandMultilineByDefault.asStateFlow()

    private val _filtersListWidth = MutableStateFlow(DEFAULT_FILTERS_LIST_WIDTH)
    val filtersListWidth = _filtersListWidth.asStateFlow()

    private val _alwaysShowBookmarks = MutableStateFlow(true)
    val alwaysShowBookmarks = _alwaysShowBookmarks.asStateFlow()

    private val _quickFilterVisible = MutableStateFlow(true)
    val quickFilterVisible = _quickFilterVisible.asStateFlow()

    private val _logBuffers = MutableStateFlow(DEFAULT_LOG_BUFFERS)
    val logBuffers = _logBuffers.asStateFlow()

    private val _filters = MutableStateFlow<List<FilterRule>>(emptyList())
    val filters = _filters.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<String?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    /** Name of the file currently loaded as the log source, or null when reading a live device. */
    private val _loadedFile = MutableStateFlow<String?>(null)
    val loadedFile = _loadedFile.asStateFlow()

    /** Absolute paths of recently opened log files, most-recent first. */
    private val _recentFiles = MutableStateFlow<List<String>>(emptyList())
    val recentFiles = _recentFiles.asStateFlow()

    private var captureJob: Job? = null
    private var captureGen: Long = 0

    /** Monotonic, thread-safe id source (parsing runs off the main thread; restarts may briefly overlap). */
    private val nextLogId = AtomicInteger(0)

    /** Plain snapshot of [pidToPackage] for the background parser, which must not read Compose state. */
    @Volatile
    private var pidSnapshot: Map<String, String> = emptyMap()

    /** Pids that produced at least one visible log entry; gates process-death banners to spam-free. */
    private val pidsSeenInLogs = HashSet<String>()

    /** Package names that produced log entries, for process-start banners after a restart. */
    private val packagesSeenInLogs = HashSet<String>()

    /** `date time` of the newest parsed entry; reconnect resumes the stream from here (logcat -T). */
    @Volatile
    private var lastEntryTimestamp: String? = null

    /** Serial of the last live capture source, so a reappearing device resumes automatically. */
    private var lastLiveSerial: String? = null

    /** One-shot logcat -T value consumed by the next [startCapture] (set on auto-reconnect). */
    private var resumeSinceTimestamp: String? = null

    init {
        applyConfig(configManager.load())
        _filters.value = filterStore.load()
        _searchHistory.value = searchHistoryStore.load()
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

        // Catch every exit path (window close, Ctrl+C/SIGTERM, JVM shutdown): record the query in
        // history and persist it so it's restored into the quick filter on next launch.
        Runtime.getRuntime().addShutdownHook(
            Thread {
                commitSearchQuery()
                configManager.save(currentConfig())
            }
        )
    }

    fun refreshDevices() {
        viewModelScope.launch {
            val list = DeviceService.list()
            withContext(Dispatchers.Main) {
                _devices.value = list
                val current = _selectedDevice.value
                if (current != null && list.none { it.serial == current }) {
                    // Device dropped: remember it for auto-reconnect and stop the dead stream
                    // instead of restarting against no device (which only spews adb errors).
                    lastLiveSerial = current
                    _selectedDevice.value = null
                    stopCapture()
                    appendSystemBanner("DEVICE DISCONNECTED ($current)")
                } else if (current == null) {
                    // Prefer the device we lost; otherwise auto-pick a sole online device.
                    val returned = list.firstOrNull { it.serial == lastLiveSerial && it.isOnline }
                    val sole = list.singleOrNull()?.takeIf { it.isOnline }
                    val pick = returned ?: sole ?: return@withContext
                    val isReconnect = pick.serial == lastLiveSerial
                    _selectedDevice.value = pick.serial
                    if (isReconnect) {
                        // Resume from the last seen line so the kept logs aren't duplicated and
                        // lines buffered on the device while unplugged still arrive.
                        appendSystemBanner("DEVICE RECONNECTED (${pick.serial})")
                        resumeSinceTimestamp = lastEntryTimestamp
                        restartCapture(clearLogs = false)
                    } else {
                        restartCapture(clearLogs = true)
                    }
                    refreshPackages()
                }
            }
        }
    }

    /**
     * Append a synthetic separator-style entry (device/process lifecycle banner) to the log and,
     * filter permitting, the visible view. Live capture only — banners make no sense in a file.
     */
    private fun appendSystemBanner(text: String) {
        if (_loadedFile.value != null) return
        val content = "---------------------------- $text ----------------------------"
        val entry = LogEntry(
            id = nextLogId.getAndIncrement(),
            content = content,
            message = content,
            level = "I",
        )
        logs.add(entry)
        if (matches(entry)) filteredLogs.add(entry)
    }

    fun selectDevice(serial: String?) {
        // Re-selecting the same device must still switch back to live when a file is loaded.
        if (_selectedDevice.value == serial && _loadedFile.value == null) return
        _selectedDevice.value = serial
        restartCapture(clearLogs = true)
        if (serial != null) refreshPackages()
    }

    private fun restartCapture(clearLogs: Boolean) {
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _isCapturing.value = false
        if (clearLogs) {
            logs.clear()
            filteredLogs.clear()
            selectedIds.clear()
            clearDerivedState()
            nextLogId.set(0)
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
        _expandMultilineByDefault.value = config.expandMultilineByDefault
        _filtersListWidth.value = config.filtersListWidth
        _alwaysShowBookmarks.value = config.alwaysShowBookmarks
        _quickFilterVisible.value = config.quickFilterVisible
        _logBuffers.value = config.logBuffers
        _searchQuery.value = config.searchQuery
        parsedQuery = LogQuery.parse(config.searchQuery)
        _searchQueryError.value = parsedQuery.error
        _screenshotDir.value = config.screenshotDir
        _openScreenshotAfterCapture.value = config.openScreenshotAfterCapture
        _adbPath.value = config.adbPath
        Adb.path = config.adbPath
        _recordingDir.value = config.recordingDir
        _recordingShowTaps.value = config.recordingShowTaps
        _recordingBitrateMbps.value = config.recordingBitrateMbps
        _recordingSize.value = config.recordingSize
        windowBounds = WindowBounds(config.windowX, config.windowY, config.windowWidth, config.windowHeight)
        _recentFiles.value = config.recentFiles
    }

    private fun currentConfig(): UserConfig = UserConfig(
        visibleColumns = _visibleColumns.value,
        columnWidths = _columnWidths.value,
        themeMode = _themeMode.value,
        fontSize = _fontSize.value,
        fontFamily = _fontFamily.value,
        fontWeight = _fontWeight.value,
        softWrap = _softWrap.value,
        expandMultilineByDefault = _expandMultilineByDefault.value,
        filtersListWidth = _filtersListWidth.value,
        alwaysShowBookmarks = _alwaysShowBookmarks.value,
        quickFilterVisible = _quickFilterVisible.value,
        logBuffers = _logBuffers.value,
        searchQuery = _searchQuery.value,
        screenshotDir = _screenshotDir.value,
        openScreenshotAfterCapture = _openScreenshotAfterCapture.value,
        adbPath = _adbPath.value,
        recordingDir = _recordingDir.value,
        recordingShowTaps = _recordingShowTaps.value,
        recordingBitrateMbps = _recordingBitrateMbps.value,
        recordingSize = _recordingSize.value,
        recentFiles = _recentFiles.value,
        windowX = windowBounds.x,
        windowY = windowBounds.y,
        windowWidth = windowBounds.width,
        windowHeight = windowBounds.height,
    )

    /** Last-known window geometry (dp). Read once at startup; written on resize/move and close. */
    var windowBounds: WindowBounds = WindowBounds()
        private set

    fun saveWindowBounds(x: Float, y: Float, width: Float, height: Float) {
        val next = WindowBounds(x, y, width, height)
        if (next == windowBounds) return
        windowBounds = next
        persistConfig()
    }

    private fun persistConfig() {
        viewModelScope.launch(Dispatchers.IO) { configManager.save(currentConfig()) }
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

    fun setScreenshotDir(path: String) {
        if (_screenshotDir.value == path) return
        _screenshotDir.value = path
        persistConfig()
    }

    fun setOpenScreenshotAfterCapture(value: Boolean) {
        if (_openScreenshotAfterCapture.value == value) return
        _openScreenshotAfterCapture.value = value
        persistConfig()
    }

    fun setAdbPath(path: String) {
        if (_adbPath.value == path) return
        _adbPath.value = path
        Adb.path = path
        persistConfig()
        // The 5s device-poll loop picks up the new binary on its own; no forced restart here so
        // typing the path char-by-char doesn't spawn a capture restart per keystroke.
    }

    fun setRecordingDir(path: String) {
        if (_recordingDir.value == path) return
        _recordingDir.value = path
        persistConfig()
    }

    fun setRecordingShowTaps(value: Boolean) {
        if (_recordingShowTaps.value == value) return
        _recordingShowTaps.value = value
        persistConfig()
    }

    fun setRecordingBitrateMbps(value: Int) {
        if (_recordingBitrateMbps.value == value) return
        _recordingBitrateMbps.value = value
        persistConfig()
    }

    fun setRecordingSize(value: String) {
        if (_recordingSize.value == value) return
        _recordingSize.value = value
        persistConfig()
    }

    fun toggleRecording() {
        if (_isRecording.value) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val serial = _selectedDevice.value
        if (_isRecording.value || serial == null || _loadedFile.value != null) return
        _isRecording.value = true
        // No ':' — illegal in sdcard (FAT/sdcardfs) filenames, screenrecord would fail to create the file.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val devicePath = "/sdcard/loglens-rec-$stamp.mp4"
        val dir = File(_recordingDir.value.ifBlank { "${System.getProperty("user.home")}/Videos/android" })
        val target = File(dir, "rec_$stamp.mp4")
        val showTaps = _recordingShowTaps.value
        val bitrate = _recordingBitrateMbps.value
        val size = _recordingSize.value
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            var priorTaps: String? = null
            try {
                if (showTaps) {
                    priorTaps = ScreenRecordingService.getShowTouches(serial)
                    ScreenRecordingService.setShowTouches(serial, true)
                }
                val proc = ScreenRecordingService.start(serial, devicePath, bitrate, size)
                recordingProcess = proc
                // Drain the merged output (blocks until the process ends) so the pipe never stalls
                // and any screenrecord error surfaces.
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (output.isNotBlank()) System.err.println("loglens: screenrecord: ${output.trim()}")
                // Process ended (manual stop or the ~180s screenrecord limit) — fetch the file.
                if (ScreenRecordingService.pullAndCleanup(serial, devicePath, target)) {
                    _recordingSaved.emit(target)
                }
            } catch (e: Exception) {
                System.err.println("loglens: recording failed: ${e.message}")
            } finally {
                // Restore the device's prior show-touches setting (don't clobber the user's choice).
                if (showTaps) runCatching { ScreenRecordingService.setShowTouches(serial, priorTaps == "1") }
                recordingProcess = null
                _isRecording.value = false
            }
        }
    }

    private fun stopRecording() {
        val serial = _selectedDevice.value
        // SIGINT the on-device process so the mp4 is finalized; destroy() is only a backstop.
        viewModelScope.launch(Dispatchers.IO) { ScreenRecordingService.signalStop(serial) }
        recordingProcess?.destroy()
    }

    /** Toggle one adb buffer. Refuses to clear the last one, then restarts capture from the new source. */
    fun toggleLogBuffer(buffer: LogBuffer) {
        val current = _logBuffers.value
        val next = if (buffer in current) current - buffer else current + buffer
        if (next.isEmpty() || next == current) return
        _logBuffers.value = next
        persistConfig()
        restartCapture(clearLogs = true)
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

    /** 0-based position of [id] among bookmarks in log order, or -1 if it isn't one. */
    fun bookmarkOrdinal(id: Int): Int = sortedBookmarks.indexOf(id)

    /** 0-based position of [id] among visible crashes in log order, or -1 if it isn't one. */
    fun crashOrdinal(id: Int): Int = visibleCrashes.indexOf(id)

    fun findNextCrash(currentId: Int): Int? =
        visibleCrashes.firstOrNull { it > currentId } ?: visibleCrashes.firstOrNull()

    fun findPreviousCrash(currentId: Int): Int? =
        visibleCrashes.lastOrNull { it < currentId } ?: visibleCrashes.lastOrNull()

    // Capture appends in id order, so appending keeps [visibleCrashes] sorted.
    private fun registerCrash(id: Int, visible: Boolean) {
        if (crashIds.add(id) && visible) visibleCrashes = visibleCrashes + id
    }

    fun toggleExpand(id: Int) {
        if (!expandedIds.add(id)) expandedIds.remove(id)
    }

    /** Force [id] expanded regardless of the default — used when navigation lands on a folded match. */
    fun ensureExpanded(id: Int) {
        if (_expandMultilineByDefault.value) expandedIds.remove(id) else expandedIds.add(id)
    }

    fun setExpandMultilineByDefault(value: Boolean) {
        if (_expandMultilineByDefault.value == value) return
        _expandMultilineByDefault.value = value
        // Per-row toggles were relative to the old default; reset so every row adopts the new one.
        expandedIds.clear()
        persistConfig()
    }

    /** Drop id-keyed derived state when the log set is reset; ids are reused after [nextLogId] resets. */
    private fun clearDerivedState() {
        crashIds.clear()
        visibleCrashes = emptyList()
        expandedIds.clear()
        pidsSeenInLogs.clear()
        packagesSeenInLogs.clear()
        lastEntryTimestamp = null
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
                emitProcessBanners(oldMap = pidSnapshot, newMap = newMap)
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

    /**
     * Android-Studio-style process lifecycle banners, derived from pid→package poll diffs. Scoped
     * to processes that actually appeared in this session's logs ([pidsSeenInLogs] /
     * [packagesSeenInLogs]) — unrelated system process churn stays silent.
     */
    private fun emitProcessBanners(oldMap: Map<String, String>, newMap: Map<String, String>) {
        if (!_isCapturing.value || oldMap.isEmpty()) return
        for ((pid, pkg) in oldMap) {
            if (pid !in newMap && pid in pidsSeenInLogs) {
                appendSystemBanner("PROCESS ENDED ($pid) for package $pkg")
            }
        }
        for ((pid, pkg) in newMap) {
            if (pid !in oldMap && pkg in packagesSeenInLogs) {
                appendSystemBanner("PROCESS STARTED ($pid) for package $pkg")
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

    fun clearSelection() {
        selectedIds.clear()
        selectionAnchor = null
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
        parsedQuery = LogQuery.parse(query)
        _searchQueryError.value = parsedQuery.error
        recomputeFiltered()
    }

    /**
     * Record the current query in history. Skips blanks and queries that failed to parse.
     * Saved synchronously (history is tiny) so it survives an immediate app exit.
     */
    fun commitSearchQuery() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty() || parsedQuery.error != null) return
        // Move an existing identical entry to the front; cap the list.
        val updated = (listOf(query) + _searchHistory.value.filterNot { it == query }).take(SEARCH_HISTORY_LIMIT)
        if (updated == _searchHistory.value) return
        _searchHistory.value = updated
        searchHistoryStore.save(updated)
    }

    fun clearSearchHistory() {
        if (_searchHistory.value.isEmpty()) return
        _searchHistory.value = emptyList()
        searchHistoryStore.save(emptyList())
    }

    /** True if [log] should appear in [filteredLogs] under the current criteria. */
    private fun matches(log: LogEntry): Boolean {
        if (_alwaysShowBookmarks.value && log.id in bookmarkedIds) return true
        if (!parsedQuery.matches(log)) return false
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
        // Drop selection on rows the new criteria hide — otherwise shortcuts (B, copy, ↑/↓ anchor)
        // keep acting on lines the user can no longer see.
        if (selectedIds.isNotEmpty()) {
            val visible = rebuilt.mapTo(HashSet()) { it.id }
            selectedIds.retainAll(visible)
        }
        visibleCrashes =
            if (crashIds.isEmpty()) emptyList()
            else rebuilt.mapNotNull { log -> log.id.takeIf { it in crashIds } }
    }

    fun startCapture() {
        if (_isCapturing.value) return
        // Going live: we are no longer reading from a file.
        _loadedFile.value = null
        _isCapturing.value = true
        _isPaused.value = false
        _selectedDevice.value?.let { lastLiveSerial = it }
        // One-shot resume point from an auto-reconnect; a normal start reads the whole buffer.
        val sinceTime = resumeSinceTimestamp
        resumeSinceTimestamp = null
        val gen = ++captureGen
        val channel = Channel<CapturedLine>(Channel.UNLIMITED)

        // Producer: read and detect record headers off the main thread. A null header marks a
        // continuation line (stack frame) the consumer folds into the preceding entry.
        val producer = viewModelScope.launch(Dispatchers.Default) {
            try {
                logcatService.stream(_selectedDevice.value, _logBuffers.value, sinceTime).collect { line ->
                    channel.send(CapturedLine(line, LogParser.parseLine(line, pidSnapshot)))
                }
            } finally {
                channel.close()
            }
        }

        // Consumer: batch-flush parsed entries to the observable list on the main thread, so a large
        // backlog collapses into a few list updates per second instead of one recomposition per line.
        // Continuation lines are folded onto the current record in place (O(1)): the stream is ordered
        // and nothing appends between a header and its continuations, so the record stays at the tail
        // index we recorded. State persists across batches because a trace can span a flush boundary.
        captureJob = viewModelScope.launch {
            var foldCurrent: LogEntry? = null
            var foldLogsIdx = -1
            var foldFilteredIdx = -1
            try {
                while (isActive) {
                    // Display pause: stop draining; the unbounded channel keeps buffering the
                    // stream, so resuming flushes everything that arrived in the meantime.
                    if (_isPaused.value) {
                        delay(FLUSH_INTERVAL_MS)
                        continue
                    }
                    val first = channel.receiveCatching().getOrNull() ?: break
                    val batch = ArrayList<CapturedLine>()
                    batch.add(first)
                    while (true) {
                        batch.add(channel.tryReceive().getOrNull() ?: break)
                    }
                    if (gen == captureGen) {
                        for (cap in batch) {
                            val header = cap.header
                            if (header != null && header.time.isNotBlank()) {
                                // Reconnect resume point + process-banner bookkeeping.
                                lastEntryTimestamp = "${header.date} ${header.time}"
                                if (header.pid.isNotBlank()) pidsSeenInLogs.add(header.pid)
                                if (header.appName.isNotBlank()) packagesSeenInLogs.add(header.appName)
                            }
                            if (foldCurrent != null && LogParser.shouldFold(foldCurrent, header, cap.raw)) {
                                // Continuation (or another line of the same crash dump): grow the
                                // current record in place. Re-prefixed crash lines contribute only
                                // their parsed message, not the repeated `E AndroidRuntime:` prefix.
                                val merged = LogParser.foldContinuation(foldCurrent, cap.raw, header?.message ?: cap.raw)
                                logs[foldLogsIdx] = merged
                                if (foldFilteredIdx >= 0) {
                                    filteredLogs[foldFilteredIdx] = merged
                                } else if (matches(merged)) {
                                    // The grown message now matches a filter it didn't before.
                                    filteredLogs.add(merged)
                                    foldFilteredIdx = filteredLogs.lastIndex
                                    // A previously hidden crash just became visible.
                                    if (merged.id in crashIds) visibleCrashes = visibleCrashes + merged.id
                                }
                                foldCurrent = merged
                            } else {
                                val isSep = LogParser.isSeparator(cap.raw)
                                val prev = if (foldLogsIdx >= 0) logs[foldLogsIdx] else null
                                // Recognised header, or an orphan continuation with no current record
                                // → standalone D entry, as in LogParser.parseLog. id assigned only if
                                // we actually add it (a dedup bump consumes no id).
                                val candidate = header
                                    ?: LogEntry(id = 0, content = cap.raw, message = cap.raw, level = "D")
                                if (prev != null && !isSep && candidate.sameLogAs(prev)) {
                                    // Same log again back-to-back: bump the count in place, no new row.
                                    val bumped = prev.copy(repeatCount = prev.repeatCount + 1)
                                    logs[foldLogsIdx] = bumped
                                    if (foldFilteredIdx >= 0) filteredLogs[foldFilteredIdx] = bumped
                                    foldCurrent = if (header != null) bumped else null
                                } else {
                                    val entry = candidate.copy(id = nextLogId.getAndIncrement())
                                    logs.add(entry)
                                    foldLogsIdx = logs.lastIndex
                                    if (matches(entry)) {
                                        filteredLogs.add(entry)
                                        foldFilteredIdx = filteredLogs.lastIndex
                                    } else {
                                        foldFilteredIdx = -1
                                    }
                                    if (CrashDetector.isCrash(entry)) {
                                        registerCrash(entry.id, visible = foldFilteredIdx >= 0)
                                    }
                                    // A separator collects no continuations, so trailing text isn't
                                    // glued onto an unrelated record.
                                    foldCurrent = if (header != null && !isSep) entry else null
                                }
                            }
                        }
                    }
                    delay(FLUSH_INTERVAL_MS)
                }
            } finally {
                producer.cancel()
                if (gen == captureGen) _isCapturing.value = false
            }
        }
    }

    /** A captured line and its parsed header ([LogParser.parseLine]), or null header for a continuation. */
    private class CapturedLine(val raw: String, val header: LogEntry?)

    fun stopCapture() {
        captureGen++
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _isCapturing.value = false
        _isPaused.value = false
    }

    fun toggleCapture() {
        if (_isCapturing.value) stopCapture() else startCapture()
    }

    /** Pause/resume the display while the stream keeps buffering; see [isPaused]. */
    fun togglePause() {
        if (_isCapturing.value) _isPaused.value = !_isPaused.value
    }

    fun clearLogs() {
        logs.clear()
        filteredLogs.clear()
        selectedIds.clear()
        clearDerivedState()
        // Restart the Index column from 0; new entries get fresh ids.
        nextLogId.set(0)
        // For a live device, also wipe the on-device logcat buffer so cleared lines never reappear.
        // `logcat -c` kills a concurrently running logcat reader (EOF), which would end the capture,
        // so stop the stream first, clear, then resume.
        if (_loadedFile.value == null && _selectedDevice.value != null) {
            val serial = _selectedDevice.value
            val buffers = _logBuffers.value
            val wasCapturing = _isCapturing.value
            if (wasCapturing) stopCapture()
            viewModelScope.launch {
                logcatService.clearBuffers(serial, buffers)
                if (wasCapturing) startCapture()
            }
        }
    }

    /** Stop live capture and load logs from a saved file, parsing each line as logcat output. */
    fun loadLogsFromFile(file: File) {
        stopCapture()
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                // adb bugreports ship as a zip — pull the bugreport-*.txt out first. Otherwise slurp
                // the file (Studio JSON / flat bugreports need the whole text), unless it's huge, in
                // which case we stream the lines.
                val text = BugreportParser.extractFromZip(file)
                    ?: if (file.length() < MAX_JSON_LOG_BYTES) file.readText() else null
                val structured = when {
                    text == null -> null
                    AndroidStudioLogParser.matches(text) -> AndroidStudioLogParser.parse(text)
                    BugreportParser.matches(text) -> BugreportParser.parse(text)
                    else -> null
                }
                structured
                    ?: text?.let { LogParser.parseLog(it.lineSequence()) }
                    ?: file.useLines { lines -> LogParser.parseLog(lines) }
            }
            logs.clear()
            filteredLogs.clear()
            selectedIds.clear()
            clearDerivedState()
            logs.addAll(parsed)
            nextLogId.set(parsed.size)
            // visibleCrashes is derived from these in recomputeFiltered() below.
            parsed.forEach { if (CrashDetector.isCrash(it)) crashIds.add(it.id) }
            _autoFollow.value = false
            _loadedFile.value = file.name
            recomputeFiltered()
            rememberRecentFile(file)
        }
    }

    /** Open a previously loaded file by path; if it's gone, drop it from the recent list. */
    fun openRecentFile(path: String) {
        val file = File(path)
        if (file.exists()) loadLogsFromFile(file)
        else _recentFiles.value = _recentFiles.value.filterNot { it == path }.also { persistConfig() }
    }

    fun clearRecentFiles() {
        if (_recentFiles.value.isEmpty()) return
        _recentFiles.value = emptyList()
        persistConfig()
    }

    private fun rememberRecentFile(file: File) {
        val path = file.absolutePath
        // Most-recent first, de-duplicated, capped so the list (and config) stays small.
        _recentFiles.value = (listOf(path) + _recentFiles.value.filterNot { it == path }).take(MAX_RECENT_FILES)
        persistConfig()
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

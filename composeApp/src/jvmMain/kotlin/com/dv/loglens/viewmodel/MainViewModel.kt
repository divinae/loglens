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
import com.dv.loglens.service.ProcessTrackerService
import com.dv.loglens.service.ScreenRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private const val FLUSH_INTERVAL_MS = 40L

/**
 * Max captured lines processed per drain cycle. The capture loop runs on the main dispatcher and
 * processing costs ~15µs/line (parse-fold + filter match + crash detect + snapshot writes), so a big
 * chunk blocks the UI thread well past a 16ms frame and makes scrolling stutter. Sized so one chunk
 * stays under a frame; on a backlog the loop yields a frame between chunks, so the UI keeps painting
 * while catch-up continues. The rest stays buffered in the (unbounded) channel — nothing is dropped.
 */
private const val MAX_DRAIN_PER_FLUSH = 350

// Polling for a just-started process's name: `ps` reports the zygote fork's final name only
// after bindApplication renames it, typically well within a second.
private const val NAME_RESOLVE_ATTEMPTS = 10
private const val NAME_RESOLVE_DELAY_MS = 200L

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

    /**
     * Bumped on every [recomputeFiltered] rebuild. The UI watches this to tell a filter-driven
     * scroll reset (clear + refill drops the scroll position) apart from a genuine user scroll-up,
     * so tail-follow isn't cancelled just because the filter criteria changed.
     */
    private val _recomputeTick = mutableStateOf(0)
    val recomputeTick: Int get() = _recomputeTick.value

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

    /** The single screen-state object the UI collects; see [ScreenState] for per-field docs. */
    private val _state = MutableStateFlow(ScreenState())
    val state = _state.asStateFlow()

    /** Parsed form of [ScreenState.searchQuery]; rebuilt only when the text changes, not per log line. */
    @Volatile
    private var parsedQuery: LogQuery = LogQuery.EMPTY

    /** Emits the saved file when a recording finishes successfully, so the UI can open it. */
    private val _recordingSaved = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val recordingSaved = _recordingSaved.asSharedFlow()

    /** One-shot user-facing toasts (screenshot saved, recording started/saved). */
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast = _toast.asSharedFlow()

    fun showToast(text: String) {
        _toast.tryEmit(text)
    }

    @Volatile
    private var recordingProcess: Process? = null
    private var recordingJob: Job? = null

    private var captureJob: Job? = null
    private var captureGen: Long = 0

    /** Monotonic, thread-safe id source (parsing runs off the main thread; restarts may briefly overlap). */
    private val nextLogId = AtomicInteger(0)

    /** Plain snapshot of [pidToPackage] for the background parser, which must not read Compose state. */
    @Volatile
    private var pidSnapshot: Map<String, String> = emptyMap()

    /** Pids that produced at least one visible log entry; gates process-death banners to spam-free. */
    private val pidsSeenInLogs = HashSet<String>()

    /** Pids already bannered by the jdwp tracker, so the slower ps-poll diff doesn't repeat them. */
    private val trackerBanneredPids = HashSet<String>()

    /** Follows `track-jdwp` while capturing; null when stopped or the adb server refused the stream. */
    private var trackerJob: Job? = null

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
        _state.update { it.copy(filters = filterStore.load()) }
        _state.update { it.copy(searchHistory = searchHistoryStore.load()) }
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
                _state.update { it.copy(devices = list) }
                val current = _state.value.selectedDevice
                if (current != null && list.none { it.serial == current }) {
                    // Device dropped: remember it for auto-reconnect and stop the dead stream
                    // instead of restarting against no device (which only spews adb errors).
                    lastLiveSerial = current
                    _state.update { it.copy(selectedDevice = null) }
                    stopCapture()
                    appendSystemBanner("DEVICE DISCONNECTED ($current)")
                } else if (current == null) {
                    // Prefer the device we lost; otherwise auto-pick a sole online device.
                    val returned = list.firstOrNull { it.serial == lastLiveSerial && it.isOnline }
                    val sole = list.singleOrNull()?.takeIf { it.isOnline }
                    val pick = returned ?: sole ?: return@withContext
                    val isReconnect = pick.serial == lastLiveSerial
                    _state.update { it.copy(selectedDevice = pick.serial) }
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
     * Process banners carry the process's [pid]/[appName] so package/pid filters keep them visible
     * alongside the process they describe.
     */
    private fun appendSystemBanner(text: String, pid: String = "", appName: String = "") {
        if (_state.value.loadedFile != null) return
        val content = "------------------- $text -------------------"
        val entry = LogEntry(
            id = nextLogId.getAndIncrement(),
            content = content,
            message = content,
            level = "V",
            pid = pid,
            appName = appName,
        )
        logs.add(entry)
        if (matches(entry)) filteredLogs.add(entry)
    }

    fun selectDevice(serial: String?) {
        // Re-selecting the same device must still switch back to live when a file is loaded.
        if (_state.value.selectedDevice == serial && _state.value.loadedFile == null) return
        _state.update { it.copy(selectedDevice = serial) }
        restartCapture(clearLogs = true)
        if (serial != null) refreshPackages()
    }

    private fun restartCapture(clearLogs: Boolean) {
        captureJob?.cancel()
        captureJob = null
        logcatService.stop()
        _state.update { it.copy(isCapturing = false) }
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
        _state.update { it.copy(visibleColumns = config.visibleColumns) }
        _state.update { it.copy(columnWidths = config.columnWidths) }
        _state.update { it.copy(themeMode = config.themeMode) }
        _state.update { it.copy(fontSize = config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)) }
        _state.update { it.copy(fontFamily = config.fontFamily) }
        _state.update { it.copy(fontWeight = config.fontWeight) }
        _state.update { it.copy(softWrap = config.softWrap) }
        _state.update { it.copy(expandMultilineByDefault = config.expandMultilineByDefault) }
        _state.update { it.copy(filtersListWidth = config.filtersListWidth) }
        _state.update { it.copy(alwaysShowBookmarks = config.alwaysShowBookmarks) }
        _state.update { it.copy(quickFilterVisible = config.quickFilterVisible) }
        _state.update { it.copy(logBuffers = config.logBuffers) }
        _state.update { it.copy(searchQuery = config.searchQuery) }
        parsedQuery = LogQuery.parse(config.searchQuery)
        _state.update { it.copy(searchQueryError = parsedQuery.error) }
        _state.update { it.copy(screenshotDir = config.screenshotDir) }
        _state.update { it.copy(openScreenshotAfterCapture = config.openScreenshotAfterCapture) }
        _state.update { it.copy(adbPath = config.adbPath) }
        Adb.path = config.adbPath
        _state.update { it.copy(recordingDir = config.recordingDir) }
        _state.update { it.copy(recordingShowTaps = config.recordingShowTaps) }
        _state.update { it.copy(recordingBitrateMbps = config.recordingBitrateMbps) }
        _state.update { it.copy(recordingSize = config.recordingSize) }
        windowBounds = WindowBounds(config.windowX, config.windowY, config.windowWidth, config.windowHeight)
        _state.update { it.copy(recentFiles = config.recentFiles) }
    }

    private fun currentConfig(): UserConfig = UserConfig(
        visibleColumns = _state.value.visibleColumns,
        columnWidths = _state.value.columnWidths,
        themeMode = _state.value.themeMode,
        fontSize = _state.value.fontSize,
        fontFamily = _state.value.fontFamily,
        fontWeight = _state.value.fontWeight,
        softWrap = _state.value.softWrap,
        expandMultilineByDefault = _state.value.expandMultilineByDefault,
        filtersListWidth = _state.value.filtersListWidth,
        alwaysShowBookmarks = _state.value.alwaysShowBookmarks,
        quickFilterVisible = _state.value.quickFilterVisible,
        logBuffers = _state.value.logBuffers,
        searchQuery = _state.value.searchQuery,
        screenshotDir = _state.value.screenshotDir,
        openScreenshotAfterCapture = _state.value.openScreenshotAfterCapture,
        adbPath = _state.value.adbPath,
        recordingDir = _state.value.recordingDir,
        recordingShowTaps = _state.value.recordingShowTaps,
        recordingBitrateMbps = _state.value.recordingBitrateMbps,
        recordingSize = _state.value.recordingSize,
        recentFiles = _state.value.recentFiles,
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
        if (_state.value.filtersListWidth == width) return
        _state.update { it.copy(filtersListWidth = width) }
        persistConfig()
    }

    fun setAlwaysShowBookmarks(value: Boolean) {
        if (_state.value.alwaysShowBookmarks == value) return
        _state.update { it.copy(alwaysShowBookmarks = value) }
        recomputeFiltered()
        persistConfig()
    }

    fun setQuickFilterVisible(value: Boolean) {
        if (_state.value.quickFilterVisible == value) return
        _state.update { it.copy(quickFilterVisible = value) }
        persistConfig()
    }

    fun setScreenshotDir(path: String) {
        if (_state.value.screenshotDir == path) return
        _state.update { it.copy(screenshotDir = path) }
        persistConfig()
    }

    fun setOpenScreenshotAfterCapture(value: Boolean) {
        if (_state.value.openScreenshotAfterCapture == value) return
        _state.update { it.copy(openScreenshotAfterCapture = value) }
        persistConfig()
    }

    fun setAdbPath(path: String) {
        if (_state.value.adbPath == path) return
        _state.update { it.copy(adbPath = path) }
        Adb.path = path
        persistConfig()
        // The 5s device-poll loop picks up the new binary on its own; no forced restart here so
        // typing the path char-by-char doesn't spawn a capture restart per keystroke.
    }

    fun setRecordingDir(path: String) {
        if (_state.value.recordingDir == path) return
        _state.update { it.copy(recordingDir = path) }
        persistConfig()
    }

    fun setRecordingShowTaps(value: Boolean) {
        if (_state.value.recordingShowTaps == value) return
        _state.update { it.copy(recordingShowTaps = value) }
        persistConfig()
    }

    fun setRecordingBitrateMbps(value: Int) {
        if (_state.value.recordingBitrateMbps == value) return
        _state.update { it.copy(recordingBitrateMbps = value) }
        persistConfig()
    }

    fun setRecordingSize(value: String) {
        if (_state.value.recordingSize == value) return
        _state.update { it.copy(recordingSize = value) }
        persistConfig()
    }

    fun toggleRecording() {
        if (_state.value.isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val serial = _state.value.selectedDevice
        if (_state.value.isRecording || serial == null || _state.value.loadedFile != null) return
        _state.update { it.copy(isRecording = true) }
        showToast("Recording started")
        // No ':' — illegal in sdcard (FAT/sdcardfs) filenames, screenrecord would fail to create the file.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val devicePath = "/sdcard/loglens-rec-$stamp.mp4"
        val dir = File(_state.value.recordingDir.ifBlank { "${System.getProperty("user.home")}/Videos/android" })
        val target = File(dir, "rec_$stamp.mp4")
        val showTaps = _state.value.recordingShowTaps
        val bitrate = _state.value.recordingBitrateMbps
        val size = _state.value.recordingSize
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
                    showToast("Recording saved → ${target.name}")
                }
            } catch (e: Exception) {
                System.err.println("loglens: recording failed: ${e.message}")
            } finally {
                // Restore the device's prior show-touches setting (don't clobber the user's choice).
                if (showTaps) runCatching { ScreenRecordingService.setShowTouches(serial, priorTaps == "1") }
                recordingProcess = null
                _state.update { it.copy(isRecording = false) }
            }
        }
    }

    private fun stopRecording() {
        val serial = _state.value.selectedDevice
        // SIGINT the on-device process so the mp4 is finalized; destroy() is only a backstop.
        viewModelScope.launch(Dispatchers.IO) { ScreenRecordingService.signalStop(serial) }
        recordingProcess?.destroy()
    }

    /** Toggle one adb buffer. Refuses to clear the last one, then restarts capture from the new source. */
    fun toggleLogBuffer(buffer: LogBuffer) {
        val current = _state.value.logBuffers
        val next = if (buffer in current) current - buffer else current + buffer
        if (next.isEmpty() || next == current) return
        _state.update { it.copy(logBuffers = next) }
        persistConfig()
        restartCapture(clearLogs = true)
    }

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (_state.value.fontSize == clamped) return
        _state.update { it.copy(fontSize = clamped) }
        persistConfig()
    }

    fun setFontFamily(family: String) {
        if (_state.value.fontFamily == family) return
        _state.update { it.copy(fontFamily = family) }
        persistConfig()
    }

    fun setFontWeight(weight: String) {
        if (_state.value.fontWeight == weight) return
        _state.update { it.copy(fontWeight = weight) }
        persistConfig()
    }

    fun setSoftWrap(enabled: Boolean) {
        if (_state.value.softWrap == enabled) return
        _state.update { it.copy(softWrap = enabled) }
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
        if (_state.value.alwaysShowBookmarks) recomputeFiltered()
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
        if (_state.value.expandMultilineByDefault) expandedIds.remove(id) else expandedIds.add(id)
    }

    fun setExpandMultilineByDefault(value: Boolean) {
        if (_state.value.expandMultilineByDefault == value) return
        _state.update { it.copy(expandMultilineByDefault = value) }
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
        trackerBanneredPids.clear()
        lastEntryTimestamp = null
    }

    fun setAutoFollow(enabled: Boolean) {
        _state.update { it.copy(autoFollow = enabled) }
    }

    fun toggleAutoFollow() {
        _state.update { it.copy(autoFollow = !_state.value.autoFollow) }
    }

    fun refreshPackages() {
        viewModelScope.launch {
            val newMap = PackageService.fetchPidMap(_state.value.selectedDevice)
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
                            // The package did log — count it for process-start banners too.
                            packagesSeenInLogs.add(name)
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
        if (!_state.value.isCapturing || oldMap.isEmpty()) return
        for ((pid, pkg) in oldMap) {
            if (pid !in newMap && pid in pidsSeenInLogs && !trackerBanneredPids.remove(pid)) {
                appendSystemBanner("PROCESS ENDED ($pid) for package $pkg", pid, pkg)
            }
        }
        for ((pid, pkg) in newMap) {
            if (pid !in oldMap && pkg in packagesSeenInLogs && !trackerBanneredPids.remove(pid)) {
                appendSystemBanner("PROCESS STARTED ($pid) for package $pkg", pid, pkg)
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
        val current = _state.value.visibleColumns
        _state.update { it.copy(visibleColumns = if (column in current) current - column else current + column) }
        persistConfig()
    }

    fun updateColumnWidth(column: LogColumn, width: Float) {
        _state.update {
            it.copy(columnWidths = it.columnWidths.toMutableMap().apply { this[column] = width.coerceAtLeast(10f) })
        }
        persistConfig()
    }

    fun setTheme(mode: ThemeMode) {
        if (_state.value.themeMode == mode) return
        _state.update { it.copy(themeMode = mode) }
        persistConfig()
    }

    fun updateSearchQuery(query: String) {
        if (_state.value.searchQuery == query) return
        _state.update { it.copy(searchQuery = query) }
        parsedQuery = LogQuery.parse(query)
        _state.update { it.copy(searchQueryError = parsedQuery.error) }
        recomputeFiltered()
    }

    /**
     * Record the current query in history. Skips blanks and queries that failed to parse.
     * Saved synchronously (history is tiny) so it survives an immediate app exit.
     */
    fun commitSearchQuery() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty() || parsedQuery.error != null) return
        // Move an existing identical entry to the front; cap the list.
        val updated = (listOf(query) + _state.value.searchHistory.filterNot { it == query }).take(SEARCH_HISTORY_LIMIT)
        if (updated == _state.value.searchHistory) return
        _state.update { it.copy(searchHistory = updated) }
        searchHistoryStore.save(updated)
    }

    fun clearSearchHistory() {
        if (_state.value.searchHistory.isEmpty()) return
        _state.update { it.copy(searchHistory = emptyList()) }
        searchHistoryStore.save(emptyList())
    }

    /** True if [log] should appear in [filteredLogs] under the current criteria. */
    private fun matches(log: LogEntry): Boolean {
        if (_state.value.alwaysShowBookmarks && log.id in bookmarkedIds) return true
        if (!parsedQuery.matches(log)) return false
        if (hideFilters.any { it.matches(log) }) return false
        if (showFilters.isNotEmpty() && showFilters.none { it.matches(log) }) return false
        return true
    }

    /** Refresh the cached active-filter partition; call before any (re)filtering pass. */
    private fun refreshFilterPartition() {
        val active = _state.value.filters.filter { it.enabled }
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
        _recomputeTick.value++
    }

    fun startCapture() {
        if (_state.value.isCapturing) return
        // Going live: we are no longer reading from a file.
        _state.update { it.copy(loadedFile = null) }
        _state.update { it.copy(isCapturing = true) }
        _state.update { it.copy(isPaused = false) }
        _state.value.selectedDevice?.let { lastLiveSerial = it }
        // One-shot resume point from an auto-reconnect; a normal start reads the whole buffer.
        val sinceTime = resumeSinceTimestamp
        resumeSinceTimestamp = null
        val gen = ++captureGen
        val channel = Channel<CapturedLine>(Channel.UNLIMITED)

        startProcessTracker()

        // Producer: read and detect record headers off the main thread. A null header marks a
        // continuation line (stack frame) the consumer folds into the preceding entry.
        val producer = viewModelScope.launch(Dispatchers.Default) {
            try {
                logcatService.stream(_state.value.selectedDevice, _state.value.logBuffers, sinceTime).collect { line ->
                    // Off the main thread: parse, and precompute the filter-match and crash flags
                    // (the consumer's heaviest per-line work). They don't depend on the not-yet-
                    // assigned id — a fresh line is never bookmarked — so the result matches the
                    // consumer's entry. Only header lines are precomputed; orphan continuations are
                    // rare and resolved by the consumer.
                    val h = LogParser.parseLine(line, pidSnapshot)
                    channel.send(
                        if (h != null) CapturedLine(line, h, matches(h), CrashDetector.isCrash(h))
                        else CapturedLine(line, null),
                    )
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
                    if (_state.value.isPaused) {
                        delay(FLUSH_INTERVAL_MS)
                        continue
                    }
                    val first = channel.receiveCatching().getOrNull() ?: break
                    val batch = ArrayList<CapturedLine>()
                    batch.add(first)
                    // Bounded drain: never grab more than one chunk per cycle so the main thread isn't
                    // stalled for the whole backlog during a burst.
                    while (batch.size < MAX_DRAIN_PER_FLUSH) {
                        batch.add(channel.tryReceive().getOrNull() ?: break)
                    }
                    val hitCap = batch.size >= MAX_DRAIN_PER_FLUSH
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
                                val merged =
                                    LogParser.foldContinuation(foldCurrent, cap.raw, header?.message ?: cap.raw)
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
                                    // Reuse the producer's off-main flags for header lines; only the
                                    // rare orphan (null header) is computed here.
                                    val matched = if (header != null) cap.matchesFilter else matches(entry)
                                    if (matched) {
                                        filteredLogs.add(entry)
                                        foldFilteredIdx = filteredLogs.lastIndex
                                    } else {
                                        foldFilteredIdx = -1
                                    }
                                    val crash = if (header != null) cap.isCrash else CrashDetector.isCrash(entry)
                                    if (crash) {
                                        registerCrash(entry.id, visible = foldFilteredIdx >= 0)
                                    }
                                    // A separator collects no continuations, so trailing text isn't
                                    // glued onto an unrelated record.
                                    foldCurrent = if (header != null && !isSep) entry else null
                                }
                            }
                        }
                    }
                    // Backlog left (we hit the cap): yield a frame so scrolling/input stay responsive,
                    // then loop straight into the next chunk to keep catch-up throughput. Otherwise
                    // idle the normal flush interval until more lines arrive.
                    if (hitCap) yield() else delay(FLUSH_INTERVAL_MS)
                }
            } finally {
                producer.cancel()
                if (gen == captureGen) _state.update { it.copy(isCapturing = false) }
            }
        }
    }

    /**
     * A captured line and its parsed header ([LogParser.parseLine]), or null header for a
     * continuation. [matchesFilter] and [isCrash] are precomputed off the main thread for header
     * lines (false/false when header is null — the consumer recomputes for the rare orphan case).
     */
    private class CapturedLine(
        val raw: String,
        val header: LogEntry?,
        val matchesFilter: Boolean = false,
        val isCrash: Boolean = false,
    )

    fun stopCapture() {
        captureGen++
        captureJob?.cancel()
        captureJob = null
        trackerJob?.cancel()
        trackerJob = null
        logcatService.stop()
        _state.update { it.copy(isCapturing = false) }
        _state.update { it.copy(isPaused = false) }
    }

    /**
     * Instant process lifecycle banners, the Android Studio way: the adb server's `track-jdwp`
     * stream pushes the debuggable-pid set on every change, so a restart of the debugged app
     * banners immediately instead of waiting out the next ps poll (which stays on as a catch-all
     * for non-debuggable processes — [trackerBanneredPids] keeps the two from double-reporting).
     */
    private fun startProcessTracker() {
        trackerJob?.cancel()
        val serial = _state.value.selectedDevice ?: return
        trackerJob = viewModelScope.launch {
            var previous: Set<String>? = null
            ProcessTrackerService.track(serial)
                .catch { /* adb server gone / stream refused — poll-based banners still apply */ }
                .collect { pids ->
                    val prev = previous
                    previous = pids
                    if (prev == null) return@collect // baseline, not a change
                    val ended = prev - pids
                    val started = pids - prev
                    withContext(Dispatchers.Main) {
                        for (pid in ended) {
                            trackerBanneredPids.add(pid)
                            val pkg = pidToPackage[pid] ?: "pid $pid"
                            appendSystemBanner("PROCESS ENDED ($pid) for package $pkg", pid, pkg)
                        }
                        started.forEach(trackerBanneredPids::add)
                    }
                    // A pid registers with jdwp before `ps` reports its final process name (the
                    // zygote fork is renamed during bind), so poll briefly for the real name —
                    // a banner with a placeholder would be invisible under a package: filter.
                    for (pid in started) {
                        var pkg: String? = null
                        var attempt = 0
                        while (pkg == null && attempt < NAME_RESOLVE_ATTEMPTS) {
                            pkg = PackageService.fetchPidMap(serial)[pid]
                                ?.takeUnless { it.startsWith("zygote") || it == "<pre-initialized>" }
                            if (pkg == null) delay(NAME_RESOLVE_DELAY_MS)
                            attempt++
                        }
                        val resolved = pkg ?: pidToPackage[pid] ?: "pid $pid"
                        withContext(Dispatchers.Main) {
                            appendSystemBanner(
                                "PROCESS STARTED ($pid) for package $resolved",
                                pid,
                                resolved,
                            )
                        }
                    }
                }
        }
    }

    fun toggleCapture() {
        if (_state.value.isCapturing) stopCapture() else startCapture()
    }

    /** Pause/resume the display while the stream keeps buffering; see [isPaused]. */
    fun togglePause() {
        if (_state.value.isCapturing) _state.update { it.copy(isPaused = !_state.value.isPaused) }
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
        if (_state.value.loadedFile == null && _state.value.selectedDevice != null) {
            val serial = _state.value.selectedDevice
            val buffers = _state.value.logBuffers
            val wasCapturing = _state.value.isCapturing
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
            _state.update { it.copy(autoFollow = false) }
            _state.update { it.copy(loadedFile = file.name) }
            recomputeFiltered()
            rememberRecentFile(file)
        }
    }

    /** Open a previously loaded file by path; if it's gone, drop it from the recent list. */
    fun openRecentFile(path: String) {
        val file = File(path)
        if (file.exists()) loadLogsFromFile(file)
        else _state.update { it.copy(recentFiles = _state.value.recentFiles.filterNot { it == path }.also { persistConfig() }) }
    }

    fun clearRecentFiles() {
        if (_state.value.recentFiles.isEmpty()) return
        _state.update { it.copy(recentFiles = emptyList()) }
        persistConfig()
    }

    private fun rememberRecentFile(file: File) {
        val path = file.absolutePath
        // Most-recent first, de-duplicated, capped so the list (and config) stays small.
        _state.update { it.copy(recentFiles = (listOf(path) + _state.value.recentFiles.filterNot { it == path }).take(MAX_RECENT_FILES)) }
        persistConfig()
    }

    fun replaceFilters(newFilters: List<FilterRule>) {
        _state.update { it.copy(filters = newFilters) }
        recomputeFiltered()
        persistFilters()
    }

    fun addFilter(rule: FilterRule) {
        _state.update { it.copy(filters = _state.value.filters + rule) }
        recomputeFiltered()
        persistFilters()
    }

    fun removeFilter(id: String) {
        val current = _state.value.filters
        if (current.none { it.id == id }) return
        _state.update { it.copy(filters = current.filterNot { it.id == id }) }
        recomputeFiltered()
        persistFilters()
    }

    fun reorderFilters(from: Int, to: Int) {
        val current = _state.value.filters
        if (from == to || from !in current.indices || to !in current.indices) return
        _state.update { it.copy(filters = current.toMutableList().apply { add(to, removeAt(from)) }) }
        // Order can change which SHOW/HIDE rule wins, so the visible set may shift.
        recomputeFiltered()
        persistFilters()
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        val current = _state.value.filters
        if (current.none { it.id == id && it.enabled != enabled }) return
        _state.update { it.copy(filters = current.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        recomputeFiltered()
        persistFilters()
    }

    private fun persistFilters() {
        val snapshot = _state.value.filters
        viewModelScope.launch(Dispatchers.IO) { filterStore.save(snapshot) }
    }

    override fun onCleared() {
        logcatService.stop()
        super.onCleared()
    }
}

/**
 * The complete screen state the UI renders, collected as one object instead of ~30 separate flows.
 * Field notes that used to live on the individual flows:
 *  - [isPaused]: display pause — the adb stream keeps buffering, only UI appending stops.
 *  - [searchQueryError]: non-null when the current query has a syntax/regex error.
 *  - [screenshotDir] / [recordingDir]: blank means the platform default directory.
 *  - [adbPath]: blank means "adb" from PATH.  [loadedFile]: null while reading a live device.
 *  - [searchHistory] / [recentFiles]: most-recent first.
 */
data class ScreenState(
    val isCapturing: Boolean = false,
    val isPaused: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchQuery: String = "",
    val searchQueryError: String? = null,
    val searchHistory: List<String> = emptyList(),
    val screenshotDir: String = "",
    val openScreenshotAfterCapture: Boolean = true,
    val adbPath: String = "",
    val recordingDir: String = "",
    val isRecording: Boolean = false,
    val recordingShowTaps: Boolean = false,
    val recordingBitrateMbps: Int = 0,
    val recordingSize: String = "",
    val autoFollow: Boolean = true,
    val visibleColumns: Set<LogColumn> = DEFAULT_VISIBLE_COLUMNS,
    val columnWidths: Map<LogColumn, Float> = DEFAULT_COLUMN_WIDTHS,
    val fontSize: Float = DEFAULT_FONT_SIZE,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val fontWeight: String = DEFAULT_FONT_WEIGHT,
    val softWrap: Boolean = true,
    val expandMultilineByDefault: Boolean = false,
    val filtersListWidth: Float = DEFAULT_FILTERS_LIST_WIDTH,
    val alwaysShowBookmarks: Boolean = true,
    val quickFilterVisible: Boolean = true,
    val logBuffers: Set<LogBuffer> = DEFAULT_LOG_BUFFERS,
    val filters: List<FilterRule> = emptyList(),
    val devices: List<Device> = emptyList(),
    val selectedDevice: String? = null,
    val loadedFile: String? = null,
    val recentFiles: List<String> = emptyList(),
)

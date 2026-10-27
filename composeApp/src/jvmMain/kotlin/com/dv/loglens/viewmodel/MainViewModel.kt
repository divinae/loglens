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
import com.dv.loglens.model.ToastKind
import com.dv.loglens.model.ToastMessage
import com.dv.loglens.model.DEFAULT_COLUMN_WIDTHS
import com.dv.loglens.model.DEFAULT_FILTERS_LIST_WIDTH
import com.dv.loglens.model.DEFAULT_FONT_FAMILY
import com.dv.loglens.model.DEFAULT_FONT_SIZE
import com.dv.loglens.model.DEFAULT_FONT_WEIGHT
import com.dv.loglens.model.DEFAULT_LOG_BUFFERS
import com.dv.loglens.model.DEFAULT_VISIBLE_COLUMNS
import com.dv.loglens.model.Device
import com.dv.loglens.model.FilterRule
import com.dv.loglens.model.LogBuffer
import com.dv.loglens.model.FontWeightPreset
import com.dv.loglens.model.LogColumn
import com.dv.loglens.model.LogEntry
import com.dv.loglens.model.LogQuery
import com.dv.loglens.model.MAX_FONT_SIZE
import com.dv.loglens.model.MIN_COLUMN_WIDTH
import com.dv.loglens.model.MIN_FONT_SIZE
import com.dv.loglens.model.ThemeMode
import com.dv.loglens.model.UserConfig
import com.dv.loglens.model.WindowBounds
import com.dv.loglens.parser.AndroidStudioLogParser
import com.dv.loglens.parser.BugreportParser
import com.dv.loglens.parser.CrashDetector
import com.dv.loglens.parser.LogParser
import com.dv.loglens.service.Adb
import com.dv.loglens.service.Scrcpy
import com.dv.loglens.service.SystemFonts
import com.dv.loglens.service.DeviceService
import com.dv.loglens.service.LogcatService
import com.dv.loglens.service.PackageService
import com.dv.loglens.service.ProcessTrackerService
import com.dv.loglens.service.ScrcpyService
import com.dv.loglens.util.Log
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

    // Query + SHOW/HIDE rule predicate (the testable part of matching); the bookmark override below
    // stays here because it reads Compose state. Refreshed via refreshFilterPartition().
    private val logFilter = LogFilter()

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

    /** One-shot user-facing toasts (screenshot saved, recording started/saved, device events). */
    private val _toast = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 4)
    val toast = _toast.asSharedFlow()

    fun showToast(text: String) {
        _toast.tryEmit(ToastMessage(text, ToastKind.SUCCESS))
    }

    /** Neutral status note (blue) — e.g. a device connecting. */
    fun showInfoToast(text: String) {
        _toast.tryEmit(ToastMessage(text, ToastKind.INFO))
    }

    /** Warning (red, no success check mark) — e.g. a device drop or a missing tool. */
    fun showErrorToast(text: String) {
        _toast.tryEmit(ToastMessage(text, ToastKind.ERROR))
    }

    /**
     * Current live-capture error, shown as an independent floating element (not a log entry).
     * Null when there's nothing to show; cleared by [dismissCaptureError] or a fresh capture start.
     */
    private val _captureError = MutableStateFlow<String?>(null)
    val captureError = _captureError.asStateFlow()

    fun dismissCaptureError() {
        _captureError.value = null
    }

    /** A failed file load, shown as a centered placeholder in the empty log view. */
    private val _fileError = MutableStateFlow<String?>(null)
    val fileError = _fileError.asStateFlow()

    fun dismissFileError() {
        _fileError.value = null
    }

    private val recordingController = RecordingController(
        scope = viewModelScope,
        isRecording = { _state.value.isRecording },
        setRecording = { value -> _state.update { it.copy(isRecording = value) } },
        currentSerial = { _state.value.selectedDevice },
        isFileLoaded = { _state.value.loadedFile != null },
        settings = {
            RecordingSettings(
                dir = _state.value.recordingDir,
                showTaps = _state.value.recordingShowTaps,
                bitrateMbps = _state.value.recordingBitrateMbps,
                size = _state.value.recordingSize,
            )
        },
        showToast = ::showToast,
        onSaved = { _recordingSaved.emit(it) },
    )

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
                    showErrorToast("Device disconnected ($current)")
                } else if (current == null && _state.value.loadedFile == null) {
                    // Don't hijack a file the user is viewing: only auto-pick a device when not
                    // reading from a file.
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
                        showInfoToast("Device reconnected (${pick.serial})")
                        resumeSinceTimestamp = lastEntryTimestamp
                        restartCapture(clearLogs = false)
                    } else {
                        appendSystemBanner("DEVICE CONNECTED (${pick.serial})")
                        showInfoToast("Device connected (${pick.serial})")
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
        // Side effects that the state copy below references or that live outside ScreenState.
        parsedQuery = LogQuery.parse(config.searchQuery)
        Adb.path = config.adbPath
        Scrcpy.path = config.scrcpyPath
        windowBounds = WindowBounds(config.windowX, config.windowY, config.windowWidth, config.windowHeight)
        // One copy() instead of ~25, so startup emits a single ScreenState rather than allocating
        // and publishing an intermediate per field.
        _state.update {
            it.copy(
                visibleColumns = config.visibleColumns,
                columnWidths = config.columnWidths,
                themeMode = config.themeMode,
                fontSize = config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
                fontFamily = config.fontFamily,
                fontWeight = config.fontWeight,
                softWrap = config.softWrap,
                expandMultilineByDefault = config.expandMultilineByDefault,
                filtersListWidth = config.filtersListWidth,
                alwaysShowBookmarks = config.alwaysShowBookmarks,
                quickFilterVisible = config.quickFilterVisible,
                logBuffers = config.logBuffers,
                searchQuery = config.searchQuery,
                searchQueryError = parsedQuery.error,
                screenshotDir = config.screenshotDir,
                openScreenshotAfterCapture = config.openScreenshotAfterCapture,
                adbPath = config.adbPath,
                scrcpyPath = config.scrcpyPath,
                recordingDir = config.recordingDir,
                recordingShowTaps = config.recordingShowTaps,
                recordingBitrateMbps = config.recordingBitrateMbps,
                recordingSize = config.recordingSize,
                recentFiles = config.recentFiles,
            )
        }
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
        scrcpyPath = _state.value.scrcpyPath,
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

    fun setScrcpyPath(path: String) {
        if (_state.value.scrcpyPath == path) return
        _state.update { it.copy(scrcpyPath = path) }
        Scrcpy.path = path
        persistConfig()
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

    fun toggleRecording() = recordingController.toggle()

    /** Open scrcpy in its own window to mirror the live device. No-op while reading a file. */
    fun launchScrcpy() {
        val serial = _state.value.selectedDevice
        if (serial == null || _state.value.loadedFile != null) return
        val connection = _state.value.devices.firstOrNull { it.serial == serial }?.connection
        viewModelScope.launch {
            if (ScrcpyService.launch(serial, connection)) {
                showInfoToast("Mirroring device — scrcpy window opened")
            } else {
                showErrorToast("scrcpy not found — install it and ensure it's on PATH")
            }
        }
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
        // A weight the new family has no face for would render as Normal; make that explicit.
        val weight = FontWeightPreset.fromName(_state.value.fontWeight)
        val keptWeight = if (weight in SystemFonts.availableWeights(family)) weight else FontWeightPreset.NORMAL
        _state.update { it.copy(fontFamily = family, fontWeight = keptWeight.name) }
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
        // A loaded file's logs don't come from the live device, so there's nothing to backfill —
        // and running the O(n) backfill scan over a large file on every 1–5s poll froze the UI
        // when a device was plugged in while viewing a file.
        if (_state.value.loadedFile != null) return
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
            it.copy(
                columnWidths = it.columnWidths.toMutableMap().apply {
                    this[column] = width.coerceAtLeast(MIN_COLUMN_WIDTH)
                },
            )
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
        return logFilter.matches(log)
    }

    /** Refresh the cached query + filter partition; call before any (re)filtering pass. */
    private fun refreshFilterPartition() {
        logFilter.update(parsedQuery, _state.value.filters)
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
        // A fresh attempt clears any stale capture/file error from a previous run.
        _captureError.value = null
        _fileError.value = null
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
                logcatService.stream(
                    _state.value.selectedDevice,
                    _state.value.logBuffers,
                    sinceTime,
                    onError = ::onCaptureError,
                ).collect { line ->
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
        // The per-line fold/dedup/crash logic lives in [LogIngestor]; the ingestor holds the fold
        // cursor across batches because a trace can span a flush boundary, so one instance lives for
        // the length of this capture session.
        val ingestor = LogIngestor(
            logs = logs,
            filteredLogs = filteredLogs,
            nextLogId = nextLogId::getAndIncrement,
            matches = ::matches,
            isCrash = CrashDetector::isCrash,
            isKnownCrash = crashIds::contains,
            onHeader = ::observeCapturedHeader,
            registerCrash = ::registerCrash,
            onCrashBecameVisible = { visibleCrashes = visibleCrashes + it },
        )
        captureJob = viewModelScope.launch {
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
                    // A restart bumps the generation; drop a batch parsed against the old source.
                    if (gen == captureGen) ingestor.ingest(batch)
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
     * adb failed to launch (or emitted an error on stderr) for the live stream. Surface it as an
     * independent floating element via [captureError] rather than a fake log line, and record it for
     * diagnostics. Called from the producer's IO context, so the state mutation hops to the main thread.
     */
    private fun onCaptureError(message: String) {
        Log.w(message)
        viewModelScope.launch(Dispatchers.Main) {
            _captureError.value = message
        }
    }

    /** Per-header capture bookkeeping: reconnect resume point and seen pids/packages for banners. */
    private fun observeCapturedHeader(header: LogEntry) {
        if (header.time.isBlank()) return
        lastEntryTimestamp = "${header.date} ${header.time}"
        if (header.pid.isNotBlank()) pidsSeenInLogs.add(header.pid)
        if (header.appName.isNotBlank()) packagesSeenInLogs.add(header.appName)
    }

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
    /** Result of the off-main file load: parsed entries plus the derived crash/filter views. */
    private class LoadedLogs(
        val entries: List<LogEntry>,
        val crashIds: Set<Int>,
        val filtered: List<LogEntry>,
        val visibleCrashes: List<Int>,
    )

    fun loadLogsFromFile(file: File) {
        stopCapture()
        // Switching to a file: any live-capture error and any prior file error are now stale.
        _captureError.value = null
        _fileError.value = null
        // Snapshot the active filter criteria so the heavy filtering pass below can run off the main
        // thread without touching live state.
        refreshFilterPartition()
        viewModelScope.launch {
            // Do everything O(n) — parse, crash detection, and the initial filter pass — on IO, so
            // the main thread only does the unavoidable observable-list inserts. Previously the
            // crash scan and full filter ran on the main thread right after parse, freezing the
            // whole app "for a moment" on a large file.
            val loaded = try {
                withContext(Dispatchers.IO) {
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
                    val parsed = structured
                        ?: text?.let { LogParser.parseLog(it.lineSequence()) }
                        ?: file.useLines { lines -> LogParser.parseLog(lines) }
                    val crashes = parsed.mapNotNullTo(HashSet()) { it.id.takeIf { _ -> CrashDetector.isCrash(it) } }
                    val filtered = parsed.filter { matches(it) }
                    val visible = if (crashes.isEmpty()) emptyList()
                    else filtered.mapNotNull { log -> log.id.takeIf { it in crashes } }
                    LoadedLogs(parsed, crashes, filtered, visible)
                }
            } catch (e: Exception) {
                Log.w("failed to load ${file.name}", e)
                failFileLoad("Couldn't open ${file.name}: ${e.message ?: e::class.simpleName}")
                return@launch
            }
            logs.clear()
            filteredLogs.clear()
            selectedIds.clear()
            clearDerivedState()
            logs.addAll(loaded.entries)
            nextLogId.set(loaded.entries.size)
            crashIds.addAll(loaded.crashIds)
            filteredLogs.addAll(loaded.filtered)
            visibleCrashes = loaded.visibleCrashes
            _state.update { it.copy(autoFollow = false) }
            _state.update { it.copy(loadedFile = file.name) }
            _recomputeTick.value++
            rememberRecentFile(file)
        }
    }

    /** Open a previously loaded file by path; if it's gone, tell the user and drop it from the list. */
    fun openRecentFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            loadLogsFromFile(file)
        } else {
            // Same treatment as a parse failure: clear whatever was open and show the centered
            // panel (no toast for file-open errors). Drop the dead entry from the recent list.
            failFileLoad("File no longer exists: ${file.name}")
            _state.update { it.copy(recentFiles = _state.value.recentFiles.filterNot { it == path }.also { persistConfig() }) }
        }
    }

    /** Clear the open logs and surface [message] as the centered file-load error panel. */
    private fun failFileLoad(message: String) {
        logs.clear()
        filteredLogs.clear()
        selectedIds.clear()
        clearDerivedState()
        _state.update { it.copy(loadedFile = null) }
        _recomputeTick.value++
        _fileError.value = message
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
    val scrcpyPath: String = "",
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

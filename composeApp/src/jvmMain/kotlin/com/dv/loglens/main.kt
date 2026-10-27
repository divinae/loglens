package com.dv.loglens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import com.dv.loglens.util.Log
import com.dv.loglens.util.Wayland
import com.dv.loglens.viewmodel.MainViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import org.jetbrains.skia.Image
import androidx.compose.ui.awt.ComposePanel
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Width reserved left of the toolbar for the macOS traffic-light buttons once the content
// extends into the (hidden) title bar area.
private val MAC_TRAFFIC_LIGHTS_INSET = 72.dp

// Window geometry is saved this long after the last resize/move, so a drag writes once.
private const val BOUNDS_SAVE_DEBOUNCE_MS = 500L

// Packaged apps have no console, so an uncaught exception would vanish silently. Append a
// timestamped stack trace to ~/.config/loglens/crash.log (same dir ConfigManager uses) so users
// have something to attach to a bug report. Chained after any prior handler.
private fun installCrashHandler() {
    val crashLog = File(System.getProperty("user.home"), ".config/loglens/crash.log")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            crashLog.parentFile?.mkdirs()
            crashLog.appendText(
                buildString {
                    append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    append(" [").append(thread.name).append("] ")
                    appendLine(throwable.toString())
                    throwable.stackTrace.forEach { appendLine("\tat $it") }
                    appendLine()
                },
            )
        }
        Log.e("uncaught exception on ${thread.name}", throwable)
        previous?.uncaughtException(thread, throwable)
    }
}

// The window/taskbar icon, shared by both window hosts.
private fun loadIconBytes(): ByteArray? = runCatching {
    object {}.javaClass.getResourceAsStream("/loglens-icon.png")!!.use { it.readBytes() }
}.getOrNull()

// Steps common to closing the main window under either host ([waylandMain], [composeMain]),
// before each persists its geometry and exits its own way.
private fun onMainWindowClosing(viewModel: MainViewModel) {
    // Record the current query before exit so it survives in history.
    viewModel.commitSearchQuery()
}

fun main() {
    // Before anything touches AWT: the toolkit is fixed once AWT initialises.
    Wayland.selectToolkit()
    if (Wayland.active) waylandMain() else composeMain()
}

// Native Wayland: Compose's Window can't draw under WLToolkit, so host the app in a Swing frame
// whose ComposePanel renders through Swing graphics (see [Wayland]). Dialogs render in-window.
private fun waylandMain() {
    installCrashHandler()
    SwingUtilities.invokeLater {
        val viewModel = MainViewModel()
        val frame = JFrame("LogLens ${BuildInfo.version}")
        frame.iconImage = loadIconBytes()?.let { ImageIO.read(ByteArrayInputStream(it)) }
        frame.contentPane.add(ShutdownSafeHost(ComposePanel().apply { setContent { App(viewModel) } }))
        frame.setSize(viewModel.windowBounds.width.roundToInt(), viewModel.windowBounds.height.roundToInt())
        // Wayland clients can neither read nor set their global position, so only the size is
        // persisted; the stored position is kept for X11 sessions.
        fun persistSize() = viewModel.saveWindowBounds(
            viewModel.windowBounds.x,
            viewModel.windowBounds.y,
            frame.width.toFloat(),
            frame.height.toFloat(),
        )
        // Debounced so a resize drag writes once, not per frame.
        val persistTimer = Timer(BOUNDS_SAVE_DEBOUNCE_MS.toInt()) { persistSize() }.apply { isRepeats = false }
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = persistTimer.restart()
        })
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                onMainWindowClosing(viewModel)
                persistSize()
                frame.dispose()
                // The view model's shutdown hook makes the final, synchronous config save.
                exitProcess(0)
            }
        })
        frame.isVisible = true
    }
}

// On SIGTERM (e.g. logout) JBR's WLToolkit tears windows down from its own shutdown thread; the
// ComposePanel's removeNotify then throws (Compose lifecycle must change on the main thread) and
// lands in crash.log. Off the EDT this only happens while the JVM exits, so skip the teardown.
private class ShutdownSafeHost(content: JComponent) : JPanel(BorderLayout()) {
    init {
        add(content)
    }

    override fun removeNotify() {
        if (SwingUtilities.isEventDispatchThread()) super.removeNotify()
    }
}

@OptIn(FlowPreview::class)
private fun composeMain() = application {
    installCrashHandler()
    // The window/taskbar icon. A 256px source so it stays crisp on HiDPI displays, where the
    // window manager scales it up rather than rendering a blurry low-res bitmap.
    val icon = remember {
        loadIconBytes()?.let { BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap()) }
    }
    val viewModel = remember { MainViewModel() }
    val bounds = remember { viewModel.windowBounds }
    val windowState = rememberWindowState(
        position = if (bounds.x >= 0f && bounds.y >= 0f) WindowPosition(bounds.x.dp, bounds.y.dp)
        else WindowPosition(Alignment.Center),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )
    Window(
        onCloseRequest = {
            onMainWindowClosing(viewModel)
            persistBounds(viewModel, windowState)
            exitApplication()
        },
        state = windowState,
        title = "LogLens ${BuildInfo.version}",
        icon = icon,
    ) {
        // Persist geometry as the user resizes/moves; debounced so a drag writes once, not per frame.
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size to windowState.position }
                .debounce(BOUNDS_SAVE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { persistBounds(viewModel, windowState) }
        }
        // macOS: no native title bar. JBR/AWT client properties make the content fill the full
        // window with a transparent, title-less bar (the traffic lights stay); the app's own top
        // bar takes over as the drag handle below. No-ops on other platforms.
        val isMac = remember { System.getProperty("os.name").startsWith("Mac") }
        if (isMac) {
            LaunchedEffect(Unit) {
                with(window.rootPane) {
                    putClientProperty("apple.awt.fullWindowContent", true)
                    putClientProperty("apple.awt.transparentTitleBar", true)
                    putClientProperty("apple.awt.windowTitleVisible", false)
                }
            }
        }
        val topBarDecoration: @Composable (content: @Composable () -> Unit) -> Unit =
            if (isMac) {
                { content ->
                    WindowDraggableArea {
                        Box(Modifier.padding(start = MAC_TRAFFIC_LIGHTS_INSET)) { content() }
                    }
                }
            } else {
                { content -> content() }
            }
        App(viewModel, topBarDecoration = topBarDecoration)
    }
}

private fun persistBounds(viewModel: MainViewModel, windowState: androidx.compose.ui.window.WindowState) {
    val size = windowState.size
    val pos = windowState.position
    val x = if (pos.isSpecified) pos.x.value else -1f
    val y = if (pos.isSpecified) pos.y.value else -1f
    val w = if (size.width.isSpecified) size.width.value else viewModel.windowBounds.width
    val h = if (size.height.isSpecified) size.height.value else viewModel.windowBounds.height
    viewModel.saveWindowBounds(x, y, w, h)
}

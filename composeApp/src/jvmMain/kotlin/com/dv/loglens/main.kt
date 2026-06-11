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
import com.dv.loglens.viewmodel.MainViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import org.jetbrains.skia.Image
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Width reserved left of the toolbar for the macOS traffic-light buttons once the content
// extends into the (hidden) title bar area.
private val MAC_TRAFFIC_LIGHTS_INSET = 72.dp

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

@OptIn(FlowPreview::class)
fun main() = application {
    installCrashHandler()
    // The window/taskbar icon. A 256px source so it stays crisp on HiDPI displays, where the
    // window manager scales it up rather than rendering a blurry low-res bitmap.
    val icon = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/loglens-icon.png")!!.use { it.readBytes() }
            BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
        }.getOrNull()
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
            // Record the current query before exit so it survives in history.
            viewModel.commitSearchQuery()
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
                .debounce(500)
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

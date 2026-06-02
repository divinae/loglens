package com.dv.loglens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import com.dv.loglens.viewmodel.MainViewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import org.jetbrains.skia.Image

fun main() {
    enableNativeWaylandIfAvailable()
    runApp()
}

/**
 * Select JBR's native Wayland AWT toolkit (WLToolkit) before AWT initialises, so on a Wayland
 * session we render as a native client instead of through XWayland. Defensive: only when on Linux,
 * in a Wayland session, and the toolkit class is actually present (it ships only in JetBrains
 * Runtime) — otherwise the default XToolkit keeps X11 / non-JBR runtimes working. Escape hatch:
 * `-Dloglens.x11=true` forces XToolkit. (Note: JBR presets awt.toolkit.name to "XToolkit", so we
 * must override that default rather than treat it as a user choice.)
 */
private fun enableNativeWaylandIfAvailable() {
    if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return
    if (System.getProperty("loglens.x11").toBoolean()) return
    if (System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) return
    val present = runCatching {
        Class.forName("sun.awt.wl.WLToolkit", false, ClassLoader.getSystemClassLoader())
    }.isSuccess
    if (present) System.setProperty("awt.toolkit.name", "WLToolkit")
}

private fun runApp() = application {
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
        App(viewModel)
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

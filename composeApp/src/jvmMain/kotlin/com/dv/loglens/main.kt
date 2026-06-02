package com.dv.loglens

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dv.loglens.viewmodel.MainViewModel
import org.jetbrains.skia.Image

fun main() = application {
    // The window/taskbar icon. A 256px source so it stays crisp on HiDPI displays, where the
    // window manager scales it up rather than rendering a blurry low-res bitmap.
    val icon = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/loglens-icon.png")!!.use { it.readBytes() }
            BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
        }.getOrNull()
    }
    val viewModel = remember { MainViewModel() }
    Window(
        onCloseRequest = {
            // Record the current query before exit so it survives in history.
            viewModel.commitSearchQuery()
            exitApplication()
        },
        title = "LogLens ${BuildInfo.version}",
        icon = icon,
    ) {
        App(viewModel)
    }
}

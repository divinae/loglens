package com.dv.logexp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "logexp ${BuildInfo.version}",
    ) {
        App()
    }
}

package com.dv.logexp

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dv.logexp.viewmodel.MainViewModel

fun main() = application {
    val viewModel = remember { MainViewModel() }
    Window(
        onCloseRequest = {
            // Record the current query before exit so it survives in history.
            viewModel.commitSearchQuery()
            exitApplication()
        },
        title = "logexp ${BuildInfo.version}",
    ) {
        App(viewModel)
    }
}

package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.loglens.BuildInfo
import com.dv.loglens.ui.theme.islandsPanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

private const val REPO_URL = "https://gitlab.com/divinae/loglens"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val state = rememberDialogState(size = DpSize(380.dp, 380.dp))
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "About",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(islandsPanel(JewelTheme.isDark)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            Text("LogLens", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Android logcat explorer",
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f),
            )
            Text(
                "Version ${BuildInfo.version}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f),
            )
            Text(
                text = REPO_URL,
                fontSize = 12.sp,
                color = Color(0xFF3592FF),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { openInBrowser(REPO_URL) },
            )

            val dim = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
            Column(
                modifier = Modifier.padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Built with", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = dim)
                Text(
                    "Kotlin ${BuildInfo.kotlinVersion} · Compose ${BuildInfo.composeVersion}",
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = dim,
                )
                Text(
                    "Jewel ${BuildInfo.jewelVersion}",
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = dim,
                )
                Text(
                    "Coroutines ${BuildInfo.coroutinesVersion}",
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = dim,
                )
                Text(
                    BuildInfo.runtime,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = dim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            DefaultButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) {
                Text("Close")
            }
        }
    }
}

package com.dv.loglens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.loglens.ui.theme.islandsPanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** One row of the quick-filter syntax reference: the example token and what it does. */
private data class QuerySyntaxSpec(val example: String, val description: String)

/** Single source of truth for the filter- and find-bar help; keep in sync with [com.dv.loglens.model.LogQuery]. */
private val QUERY_SYNTAX: List<QuerySyntaxSpec> = listOf(
    QuerySyntaxSpec("timeout", "Bare text: line contains it (case-insensitive)"),
    QuerySyntaxSpec("\"two words\"", "Quoted phrase may contain spaces"),
    QuerySyntaxSpec("tag:Foo", "Tag contains Foo"),
    QuerySyntaxSpec("package:com.foo", "Process name contains (aliases: app:, pkg:)"),
    QuerySyntaxSpec("msg:boom", "Message contains (alias: message:)"),
    QuerySyntaxSpec("pid:1234  tid:42", "Exact process / thread id"),
    QuerySyntaxSpec("level:E", "Exactly level E"),
    QuerySyntaxSpec("level>=W", "Level W or above (V < D < I < W < E < F)"),
    QuerySyntaxSpec("tag~:Foo.*Bar", "Value is a regex; tag:/Foo.*/ works too"),
    QuerySyntaxSpec("is:crash", "Crash, ANR or native crash records"),
    QuerySyntaxSpec("is:stacktrace", "Records carrying a stack trace"),
    QuerySyntaxSpec("age:5m", "Newer than 30s / 5m / 2h / 1d ago"),
    QuerySyntaxSpec("-msg:spam", "A leading - negates any term"),
    QuerySyntaxSpec("tag:Foo level>=W", "Terms combine with AND"),
    QuerySyntaxSpec("tag:Foo | tag:Bar", "A standalone | ORs groups (AND binds tighter)"),
)

@Composable
fun QueryHelpDialog(onDismiss: () -> Unit) {
    val state = rememberDialogState(size = DpSize(520.dp, 440.dp))
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Query Syntax",
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(islandsPanel(JewelTheme.isDark)).padding(16.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(QUERY_SYNTAX) { spec ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = spec.example,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.width(170.dp),
                        )
                        Text(
                            text = spec.description,
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

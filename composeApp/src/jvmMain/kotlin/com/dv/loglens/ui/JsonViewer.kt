package com.dv.loglens.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.dv.loglens.ui.theme.islandsEditor
import com.dv.loglens.ui.theme.islandsPanel
import com.dv.loglens.util.copyToClipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

private val prettyJson = Json { prettyPrint = true }

/** Pretty-prints [raw] if it is a JSON object or array, otherwise returns null. */
internal fun formatJson(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.length < 2 || (trimmed[0] != '{' && trimmed[0] != '[')) return null
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(trimmed))
    } catch (_: Exception) {
        null
    }
}

/** Lexes already-valid pretty JSON and colors keys, strings, numbers and keywords. */
internal fun highlightJson(json: String, isDark: Boolean): AnnotatedString {
    val keyColor = if (isDark) Color(0xFF9CDCFE) else Color(0xFF0451A5)
    val stringColor = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515)
    val numberColor = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658)
    val keywordColor = if (isDark) Color(0xFF569CD6) else Color(0xFF0000FF)
    return buildAnnotatedString {
        var i = 0
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < json.length) {
                        when (json[i]) {
                            '\\' -> i += 2
                            '"' -> {
                                i++; break
                            }

                            else -> i++
                        }
                    }
                    var j = i
                    while (j < json.length && json[j].isWhitespace()) j++
                    val isKey = j < json.length && json[j] == ':'
                    withStyle(SpanStyle(color = if (isKey) keyColor else stringColor)) {
                        append(json.substring(start, i))
                    }
                }

                c.isDigit() || (c == '-' && i + 1 < json.length && json[i + 1].isDigit()) -> {
                    val start = i
                    i++
                    while (i < json.length && (json[i].isDigit() || json[i] in ".eE+-")) i++
                    withStyle(SpanStyle(color = numberColor)) { append(json.substring(start, i)) }
                }

                c.isLetter() -> {
                    val start = i
                    while (i < json.length && json[i].isLetter()) i++
                    val word = json.substring(start, i)
                    if (word == "true" || word == "false" || word == "null") {
                        withStyle(SpanStyle(color = keywordColor)) { append(word) }
                    } else {
                        append(word)
                    }
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}

@Composable
internal fun JsonViewerDialog(
    json: String,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    onDismiss: () -> Unit,
) {
    val isDark = JewelTheme.isDark
    val dialogState = rememberDialogState(size = DpSize(720.dp, 600.dp))
    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Formatted JSON",
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
            modifier = Modifier
                .fillMaxSize()
                .background(islandsPanel(isDark))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(islandsEditor(isDark)),
            ) {
                val highlighted = remember(json, isDark) { highlightJson(json, isDark) }
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(8.dp),
                ) {
                    Text(highlighted, fontFamily = fontFamily, fontSize = fontSize)
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(vScroll),
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .fillMaxHeight(),
                )
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(hScroll),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                DefaultButton(onClick = { copyToClipboard(json) }) { Text("Copy") }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

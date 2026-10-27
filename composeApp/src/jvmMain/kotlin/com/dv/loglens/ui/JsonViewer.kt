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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import com.dv.loglens.ui.theme.islandsEditor
import com.dv.loglens.ui.theme.islandsPanel
import com.dv.loglens.util.copyToClipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val prettyJson = Json { prettyPrint = true }

/**
 * Pretty-prints [raw] if it contains a JSON object or array, otherwise returns null.
 * Handles JSON embedded in a larger log record (e.g. a folded HTTP log where the body
 * sits between `BODY START`/`BODY END` markers) by extracting the first balanced
 * `{...}`/`[...]` block and parsing that.
 */
internal fun formatJson(raw: String): String? {
    val trimmed = raw.trim()
    val candidate = if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        trimmed
    } else {
        extractJsonBlock(raw) ?: return null
    }
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(candidate))
    } catch (_: Exception) {
        // Fall back to a balanced-block extraction when the whole candidate has trailing junk.
        val block = extractJsonBlock(candidate) ?: return null
        try {
            prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(block))
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Returns the first balanced `{...}` or `[...]` substring in [text], respecting JSON
 * string literals (so braces inside quotes don't throw off the depth count), or null
 * if none is found.
 */
private fun extractJsonBlock(text: String): String? {
    val start = text.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return null
    val open = text[start]
    val close = if (open == '{') '}' else ']'
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> {}
            c == open -> depth++
            c == close -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
        i++
    }
    return null
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

/** Case-insensitive offsets of every [query] occurrence in [text]; empty when query is blank. */
internal fun findMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var i = text.indexOf(query, 0, ignoreCase = true)
    while (i >= 0) {
        out.add(i until i + query.length)
        i = text.indexOf(query, i + query.length, ignoreCase = true)
    }
    return out
}

// The search field uses Jewel's TextFieldValue TextField, experimental since Jewel 0.41.
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun JsonViewerDialog(
    json: String,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    onDismiss: () -> Unit,
) {
    val isDark = JewelTheme.isDark
    val dialogState = rememberDialogState(size = DpSize(720.dp, 600.dp))
    AppDialogWindow(
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
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        var search by remember { mutableStateOf(TextFieldValue("")) }
        var currentMatch by remember { mutableStateOf(0) }
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val searchFocus = remember { FocusRequester() }

        val matches = remember(json, search.text) { findMatches(json, search.text) }
        // Keep the active match in range as the query (and thus the match count) changes.
        val activeMatch = if (matches.isEmpty()) 0 else currentMatch.coerceIn(0, matches.size - 1)

        val matchColor = if (isDark) Color(0x66BBBB00) else Color(0x66FFEB3B)
        val activeColor = if (isDark) Color(0xCCDD8800) else Color(0xCCFFA726)
        val base = remember(json, isDark) { highlightJson(json, isDark) }
        val highlighted = remember(base, matches, activeMatch, matchColor, activeColor) {
            if (matches.isEmpty()) {
                base
            } else {
                buildAnnotatedString {
                    append(base)
                    matches.forEachIndexed { i, r ->
                        addStyle(
                            SpanStyle(background = if (i == activeMatch) activeColor else matchColor),
                            r.first,
                            r.last + 1,
                        )
                    }
                }
            }
        }

        // Scroll the active match into view once layout is known.
        LaunchedEffect(activeMatch, matches, layout) {
            val l = layout ?: return@LaunchedEffect
            if (matches.isEmpty()) return@LaunchedEffect
            val box = l.getBoundingBox(matches[activeMatch].first)
            vScroll.animateScrollTo((box.top.toInt() - 40).coerceAtLeast(0))
            val x = box.left.toInt() - 40
            if (x > hScroll.value || box.right.toInt() > hScroll.value + 1) hScroll.animateScrollTo(x.coerceAtLeast(0))
        }
        LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

        fun step(delta: Int) {
            if (matches.isEmpty()) return
            currentMatch = ((activeMatch + delta) % matches.size + matches.size) % matches.size
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(islandsPanel(isDark))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = search,
                    onValueChange = { search = it; currentMatch = 0 },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                                step(if (e.isShiftPressed) -1 else 1)
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Search") },
                    leadingIcon = {
                        Icon(AllIconsKeys.Actions.Find, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                val counter = if (matches.isEmpty()) {
                    if (search.text.isEmpty()) "" else "0/0"
                } else {
                    "${activeMatch + 1}/${matches.size}"
                }
                if (counter.isNotEmpty()) {
                    Text(counter, modifier = Modifier.width(56.dp))
                }
                IconButton(onClick = { step(-1) }, enabled = matches.isNotEmpty()) {
                    Icon(AllIconsKeys.Actions.FindAndShowPrevMatches, contentDescription = "Previous match")
                }
                IconButton(onClick = { step(1) }, enabled = matches.isNotEmpty()) {
                    Icon(AllIconsKeys.Actions.FindAndShowNextMatches, contentDescription = "Next match")
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(islandsEditor(isDark)),
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(8.dp),
                ) {
                    Text(
                        highlighted,
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        onTextLayout = { layout = it },
                    )
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

package com.dv.loglens.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.awt.Desktop
import java.net.URI

/**
 * Open [url] in the system browser; failures (no desktop, malformed URI) are swallowed.
 * AWT's BROWSE action is unsupported on most Linux desktops, so fall back to `xdg-open` there.
 */
internal fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            ProcessBuilder("xdg-open", url).start()
        }
    }
}

/** http/https up to the next whitespace; trailing prose punctuation is trimmed by [urlEndIndex]. */
private val URL_REGEX = Regex("""https?://\S+""")

/** Punctuation that typically follows a URL in prose rather than belonging to it. */
private const val TRAILING_PUNCTUATION = ".,;:!?'\""

/**
 * End offset (exclusive) of the URL starting at [start] with raw regex end [rawEnd], with trailing
 * prose punctuation stripped — `.` `,` `)` etc. A `)` is kept when the URL itself contains a `(`
 * (e.g. Wikipedia paths), dropped when it just closes a surrounding parenthesis.
 */
private fun urlEndIndex(text: String, start: Int, rawEnd: Int): Int {
    var end = rawEnd
    while (end > start && text[end - 1] in TRAILING_PUNCTUATION) end--
    if (end > start && text[end - 1] == ')' && '(' !in text.substring(start, end)) end--
    return end
}

/**
 * The message-cell text with find-match backgrounds and (when [linkColor] is given) clickable,
 * underlined http/https links that open in the system browser. Spans are orthogonal — a URL that
 * is also a find match renders with both the highlight background and the link styling.
 */
internal fun buildLogCellAnnotated(
    text: String,
    findQuery: String,
    findColor: Color,
    linkColor: Color?,
): AnnotatedString {
    val linkify = linkColor != null && "http" in text
    // indexOf(ignoreCase) compares via regionMatches — no lowercased copies of text/query allocated.
    val firstMatch = if (findQuery.isBlank()) -1 else text.indexOf(findQuery, ignoreCase = true)
    if (!linkify && firstMatch < 0) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        if (linkify) {
            val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            for (match in URL_REGEX.findAll(text)) {
                val start = match.range.first
                val end = urlEndIndex(text, start, match.range.last + 1)
                if (end <= start) continue
                val url = text.substring(start, end)
                addLink(LinkAnnotation.Url(url, styles, linkInteractionListener = { openInBrowser(url) }), start, end)
            }
        }
        var i = firstMatch
        while (i >= 0) {
            addStyle(SpanStyle(background = findColor), i, i + findQuery.length)
            i = text.indexOf(findQuery, i + findQuery.length, ignoreCase = true)
        }
    }
}

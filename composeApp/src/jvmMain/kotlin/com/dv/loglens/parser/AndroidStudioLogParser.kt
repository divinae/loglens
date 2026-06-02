package com.dv.loglens.parser

import com.dv.loglens.model.LogEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parser for Android Studio's exported `.logcat` files: a JSON object with a `logcatMessages`
 * array, each entry carrying a `header` (level, pid/tid, processName, tag, timestamp) and `message`.
 */
object AndroidStudioLogParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFmt = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneId.systemDefault())
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /** True if [text] looks like an Android Studio logcat JSON export. */
    fun matches(text: String): Boolean {
        val head = text.trimStart()
        return head.startsWith("{") && head.contains("\"logcatMessages\"")
    }

    /** Parse all messages; returns null if the content is not the expected JSON shape. */
    fun parse(text: String): List<LogEntry>? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val messages = root["logcatMessages"]?.jsonArray ?: return null
        messages.mapIndexed { index, element ->
            val obj = element.jsonObject
            val header = obj["header"]?.jsonObject
            val message = obj["message"]?.jsonPrimitive?.content ?: ""
            val level = header?.get("logLevel")?.jsonPrimitive?.content.orEmpty()
            val pid = header?.get("pid")?.jsonPrimitive?.content.orEmpty()
            val tid = header?.get("tid")?.jsonPrimitive?.content.orEmpty()
            val tag = header?.get("tag")?.jsonPrimitive?.content.orEmpty()
            val app = header?.get("processName")?.jsonPrimitive?.content
                ?: header?.get("applicationId")?.jsonPrimitive?.content.orEmpty()
            val ts = header?.get("timestamp")?.jsonObject
            val seconds = ts?.get("seconds")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val nanos = ts?.get("nanos")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val instant = Instant.ofEpochSecond(seconds, nanos)
            val date = dateFmt.format(instant)
            val time = timeFmt.format(instant)
            val levelChar = LogParser.levelChar(level)
            LogEntry(
                id = index,
                content = "$date $time $pid $tid $levelChar $tag: $message",
                date = date,
                time = time,
                pid = pid,
                tid = tid,
                level = levelChar,
                tag = tag,
                message = message,
                appName = app,
            )
        }
    }.getOrNull()
}

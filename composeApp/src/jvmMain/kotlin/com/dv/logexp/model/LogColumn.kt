package com.dv.logexp.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogColumn(val displayName: String) {
    INDEX("Index"),
    DATE("Date"),
    TIME("Time"),
    PID("PID"),
    TID("TID"),
    PROCESS("App"),
    LEVEL("Level"),
    TAG("Tag"),
    MESSAGE("Message")
}

/** The value of [column] for this log entry. */
fun LogEntry.cell(column: LogColumn): String = when (column) {
    LogColumn.INDEX -> id.toString()
    LogColumn.DATE -> date
    LogColumn.TIME -> time
    LogColumn.PID -> pid
    LogColumn.TID -> tid
    LogColumn.PROCESS -> appName
    LogColumn.LEVEL -> level
    LogColumn.TAG -> tag
    LogColumn.MESSAGE -> message
}

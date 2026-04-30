package com.dv.logexp.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogColumn(val displayName: String) {
    DATE("Date"),
    TIME("Time"),
    PID("PID"),
    TID("TID"),
    PROCESS("Process"),
    LEVEL("Level"),
    TAG("Tag"),
    MESSAGE("Message")
}

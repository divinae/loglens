package com.dv.logexp.model

data class LogEntry(
    val id: Int,
    val content: String,
    val date: String = "",
    val time: String = "",
    val pid: String = "",
    val tid: String = "",
    val level: String = "",
    val tag: String = "",
    val message: String = "",
    val appName: String = ""
)

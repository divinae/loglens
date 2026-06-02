package com.dv.loglens.model

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
    val appName: String = "",
    // How many consecutive identical records this row stands for (1 = no repeats coalesced).
    val repeatCount: Int = 1,
) {
    /** True when [other] is "the same log" for coalescing purposes — every field but the timestamp. */
    fun sameLogAs(other: LogEntry): Boolean =
        level == other.level && tag == other.tag && pid == other.pid &&
            tid == other.tid && appName == other.appName && message == other.message
}

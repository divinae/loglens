package com.dv.logexp.model

import kotlinx.serialization.Serializable

/** An adb logcat ring buffer, selectable via `adb logcat -b <adbName>`. */
@Serializable
enum class LogBuffer(val adbName: String, val displayName: String) {
    MAIN("main", "Main"),
    SYSTEM("system", "System"),
    CRASH("crash", "Crash"),
    RADIO("radio", "Radio"),
    EVENTS("events", "Events"),
    KERNEL("kernel", "Kernel"),
}

/** adb's own default set (main + system + crash) when no explicit buffer is chosen. */
val DEFAULT_LOG_BUFFERS: Set<LogBuffer> = setOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH)

package com.dv.loglens.model

enum class ConnectionType(val label: String) { USB("USB"), WIFI("Wi-Fi") }

data class Device(
    val serial: String,
    val state: String,
    val model: String? = null,
    val product: String? = null,
    val connection: ConnectionType? = null,
) {
    val displayName: String
        get() {
            val label = model ?: product ?: serial
            val suffix = buildList {
                connection?.let { add(it.label) }
                if (state != "device") add(state)
            }
            return if (suffix.isEmpty()) label else "$label (${suffix.joinToString(", ")})"
        }

    val isOnline: Boolean get() = state == "device"
}

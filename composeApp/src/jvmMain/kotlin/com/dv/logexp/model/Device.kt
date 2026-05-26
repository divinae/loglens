package com.dv.logexp.model

data class Device(
    val serial: String,
    val state: String,
    val model: String? = null,
    val product: String? = null,
) {
    val displayName: String
        get() {
            val label = model ?: product ?: serial
            return if (state == "device") label else "$label ($state)"
        }

    val isOnline: Boolean get() = state == "device"
}

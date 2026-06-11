package com.dv.loglens.model

/** Severity of a toast, driving its colour and glyph. */
enum class ToastKind { SUCCESS, INFO, ERROR }

/**
 * A transient toast. [kind] picks the styling: green check (success), blue info, or red warning —
 * so e.g. a device disconnect doesn't read as "OK" and a status note doesn't read as a completion.
 */
data class ToastMessage(val text: String, val kind: ToastKind = ToastKind.SUCCESS)

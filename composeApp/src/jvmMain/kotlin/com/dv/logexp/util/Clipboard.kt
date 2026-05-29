package com.dv.logexp.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Copies [text] to the system clipboard. */
fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}

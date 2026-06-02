package com.dv.loglens.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Native file dialogs. The JVM only ships X11/GTK file choosers, so on KDE we shell out to the
 * desktop's own picker (kdialog → Plasma's portal) for a native dialog; zenity covers GNOME, and
 * AWT [FileDialog] is the last-resort fallback. Blocking — call off the UI thread.
 */
object FilePicker {
    private val kdialog by lazy { which("kdialog") }
    private val zenity by lazy { which("zenity") }

    // kdialog filters are "patterns|label", multiple separated by newlines.
    private const val KDIALOG_FILTER =
        "*.txt *.log *.logcat *.zip|Log files & bugreports (*.txt, *.log, *.logcat, *.zip)\n*|All files (*)"

    fun openFile(title: String): File? = when {
        kdialog != null -> runPicker(listOf(kdialog!!, "--title", title, "--getopenfilename", startDir(), KDIALOG_FILTER))
        zenity != null -> runPicker(listOf(zenity!!, "--file-selection", "--title", title))
        else -> awt(title, null, FileDialog.LOAD)
    }

    fun saveFile(title: String, defaultName: String): File? {
        val suggested = File(startDir(), defaultName).path
        return when {
            kdialog != null -> runPicker(listOf(kdialog!!, "--title", title, "--getsavefilename", suggested, KDIALOG_FILTER))
            zenity != null -> runPicker(
                listOf(zenity!!, "--file-selection", "--save", "--confirm-overwrite", "--title", title, "--filename", suggested),
            )
            else -> awt(title, defaultName, FileDialog.SAVE)
        }
    }

    private fun runPicker(command: List<String>): File? = runCatching {
        val proc = ProcessBuilder(command).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = proc.waitFor()
        if (code == 0 && out.isNotEmpty()) File(out.lineSequence().first()) else null
    }.getOrNull()

    private fun awt(title: String, defaultName: String?, mode: Int): File? {
        val dialog = FileDialog(null as Frame?, title, mode)
        if (defaultName != null) dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }

    private fun startDir(): String = System.getProperty("user.home") ?: "."

    private fun which(cmd: String): String? =
        System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.map { File(it, cmd) }
            ?.firstOrNull { it.canExecute() }
            ?.path
}

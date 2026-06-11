package com.dv.loglens.service

import com.dv.loglens.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ScreenshotService {
    // PNG magic bytes — used to reject error output that isn't an image.
    private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /**
     * Capture the device screen via `adb [-s serial] exec-out screencap -p`, writing the PNG
     * straight to [target]. Returns true on success. `exec-out` streams the binary on stdout with
     * no `/sdcard` round-trip and no CR/LF mangling, so stderr must NOT be merged into it.
     */
    suspend fun capture(serial: String?, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = buildList {
                add(Adb.exe())
                if (serial != null) { add("-s"); add(serial) }
                add("exec-out"); add("screencap"); add("-p")
            }
            target.parentFile?.mkdirs()
            val proc = ProcessBuilder(cmd).start()
            target.outputStream().use { out -> proc.inputStream.copyTo(out) }
            val ok = proc.waitFor() == 0 && target.length() > PNG_HEADER.size &&
                target.inputStream().use { it.readNBytes(PNG_HEADER.size) }.contentEquals(PNG_HEADER)
            if (!ok) target.delete()
            ok
        } catch (e: Exception) {
            Log.w("screenshot failed", e)
            target.delete()
            false
        }
    }
}

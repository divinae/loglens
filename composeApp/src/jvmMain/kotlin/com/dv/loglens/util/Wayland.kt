package com.dv.loglens.util

import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

/**
 * Native Wayland via JetBrains Runtime's WLToolkit instead of XWayland, so fractional scaling is
 * rendered at the output's real scale rather than upscaled (blurry) by the compositor.
 *
 * Compose's own `Window`/`DialogWindow` can't draw under WLToolkit (Skiko's hardware layer locks
 * the window via JAWT for an X11 handle), so when [active] the main window is a Swing frame hosting
 * a `ComposePanel` rendered through Swing graphics (Skia raster), and dialogs render in-window.
 */
object Wayland {
    const val PIXEL_GEOMETRY_PROPERTY = "loglens.pixelGeometry"
    private const val FC_MATCH_TIMEOUT_MS = 2000L

    /** True once [selectToolkit] chose WLToolkit for this process. */
    var active: Boolean = false
        private set

    /**
     * Must run first thing in `main()`, before any AWT class is touched — the toolkit is fixed when
     * AWT initialises. Opt out with `LOGLENS_X11=1`. JBR presets `awt.toolkit.name=XToolkit`, so
     * only a hand-set `WLToolkit` can be told apart from the default and forces Wayland.
     */
    fun selectToolkit() {
        active = when {
            System.getProperty("awt.toolkit.name") == "WLToolkit" -> true
            System.getenv("LOGLENS_X11") == "1" -> false
            !System.getProperty("os.name").startsWith("Linux") -> false
            !hasWlToolkit() -> false
            else -> compositorReachable()
        }
        if (!active) return
        System.setProperty("awt.toolkit.name", "WLToolkit")
        // ComposePanel draws into a Swing-managed image instead of a native GL surface.
        System.setProperty("compose.swing.render.on.graphics", "true")
        // There's no GL context to get under WLToolkit; skip the failing attempt and its warning.
        System.setProperty("skiko.renderApi", "SOFTWARE")
        // Read by the patched org.jetbrains.skiko.swing.SoftwareSwingRedrawer for subpixel text.
        System.setProperty(PIXEL_GEOMETRY_PROPERTY, subpixelGeometry())
        warnIfRedrawerNotPatched()
        Log.i("using native Wayland (WLToolkit)")
    }

    /**
     * The desktop's subpixel order as a Skia PixelGeometry name, from fontconfig (which KDE/GNOME
     * font settings write to). `none` maps to UNKNOWN, i.e. grayscale antialiasing; an unset or
     * unreadable value assumes the common horizontal RGB.
     */
    private fun subpixelGeometry(): String {
        val rgba = runCatching {
            val proc = ProcessBuilder("fc-match", "-f", "%{rgba}", "monospace").redirectErrorStream(true).start()
            if (proc.waitFor(FC_MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) proc.inputStream.reader().readText().trim()
            else null.also { proc.destroy() }
        }.getOrNull()
        return when (rgba) {
            "2" -> "BGR_H"
            "3" -> "RGB_V"
            "4" -> "BGR_V"
            "5" -> "UNKNOWN"
            else -> "RGB_H"
        }
    }

    // The subpixel fix only works if our copy of the redrawer shadows Skiko's, i.e. the app jar
    // precedes skiko-awt on the classpath (true for `run` and jpackage today). Loaded, not inited.
    private fun warnIfRedrawerNotPatched() {
        val redrawer = Class.forName("org.jetbrains.skiko.swing.SoftwareSwingRedrawer", false, javaClass.classLoader)
        val appJar = javaClass.protectionDomain?.codeSource?.location
        if (redrawer.protectionDomain?.codeSource?.location != appJar) {
            Log.w("Skiko's own SoftwareSwingRedrawer loaded; text renders without subpixel AA")
        }
    }

    // Only JetBrains Runtime ships the Wayland toolkit.
    private fun hasWlToolkit(): Boolean =
        ModuleLayer.boot().findModule("java.desktop")
            .map { "sun.awt.wl" in it.packages }
            .orElse(false)

    // A WAYLAND_DISPLAY left over from a dead compositor isn't enough: the socket must accept.
    private fun compositorReachable(): Boolean {
        val display = System.getenv("WAYLAND_DISPLAY")?.takeIf { it.isNotBlank() } ?: return false
        val socket = if (display.startsWith("/")) {
            File(display)
        } else {
            val runtimeDir = System.getenv("XDG_RUNTIME_DIR") ?: return false
            File(runtimeDir, display)
        }
        return runCatching {
            SocketChannel.open(StandardProtocolFamily.UNIX).use {
                it.connect(UnixDomainSocketAddress.of(socket.toPath()))
            }
        }.isSuccess
    }
}

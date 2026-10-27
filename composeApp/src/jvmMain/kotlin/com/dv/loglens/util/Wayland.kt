package com.dv.loglens.util

import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel

/**
 * Native Wayland via JetBrains Runtime's WLToolkit instead of XWayland, so fractional scaling is
 * rendered at the output's real scale rather than upscaled (blurry) by the compositor.
 *
 * Compose's own `Window`/`DialogWindow` can't draw under WLToolkit (Skiko's hardware layer locks
 * the window via JAWT for an X11 handle), so when [active] the main window is a Swing frame hosting
 * a `ComposePanel` rendered through Swing graphics (Skia raster), and dialogs render in-window.
 */
object Wayland {
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
        Log.i("using native Wayland (WLToolkit)")
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

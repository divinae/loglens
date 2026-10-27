package org.jetbrains.skiko.swing;

import java.awt.Graphics2D;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.PixelGeometry;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceProps;
import org.jetbrains.skiko.GraphicsApi;
import org.jetbrains.skiko.SkiaLayerAnalytics;
import org.jetbrains.skiko.SkikoRenderDelegate;

/**
 * LogLens override of Skiko 0.150.2's SoftwareSwingRedrawer (Apache 2.0), the renderer behind
 * ComposePanel under native Wayland. Identical except the raster surface declares the desktop's
 * subpixel order: Skiko creates it without SurfaceProps, so Skia can only draw grayscale-antialiased
 * text, which looks soft next to native apps' subpixel text. Shadows the library class because
 * the app jar precedes skiko-awt on the classpath; re-check against Skiko on every upgrade.
 */
public final class SoftwareSwingRedrawer extends SwingRedrawerBase {
    // Subpixel order detected from fontconfig at startup (com.dv.loglens.util.Wayland).
    private static final SurfaceProps SURFACE_PROPS = new SurfaceProps(
        false, PixelGeometry.valueOf(System.getProperty("loglens.pixelGeometry", "RGB_H")));

    private final SkikoRenderDelegate renderDelegate;
    private final SwingPainter painter;
    private final Bitmap storage = new Bitmap();

    public SoftwareSwingRedrawer(
        SwingLayerProperties swingLayerProperties,
        SkikoRenderDelegate renderDelegate,
        SkiaLayerAnalytics analytics
    ) {
        super(swingLayerProperties, analytics, GraphicsApi.SOFTWARE_FAST);
        onDeviceChosen("Software");
        this.renderDelegate = renderDelegate;
        this.painter = new SoftwareSwingPainter(swingLayerProperties);
        onContextInit(null);
    }

    @Override
    public void dispose() {
        super.dispose();
        storage.close();
        painter.dispose();
    }

    @Override
    protected void onRender(Graphics2D g, int width, int height, long nanoTime) {
        if (storage.getWidth() != width || storage.getHeight() != height) {
            storage.allocPixelsFlags(ImageInfo.Companion.makeS32(width, height, ColorAlphaType.PREMUL), false);
        }
        long pixelsPointer = storage.peekPixels().getAddr();
        Surface surface = Surface.Companion.makeRasterDirect(
            storage.getImageInfo(), pixelsPointer, storage.getRowBytes(), SURFACE_PROPS);
        try {
            surface.getCanvas().clear(0);
            renderDelegate.onRender(surface.getCanvas(), width, height, nanoTime);
            painter.paint(g, surface, 0);
        } finally {
            surface.close();
        }
    }
}

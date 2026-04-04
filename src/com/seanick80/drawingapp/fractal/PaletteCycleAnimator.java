package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Animates a fractal by cycling gradient colors through a pre-computed
 * iteration buffer. No re-rendering needed — just re-coloring.
 */
public class PaletteCycleAnimator {

    public interface ProgressCallback {
        void onFrame(int frame, int total, long elapsedMs);
    }

    private final FractalRenderer renderer;
    private int fps = 30;
    private int totalFrames = 120;
    private float cycleSpeed = 1.0f; // full rotations over the animation

    public void setFps(int fps) { this.fps = fps; }
    public void setTotalFrames(int frames) { this.totalFrames = frames; }
    public void setCycleSpeed(float speed) { this.cycleSpeed = speed; }
    public int getFps() { return fps; }
    public int getTotalFrames() { return totalFrames; }
    public float getCycleSpeed() { return cycleSpeed; }

    public PaletteCycleAnimator(FractalRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Render palette cycle frames to the output directory.
     * Returns the number of frames written.
     */
    public int renderToFiles(File outputDir, int[] iters, int width, int height,
                             ColorGradient baseGradient, ProgressCallback callback)
            throws IOException {
        if (iters == null || width <= 0 || height <= 0) return 0;

        // Copy iteration buffer to avoid mutation
        int[] itersCopy = new int[iters.length];
        System.arraycopy(iters, 0, itersCopy, 0, iters.length);

        outputDir.mkdirs();
        File aviFile = new File(outputDir, "palette_cycle.avi");

        try (AviWriter avi = new AviWriter(aviFile, width, height, fps)) {
            for (int frame = 0; frame < totalFrames; frame++) {
                long start = System.currentTimeMillis();

                float shift = (frame * cycleSpeed) / totalFrames;
                ColorGradient shifted = shiftGradient(baseGradient, shift);
                BufferedImage img = renderer.recolorFromIters(itersCopy, width, height, shifted);

                avi.addFrame(img);

                if (callback != null) {
                    callback.onFrame(frame, totalFrames, System.currentTimeMillis() - start);
                }
            }
        }
        return totalFrames;
    }

    /**
     * Play palette cycle in a real-time preview window (looping).
     * Returns the preview window for external stop control.
     */
    public AnimationPreviewWindow playRealtime(int[] iters, int width, int height,
                                                ColorGradient baseGradient) {
        if (iters == null || width <= 0 || height <= 0) return null;

        int[] itersCopy = new int[iters.length];
        System.arraycopy(iters, 0, itersCopy, 0, iters.length);

        AnimationPreviewWindow window = new AnimationPreviewWindow(
                "Palette Cycle Preview", width, height);
        window.setVisible(true);

        final int[] frameCounter = {0};
        window.playLoop(fps, true, () -> {
            if (window.isStopped()) return null;
            float shift = (frameCounter[0] * cycleSpeed) / totalFrames;
            frameCounter[0] = (frameCounter[0] + 1) % totalFrames;
            ColorGradient shifted = shiftGradient(baseGradient, shift);
            return renderer.recolorFromIters(itersCopy, width, height, shifted);
        });

        return window;
    }

    /**
     * Shift all gradient stop positions by the given offset, wrapping at 1.0.
     * Returns a new gradient; does not modify the original.
     */
    public static ColorGradient shiftGradient(ColorGradient gradient, float offset) {
        // Normalize offset to [0, 1)
        offset = offset - (float) Math.floor(offset);

        List<ColorGradient.Stop> stops = gradient.getStops();
        ColorGradient shifted = new ColorGradient();
        shifted.getStops().clear();

        for (ColorGradient.Stop stop : stops) {
            float newPos = stop.getPosition() + offset;
            if (newPos > 1.0f) newPos -= 1.0f;
            shifted.addStop(newPos, stop.getColor());
        }
        return shifted;
    }
}

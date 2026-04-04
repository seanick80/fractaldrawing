package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Animates a fractal by rendering with increasing iteration counts,
 * showing the progressive emergence of detail.
 */
public class IterationAnimator {

    public interface ProgressCallback {
        void onFrame(int frame, int total, long elapsedMs);
    }

    private int fps = 30;
    private int startIter = 1;
    private int endIter = 256;
    private int step = 1;
    private int width = 640;
    private int height = 480;
    private volatile boolean cancelled = false;

    public void setFps(int fps) { this.fps = fps; }
    public void setStartIter(int iter) { this.startIter = iter; }
    public void setEndIter(int iter) { this.endIter = iter; }
    public void setStep(int step) { this.step = Math.max(1, step); }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void cancel() { this.cancelled = true; }
    public int getFps() { return fps; }
    public int getStartIter() { return startIter; }
    public int getEndIter() { return endIter; }
    public int getStep() { return step; }
    public int getTotalFrames() { return (endIter - startIter) / step + 1; }

    /**
     * Play iteration animation in a real-time preview window.
     * Returns the preview window for external stop control.
     */
    public AnimationPreviewWindow playRealtime(FractalRenderer sourceRenderer,
                                                ColorGradient gradient) {
        if (width <= 0 || height <= 0 || startIter > endIter) return null;

        FractalRenderer animRenderer = new FractalRenderer();
        animRenderer.setType(sourceRenderer.getType());
        animRenderer.setBounds(sourceRenderer.getMinRealBig(), sourceRenderer.getMaxRealBig(),
                               sourceRenderer.getMinImagBig(), sourceRenderer.getMaxImagBig());
        animRenderer.setRenderMode(sourceRenderer.getRenderMode());
        animRenderer.setColorMode(sourceRenderer.getColorMode());

        AnimationPreviewWindow window = new AnimationPreviewWindow(
                "Iteration Animation Preview", width, height);
        window.setVisible(true);

        final int[] currentIter = {startIter};
        window.playLoop(fps, false, () -> {
            if (window.isStopped() || currentIter[0] > endIter) return null;
            animRenderer.setMaxIterations(currentIter[0]);
            currentIter[0] += step;
            return animRenderer.render(width, height, gradient);
        });

        return window;
    }

    /**
     * Render iteration animation frames to the output directory.
     * Creates a fresh FractalRenderer per call to avoid mutating the caller's state.
     * Returns the number of frames written.
     */
    public int renderToFiles(File outputDir, FractalRenderer sourceRenderer,
                             ColorGradient gradient, ProgressCallback callback)
            throws IOException {
        if (width <= 0 || height <= 0 || startIter > endIter) return 0;

        // Create a separate renderer with the same viewport
        FractalRenderer animRenderer = new FractalRenderer();
        animRenderer.setType(sourceRenderer.getType());
        animRenderer.setBounds(sourceRenderer.getMinRealBig(), sourceRenderer.getMaxRealBig(),
                               sourceRenderer.getMinImagBig(), sourceRenderer.getMaxImagBig());
        animRenderer.setRenderMode(sourceRenderer.getRenderMode());
        animRenderer.setColorMode(sourceRenderer.getColorMode());

        outputDir.mkdirs();
        File aviFile = new File(outputDir, "iteration_anim.avi");
        int totalFrames = getTotalFrames();

        try (AviWriter avi = new AviWriter(aviFile, width, height, fps)) {
            int frame = 0;
            for (int iter = startIter; iter <= endIter; iter += step) {
                if (cancelled) break;

                long start = System.currentTimeMillis();
                animRenderer.setMaxIterations(iter);
                BufferedImage img = animRenderer.render(width, height, gradient);
                if (img == null) break; // cancelled

                avi.addFrame(img);

                if (callback != null) {
                    callback.onFrame(frame, totalFrames, System.currentTimeMillis() - start);
                }
                frame++;
            }
            return frame;
        }
    }
}

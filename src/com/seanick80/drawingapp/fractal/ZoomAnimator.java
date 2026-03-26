package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates zoom animation sequences by interpolating between keyframes.
 * Each keyframe defines a viewport (center, zoom level, maxIterations).
 * Frames are rendered and exported as numbered PNG files.
 */
public class ZoomAnimator {

    /** A single keyframe in the zoom animation. */
    public static class Keyframe {
        public final BigDecimal centerReal;
        public final BigDecimal centerImag;
        public final BigDecimal zoomLevel;
        public final int maxIterations;

        public Keyframe(BigDecimal centerReal, BigDecimal centerImag,
                        BigDecimal zoomLevel, int maxIterations) {
            this.centerReal = centerReal;
            this.centerImag = centerImag;
            this.zoomLevel = zoomLevel;
            this.maxIterations = maxIterations;
        }

        public Keyframe(double centerReal, double centerImag,
                        double zoomLevel, int maxIterations) {
            this(new BigDecimal(Double.toString(centerReal)),
                 new BigDecimal(Double.toString(centerImag)),
                 new BigDecimal(Double.toString(zoomLevel)),
                 maxIterations);
        }
    }

    /** Callback for frame progress reporting. */
    public interface ProgressCallback {
        void onFrame(int frameIndex, int totalFrames, long renderTimeMs);
    }

    private final FractalRenderer renderer;
    private final ColorGradient gradient;
    private final List<Keyframe> keyframes = new ArrayList<>();
    private int framesPerSegment = 30;
    private int width = 640;
    private int height = 480;
    private volatile boolean cancelled = false;

    public ZoomAnimator(FractalRenderer renderer, ColorGradient gradient) {
        this.renderer = renderer;
        this.gradient = gradient;
    }

    public void addKeyframe(Keyframe kf) { keyframes.add(kf); }
    public void clearKeyframes() { keyframes.clear(); }
    public List<Keyframe> getKeyframes() { return keyframes; }

    public void setFramesPerSegment(int fps) { this.framesPerSegment = fps; }
    public int getFramesPerSegment() { return framesPerSegment; }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void cancel() { cancelled = true; }

    public int getTotalFrames() {
        if (keyframes.size() < 2) return keyframes.size();
        return (keyframes.size() - 1) * framesPerSegment + 1;
    }

    /**
     * Render all frames and save to outputDir as frame_0000.png, frame_0001.png, etc.
     * Returns the number of frames rendered.
     */
    public int renderToFiles(File outputDir, ProgressCallback callback) throws Exception {
        if (keyframes.size() < 2) {
            throw new IllegalStateException("Need at least 2 keyframes for animation");
        }
        outputDir.mkdirs();
        cancelled = false;

        int totalFrames = getTotalFrames();
        int frameIndex = 0;

        for (int seg = 0; seg < keyframes.size() - 1; seg++) {
            Keyframe from = keyframes.get(seg);
            Keyframe to = keyframes.get(seg + 1);
            int segFrames = (seg == keyframes.size() - 2)
                    ? framesPerSegment + 1  // include final frame
                    : framesPerSegment;

            for (int f = 0; f < segFrames; f++) {
                if (cancelled) return frameIndex;

                double t = (double) f / framesPerSegment;
                Keyframe interpolated = interpolate(from, to, t);

                long t0 = System.currentTimeMillis();
                BufferedImage frame = renderFrame(interpolated);
                long renderTime = System.currentTimeMillis() - t0;

                String filename = String.format("frame_%04d.png", frameIndex);
                ImageIO.write(frame, "PNG", new File(outputDir, filename));

                if (callback != null) {
                    callback.onFrame(frameIndex, totalFrames, renderTime);
                }
                frameIndex++;
            }
        }
        return frameIndex;
    }

    /**
     * Render a single frame from a keyframe specification.
     */
    public BufferedImage renderFrame(Keyframe kf) {
        BigDecimal halfRange = BigDecimal.valueOf(2).divide(kf.zoomLevel,
                new MathContext(40, RoundingMode.HALF_UP));

        renderer.setMaxIterations(kf.maxIterations);
        renderer.setBounds(
                kf.centerReal.subtract(halfRange),
                kf.centerReal.add(halfRange),
                kf.centerImag.subtract(halfRange),
                kf.centerImag.add(halfRange)
        );

        return renderer.render(width, height, gradient);
    }

    /**
     * Interpolate between two keyframes.
     * Zoom interpolation is exponential (linear in log space) for smooth zooming.
     * Position interpolation is linear in the viewport at the current zoom level.
     */
    static Keyframe interpolate(Keyframe from, Keyframe to, double t) {
        MathContext mc = new MathContext(40, RoundingMode.HALF_UP);

        // Exponential zoom interpolation: zoom = from.zoom * (to.zoom/from.zoom)^t
        double logFrom = Math.log(from.zoomLevel.doubleValue());
        double logTo = Math.log(to.zoomLevel.doubleValue());
        double logZoom = logFrom + (logTo - logFrom) * t;
        BigDecimal zoom = new BigDecimal(Double.toString(Math.exp(logZoom)));

        // Linear position interpolation
        BigDecimal bt = new BigDecimal(Double.toString(t));
        BigDecimal oneMinusT = BigDecimal.ONE.subtract(bt, mc);
        BigDecimal centerR = from.centerReal.multiply(oneMinusT, mc)
                .add(to.centerReal.multiply(bt, mc), mc);
        BigDecimal centerI = from.centerImag.multiply(oneMinusT, mc)
                .add(to.centerImag.multiply(bt, mc), mc);

        // Linear iteration interpolation
        int maxIter = (int) Math.round(from.maxIterations + (to.maxIterations - from.maxIterations) * t);

        return new Keyframe(centerR, centerI, zoom, maxIter);
    }

    /**
     * CLI entry point for rendering zoom animations.
     * Usage: ZoomAnimator <keyframes.json> <outputDir> [width] [height] [framesPerSegment]
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ZoomAnimator <keyframes.json> <outputDir> [width] [height] [fps]");
            System.exit(1);
        }

        String jsonContent = java.nio.file.Files.readString(
                new File(args[0]).toPath(), java.nio.charset.StandardCharsets.UTF_8);
        File outputDir = new File(args[1]);
        int w = args.length > 2 ? Integer.parseInt(args[2]) : 640;
        int h = args.length > 3 ? Integer.parseInt(args[3]) : 480;
        int fps = args.length > 4 ? Integer.parseInt(args[4]) : 30;

        // Parse keyframes from JSON array
        List<Keyframe> keyframes = parseKeyframes(jsonContent);

        FractalRenderer renderer = new FractalRenderer();
        ColorGradient gradient = ColorGradient.fractalDefault();

        // Set fractal type from first keyframe's context (default Mandelbrot)
        ZoomAnimator animator = new ZoomAnimator(renderer, gradient);
        animator.setSize(w, h);
        animator.setFramesPerSegment(fps);
        for (Keyframe kf : keyframes) animator.addKeyframe(kf);

        System.out.printf("Rendering %d frames at %dx%d...%n", animator.getTotalFrames(), w, h);
        long startTime = System.currentTimeMillis();

        int rendered = animator.renderToFiles(outputDir, (frame, total, renderTime) -> {
            System.out.printf("  Frame %d/%d: %dms%n", frame + 1, total, renderTime);
        });

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.printf("Done: %d frames in %s%n", rendered, formatTime(totalTime));
        System.out.printf("Output: %s%n", outputDir.getAbsolutePath());
    }

    static List<Keyframe> parseKeyframes(String json) {
        List<Keyframe> result = new ArrayList<>();
        // Simple JSON array parser: [ { centerReal, centerImag, zoom, maxIterations }, ... ]
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        // Split by "}" to find individual objects
        String[] parts = json.split("\\}");
        for (String part : parts) {
            int braceIdx = part.indexOf('{');
            if (braceIdx < 0) continue;
            String obj = part.substring(braceIdx + 1);
            java.util.Map<String, String> map = FractalJsonUtil.parseJsonFragment(obj);
            if (map.containsKey("centerReal") && map.containsKey("centerImag") && map.containsKey("zoom")) {
                result.add(new Keyframe(
                        new BigDecimal(map.get("centerReal")),
                        new BigDecimal(map.get("centerImag")),
                        new BigDecimal(map.get("zoom")),
                        Integer.parseInt(map.getOrDefault("maxIterations", "256"))
                ));
            }
        }
        return result;
    }

    private static String formatTime(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("%.1fs", sec);
        int min = (int) (sec / 60);
        return String.format("%dm%.0fs", min, sec - min * 60);
    }
}

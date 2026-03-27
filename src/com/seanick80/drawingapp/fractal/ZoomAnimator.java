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
 * Frames are rendered and exported as numbered PNG files + AVI video.
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

    /** A candidate zoom target with a visual interest score. */
    public static class ZoomTarget {
        public final BigDecimal centerReal;
        public final BigDecimal centerImag;
        public final double score;
        public final BufferedImage preview;

        public ZoomTarget(BigDecimal cr, BigDecimal ci, double score, BufferedImage preview) {
            this.centerReal = cr;
            this.centerImag = ci;
            this.score = score;
            this.preview = preview;
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
    private int fps = 30;
    private volatile boolean cancelled = false;
    private boolean boomerang = true; // zoom in then back out for looping
    private int interpolationFrames = 3; // blended frames between each rendered frame

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

    public void setFps(int fps) { this.fps = fps; }
    public int getFps() { return fps; }

    public void setBoomerang(boolean boomerang) { this.boomerang = boomerang; }

    public void setInterpolationFrames(int n) { this.interpolationFrames = Math.max(0, n); }
    public int getInterpolationFrames() { return interpolationFrames; }

    public void cancel() { cancelled = true; }

    /** Number of frames that will actually be rendered (excludes boomerang reuse and interpolation). */
    public int getRenderedFrameCount() {
        if (keyframes.size() < 2) return keyframes.size();
        return (keyframes.size() - 1) * framesPerSegment + 1;
    }

    /** Total frames in the AVI (includes interpolation and boomerang). */
    public int getTotalFrames() {
        int rendered = getRenderedFrameCount();
        if (rendered < 2) return rendered;
        // Each pair of rendered frames produces (1 + interpolationFrames) AVI frames,
        // plus the last rendered frame itself
        int forwardAvi = (rendered - 1) * (1 + interpolationFrames) + 1;
        if (boomerang) {
            // Reverse: same count minus first and last (already in forward)
            return forwardAvi + forwardAvi - 2;
        }
        return forwardAvi;
    }

    /**
     * Find interesting zoom targets by scanning for boundary regions with
     * high iteration-count variance. Returns candidates sorted by score
     * (most interesting first), each with a preview thumbnail.
     *
     * @param type       fractal type to scan
     * @param maxIter    iteration limit for the scan
     * @param count      number of candidates to return
     */
    public static List<ZoomTarget> findInterestingPoints(FractalType type, int maxIter, int count) {
        // Render a low-res iteration grid covering the default viewport
        int scanW = 200, scanH = 150;
        BigDecimal centerR = type.defaultCenterReal();
        BigDecimal centerI = type.defaultCenterImag();
        double halfRange = 2.0;
        double minR = centerR.doubleValue() - halfRange;
        double minI = centerI.doubleValue() - halfRange;
        double scaleX = (2 * halfRange) / scanW;
        double scaleY = (2 * halfRange) / scanH;

        int[] iters = new int[scanW * scanH];
        for (int y = 0; y < scanH; y++) {
            double cy = minI + y * scaleY;
            for (int x = 0; x < scanW; x++) {
                double cx = minR + x * scaleX;
                iters[y * scanW + x] = type.iterate(cx, cy, maxIter);
            }
        }

        // Score each pixel by local iteration variance in a neighborhood.
        // High variance = boundary region = visually interesting.
        int radius = 3;
        double[] scores = new double[scanW * scanH];
        for (int y = radius; y < scanH - radius; y++) {
            for (int x = radius; x < scanW - radius; x++) {
                int centerIter = iters[y * scanW + x];
                // Skip deep interior points (all neighbors also maxIter)
                if (centerIter >= maxIter) {
                    scores[y * scanW + x] = 0;
                    continue;
                }
                double sum = 0, sumSq = 0;
                int n = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int iter = iters[(y + dy) * scanW + (x + dx)];
                        sum += iter;
                        sumSq += (double) iter * iter;
                        n++;
                    }
                }
                double mean = sum / n;
                double variance = sumSq / n - mean * mean;
                // Bonus for high iteration counts (more detail at deeper zoom)
                double depthBonus = Math.log1p(mean);
                scores[y * scanW + x] = variance * depthBonus;
            }
        }

        // Find top candidates, enforcing minimum distance between them
        int minDist = 20; // pixels apart
        List<ZoomTarget> candidates = new ArrayList<>();
        boolean[] excluded = new boolean[scanW * scanH];

        for (int c = 0; c < count; c++) {
            int bestIdx = -1;
            double bestScore = -1;
            for (int i = 0; i < scores.length; i++) {
                if (!excluded[i] && scores[i] > bestScore) {
                    bestScore = scores[i];
                    bestIdx = i;
                }
            }
            if (bestIdx < 0 || bestScore <= 0) break;

            int bx = bestIdx % scanW;
            int by = bestIdx / scanW;

            // Exclude nearby pixels for diversity
            for (int y = Math.max(0, by - minDist); y < Math.min(scanH, by + minDist); y++) {
                for (int x = Math.max(0, bx - minDist); x < Math.min(scanW, bx + minDist); x++) {
                    excluded[y * scanW + x] = true;
                }
            }

            double cx = minR + bx * scaleX;
            double cy = minI + by * scaleY;

            // Render a small preview at moderate zoom centered on the candidate
            FractalRenderer previewRenderer = new FractalRenderer();
            previewRenderer.setType(type);
            previewRenderer.setMaxIterations(maxIter);
            BigDecimal cr = new BigDecimal(Double.toString(cx));
            BigDecimal ci = new BigDecimal(Double.toString(cy));
            BigDecimal previewHalf = new BigDecimal("0.02");
            previewRenderer.setBounds(
                cr.subtract(previewHalf), cr.add(previewHalf),
                ci.subtract(previewHalf), ci.add(previewHalf));
            BufferedImage preview = previewRenderer.render(120, 90,
                ColorGradient.fractalDefault());

            candidates.add(new ZoomTarget(cr, ci, bestScore, preview));
        }

        return candidates;
    }

    /**
     * Determine a good iteration count for a zoom movie that goes to the
     * given zoom depth. Enough to show detail at max zoom without being
     * wasteful at shallow zoom.
     */
    public static int iterationsForZoom(double zoomLevel, int baseIterations) {
        // At zoom 1, use base. At deeper zoom, increase with log.
        // Roughly: base + 50 * log10(zoom)
        int needed = (int) (baseIterations + 50 * Math.log10(Math.max(zoomLevel, 1)));
        return Math.max(needed, baseIterations);
    }

    /**
     * Render all frames, save as individual PNGs, and assemble into an AVI video.
     * Interpolated frames are blended between rendered frames to slow playback.
     * If boomerang is enabled, renders forward then reverse for seamless looping.
     * Returns the number of rendered frames (not counting interpolated/boomerang).
     */
    public int renderToFiles(File outputDir, ProgressCallback callback) throws Exception {
        if (keyframes.size() < 2) {
            throw new IllegalStateException("Need at least 2 keyframes for animation");
        }
        outputDir.mkdirs();
        cancelled = false;

        int renderedTotal = getRenderedFrameCount();
        int renderedIndex = 0;

        // Collect forward AVI frames (rendered + interpolated) for boomerang reuse
        List<BufferedImage> forwardAviFrames = boomerang ? new ArrayList<>() : null;
        BufferedImage prevFrame = null;

        File aviFile = new File(outputDir, "zoom.avi");
        try (AviWriter avi = new AviWriter(aviFile, width, height, fps)) {
            // Forward pass
            for (int seg = 0; seg < keyframes.size() - 1; seg++) {
                Keyframe from = keyframes.get(seg);
                Keyframe to = keyframes.get(seg + 1);
                int segFrames = (seg == keyframes.size() - 2)
                        ? framesPerSegment + 1
                        : framesPerSegment;

                for (int f = 0; f < segFrames; f++) {
                    if (cancelled) return renderedIndex;

                    double t = (double) f / framesPerSegment;
                    Keyframe interpolated = interpolate(from, to, t);

                    long t0 = System.currentTimeMillis();
                    BufferedImage frame = renderFrame(interpolated);
                    long renderTime = System.currentTimeMillis() - t0;

                    String filename = String.format("frame_%04d.png", renderedIndex);
                    ImageIO.write(frame, "PNG", new File(outputDir, filename));

                    // Write interpolated blend frames between previous and current
                    if (prevFrame != null && interpolationFrames > 0) {
                        for (int b = 1; b <= interpolationFrames; b++) {
                            float alpha = (float) b / (interpolationFrames + 1);
                            BufferedImage blended = blendFrames(prevFrame, frame, alpha);
                            avi.addFrame(blended);
                            if (boomerang) forwardAviFrames.add(blended);
                        }
                    }

                    avi.addFrame(frame);
                    if (boomerang) forwardAviFrames.add(frame);
                    prevFrame = frame;

                    if (callback != null) {
                        callback.onFrame(renderedIndex, renderedTotal, renderTime);
                    }
                    renderedIndex++;
                }
            }

            // Reverse pass: reuse forward AVI frames in reverse order
            if (boomerang && forwardAviFrames.size() > 2) {
                for (int i = forwardAviFrames.size() - 2; i >= 1; i--) {
                    if (cancelled) return renderedIndex;
                    avi.addFrame(forwardAviFrames.get(i));
                }
            }
        }
        return renderedIndex;
    }

    /**
     * Blend two frames with linear interpolation.
     * alpha=0 returns frameA, alpha=1 returns frameB.
     */
    static BufferedImage blendFrames(BufferedImage frameA, BufferedImage frameB, float alpha) {
        int w = frameA.getWidth();
        int h = frameA.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float oneMinusAlpha = 1.0f - alpha;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = frameA.getRGB(x, y);
                int rgbB = frameB.getRGB(x, y);
                int r = (int) (((rgbA >> 16) & 0xFF) * oneMinusAlpha + ((rgbB >> 16) & 0xFF) * alpha);
                int g = (int) (((rgbA >> 8) & 0xFF) * oneMinusAlpha + ((rgbB >> 8) & 0xFF) * alpha);
                int b = (int) ((rgbA & 0xFF) * oneMinusAlpha + (rgbB & 0xFF) * alpha);
                result.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return result;
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
     * Zoom is exponential (linear in log space).
     * Position pans smoothly, timed so the target stays visible at every zoom level.
     *
     * The pan uses an ease-in curve tied to zoom progress: at low zoom the entire
     * set is visible so the center barely needs to move; as zoom increases, the
     * center converges on the target. This prevents the classic problem of
     * panning through the black interior at intermediate zoom levels.
     */
    static Keyframe interpolate(Keyframe from, Keyframe to, double t) {
        MathContext mc = new MathContext(40, RoundingMode.HALF_UP);

        // Exponential zoom interpolation: zoom = from.zoom * (to.zoom/from.zoom)^t
        double logFrom = Math.log(from.zoomLevel.doubleValue());
        double logTo = Math.log(to.zoomLevel.doubleValue());
        double logZoom = logFrom + (logTo - logFrom) * t;
        BigDecimal zoom = new BigDecimal(Double.toString(Math.exp(logZoom)));

        // Position interpolation: ease-in curve so center converges on target
        // as zoom increases. At zoom Z, viewport width = 4/Z. We want the
        // remaining offset to be a fraction of the viewport, so the pan is
        // barely noticeable at each frame.
        //
        // Using t^2 ease-in: center barely moves in the first half of the
        // animation (when zoomed out, offset is invisible), then converges
        // smoothly in the second half.
        double panT = t * t;
        BigDecimal bt = new BigDecimal(Double.toString(panT));
        BigDecimal oneMinusT = BigDecimal.ONE.subtract(bt, mc);
        BigDecimal centerR = from.centerReal.multiply(oneMinusT, mc)
                .add(to.centerReal.multiply(bt, mc), mc);
        BigDecimal centerI = from.centerImag.multiply(oneMinusT, mc)
                .add(to.centerImag.multiply(bt, mc), mc);

        // Linear iteration interpolation
        int maxIter = (int) Math.round(
            from.maxIterations + (to.maxIterations - from.maxIterations) * t);

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
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        String[] parts = json.split("\\}");
        for (String part : parts) {
            int braceIdx = part.indexOf('{');
            if (braceIdx < 0) continue;
            String obj = part.substring(braceIdx + 1);
            java.util.Map<String, String> map = FractalJsonUtil.parseJsonFragment(obj);
            if (map.containsKey("centerReal") && map.containsKey("centerImag")
                    && map.containsKey("zoom")) {
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

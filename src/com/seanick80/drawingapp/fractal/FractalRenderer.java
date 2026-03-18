package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Renders fractals to BufferedImage using a ColorGradient for coloring.
 * Uses a quadtree cache to avoid recomputing iteration counts for
 * previously visited regions of the complex plane.
 * Auto-switches to BigDecimal arithmetic when zoom exceeds double precision limits.
 */
public class FractalRenderer {

    public enum RenderMode { AUTO, DOUBLE, BIGDECIMAL, PERTURBATION }
    public enum ColorMode { MOD, DIVISION }

    private static final double TOLERANCE_FRACTION = 0.1;
    private static final double BIGDECIMAL_THRESHOLD = 1e-13;

    private FractalType type = FractalType.MANDELBROT;
    private RenderMode renderMode = RenderMode.AUTO;
    private ColorMode colorMode = ColorMode.MOD;
    private static final int MOD_PALETTE_SIZE = 64;
    private BigDecimal minReal = new BigDecimal("-2");
    private BigDecimal maxReal = new BigDecimal("2");
    private BigDecimal minImag = new BigDecimal("-2");
    private BigDecimal maxImag = new BigDecimal("2");
    private int maxIterations = 256;
    private IterationQuadTree cache = new IterationQuadTree(-4, 4, -4, 4);
    private boolean lastRenderWasBigDecimal = false;
    private boolean interiorPruning = true;

    private final ReentrantLock renderLock = new ReentrantLock();

    // BigDecimal progress tracking
    private final AtomicInteger bigDecimalCompletedRows = new AtomicInteger(0);
    private volatile int bigDecimalTotalRows = 0;
    private volatile boolean renderCancelled = false;
    private volatile int[] progressiveRgb = null;

    public FractalType getType() { return type; }
    public void setType(FractalType type) {
        if (this.type != type) {
            this.type = type;
            cache.clear();
        }
    }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) {
        if (this.maxIterations != maxIterations) {
            this.maxIterations = maxIterations;
            cache.clear();
        }
    }

    public double getMinReal() { return minReal.doubleValue(); }
    public double getMaxReal() { return maxReal.doubleValue(); }
    public double getMinImag() { return minImag.doubleValue(); }
    public double getMaxImag() { return maxImag.doubleValue(); }

    public BigDecimal getMinRealBig() { return minReal; }
    public BigDecimal getMaxRealBig() { return maxReal; }
    public BigDecimal getMinImagBig() { return minImag; }
    public BigDecimal getMaxImagBig() { return maxImag; }

    public void setBounds(double minReal, double maxReal, double minImag, double maxImag) {
        setBounds(new BigDecimal(Double.toString(minReal)),
                  new BigDecimal(Double.toString(maxReal)),
                  new BigDecimal(Double.toString(minImag)),
                  new BigDecimal(Double.toString(maxImag)));
    }

    public void setBounds(BigDecimal minReal, BigDecimal maxReal, BigDecimal minImag, BigDecimal maxImag) {
        boolean changed = !this.minReal.equals(minReal) || !this.maxReal.equals(maxReal)
                       || !this.minImag.equals(minImag) || !this.maxImag.equals(maxImag);
        this.minReal = minReal;
        this.maxReal = maxReal;
        this.minImag = minImag;
        this.maxImag = maxImag;
        if (changed) cache.clear();
    }

    public double getJuliaReal() {
        return (type instanceof JuliaType jt) ? jt.getCr() : -0.7;
    }
    public double getJuliaImag() {
        return (type instanceof JuliaType jt) ? jt.getCi() : 0.27015;
    }
    public BigDecimal getJuliaRealBig() {
        return (type instanceof JuliaType jt) ? jt.getCrBig() : new BigDecimal("-0.7");
    }
    public BigDecimal getJuliaImagBig() {
        return (type instanceof JuliaType jt) ? jt.getCiBig() : new BigDecimal("0.27015");
    }

    public void setJuliaConstant(double real, double imag) {
        setJuliaConstant(new BigDecimal(Double.toString(real)),
                         new BigDecimal(Double.toString(imag)));
    }

    public void setJuliaConstant(BigDecimal real, BigDecimal imag) {
        JuliaType newJulia = new JuliaType(real, imag);
        if (!(type instanceof JuliaType old) ||
            old.getCr() != newJulia.getCr() || old.getCi() != newJulia.getCi()) {
            this.type = newJulia;
            cache.clear();
        }
    }

    public RenderMode getRenderMode() { return renderMode; }
    public void setRenderMode(RenderMode mode) { this.renderMode = mode; }

    public ColorMode getColorMode() { return colorMode; }
    public void setColorMode(ColorMode mode) { this.colorMode = mode; }

    public IterationQuadTree getCache() { return cache; }

    public boolean isLastRenderBigDecimal() { return lastRenderWasBigDecimal; }

    public boolean isInteriorPruning() { return interiorPruning; }
    public void setInteriorPruning(boolean enabled) { this.interiorPruning = enabled; }

    public boolean needsBigDecimal() {
        BigDecimal rangeReal = maxReal.subtract(minReal);
        BigDecimal rangeImag = maxImag.subtract(minImag);
        return rangeReal.abs().doubleValue() < BIGDECIMAL_THRESHOLD
            || rangeImag.abs().doubleValue() < BIGDECIMAL_THRESHOLD;
    }

    public double getBigDecimalProgress() {
        return bigDecimalTotalRows > 0
            ? (double) bigDecimalCompletedRows.get() / bigDecimalTotalRows : 0;
    }

    public void cancelRender() { renderCancelled = true; }

    public int[] getProgressiveRgb() { return progressiveRgb; }

    /**
     * Render the fractal into a BufferedImage, coloring with the given gradient.
     * Points inside the set (iterations == maxIterations) are colored black.
     * Auto-switches between double and BigDecimal based on zoom depth.
     */
    public BufferedImage render(int width, int height, ColorGradient gradient) {
        try {
            renderLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            BigDecimal rangeReal = maxReal.subtract(minReal);
            BigDecimal rangeImag = maxImag.subtract(minImag);

            boolean useBigDecimal;
            boolean usePerturbationOnly = false;
            boolean useBigDecimalOnly = false;

            switch (renderMode) {
                case DOUBLE:
                    useBigDecimal = false;
                    break;
                case BIGDECIMAL:
                    useBigDecimal = true;
                    useBigDecimalOnly = true;
                    break;
                case PERTURBATION:
                    useBigDecimal = true;
                    usePerturbationOnly = true;
                    break;
                default: // AUTO
                    useBigDecimal = rangeReal.abs().doubleValue() < BIGDECIMAL_THRESHOLD
                                 || rangeImag.abs().doubleValue() < BIGDECIMAL_THRESHOLD;
                    break;
            }

            if (useBigDecimal != lastRenderWasBigDecimal) {
                cache.clear();
                lastRenderWasBigDecimal = useBigDecimal;
            }

            if (useBigDecimal) {
                if (useBigDecimalOnly) {
                    return renderPureBigDecimal(width, height, gradient, rangeReal, rangeImag);
                }
                return renderBigDecimal(width, height, gradient, rangeReal, rangeImag);
            } else {
                return renderDouble(width, height, gradient);
            }
        } finally {
            renderLock.unlock();
        }
    }

    private int[] buildColorLut(ColorGradient gradient) {
        int size = (colorMode == ColorMode.MOD) ? MOD_PALETTE_SIZE : maxIterations;
        Color[] colors = gradient.toColors(size);
        int[] lut = new int[size];
        for (int i = 0; i < size; i++) lut[i] = colors[i].getRGB();
        return lut;
    }

    private int colorForIter(int iter, int[] lut) {
        if (iter >= maxIterations) return Color.BLACK.getRGB();
        if (colorMode == ColorMode.MOD) return lut[iter % lut.length];
        return lut[iter];
    }

    private BufferedImage renderDouble(int width, int height, ColorGradient gradient) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rgb = new int[width * height];

        int[] lut = buildColorLut(gradient);

        double dMinReal = minReal.doubleValue();
        double dMaxReal = maxReal.doubleValue();
        double dMinImag = minImag.doubleValue();
        double dMaxImag = maxImag.doubleValue();
        double rangeReal = dMaxReal - dMinReal;
        double rangeImag = dMaxImag - dMinImag;
        double aspect = (double) width / height;
        double centerReal = (dMinReal + dMaxReal) / 2;
        double centerImag = (dMinImag + dMaxImag) / 2;

        double viewReal, viewImag;
        if (rangeReal / rangeImag > aspect) {
            viewReal = rangeReal;
            viewImag = rangeReal / aspect;
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag * aspect;
        }

        double finalMinReal = centerReal - viewReal / 2;
        double finalMinImag = centerImag - viewImag / 2;
        double scaleX = viewReal / (width - 1);
        double scaleY = viewImag / (height - 1);
        double tolerance = Math.min(scaleX, scaleY) * TOLERANCE_FRACTION;

        int[] iters = new int[width * height];
        boolean[] cacheHit = new boolean[width * height];

        cache.resetStats();

        IntStream.range(0, height).parallel().forEach(row -> {
            double cy = finalMinImag + row * scaleY;
            for (int col = 0; col < width; col++) {
                double cx = finalMinReal + col * scaleX;
                int idx = row * width + col;

                int iter = cache.lookup(cx, cy, tolerance);
                if (iter != IterationQuadTree.CACHE_MISS) {
                    cacheHit[idx] = true;
                    iters[idx] = iter;
                } else {
                    iter = type.iterate(cx, cy, maxIterations);
                    iters[idx] = iter;
                }

                rgb[idx] = colorForIter(iters[idx], lut);
            }
        });

        for (int row = 0; row < height; row++) {
            double cy = finalMinImag + row * scaleY;
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                if (!cacheHit[idx]) {
                    double cx = finalMinReal + col * scaleX;
                    cache.insert(cx, cy, iters[idx]);
                }
            }
        }

        double marginR = viewReal;
        double marginI = viewImag;
        cache.pruneOutside(
            finalMinReal - marginR, finalMinReal + viewReal + marginR,
            finalMinImag - marginI, finalMinImag + viewImag + marginI
        );

        image.setRGB(0, 0, width, height, rgb, 0, width);
        return image;
    }

    private BufferedImage renderBigDecimal(int width, int height, ColorGradient gradient,
                                           BigDecimal rangeReal, BigDecimal rangeImag) {
        int[] rgb = new int[width * height];
        progressiveRgb = rgb;
        bigDecimalCompletedRows.set(0);
        bigDecimalTotalRows = height;
        renderCancelled = false;

        int[] lut = buildColorLut(gradient);

        // Calculate precision: 20 + log10(zoom)
        double zoom = 4.0 / Math.min(rangeReal.abs().doubleValue(), rangeImag.abs().doubleValue());
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);

        BigDecimal bdAspect = new BigDecimal(width).divide(new BigDecimal(height), mc);
        BigDecimal centerReal = minReal.add(maxReal, mc).divide(BigDecimal.valueOf(2), mc);
        BigDecimal centerImag = minImag.add(maxImag, mc).divide(BigDecimal.valueOf(2), mc);

        BigDecimal ratioRI = rangeReal.divide(rangeImag, mc);
        BigDecimal viewReal, viewImag;
        if (ratioRI.compareTo(bdAspect) > 0) {
            viewReal = rangeReal;
            viewImag = rangeReal.divide(bdAspect, mc);
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag.multiply(bdAspect, mc);
        }

        BigDecimal scaleX = viewReal.divide(new BigDecimal(width - 1), mc);
        BigDecimal scaleY = viewImag.divide(new BigDecimal(height - 1), mc);

        // --- Perturbation theory: one BigDecimal reference orbit, all pixels in double ---

        PerturbationStrategy strategy = type.getPerturbationStrategy();

        // 1. Compute reference orbit at center using BigDecimal
        double[] refZr = new double[maxIterations + 1];
        double[] refZi = new double[maxIterations + 1];

        int refEscapeIter = strategy.computeReferenceOrbit(
            centerReal, centerImag, maxIterations, mc, refZr, refZi);

        // 2. Per-pixel deltas computed in double (pixel offset from center)
        double dScaleX = scaleX.doubleValue();
        double dScaleY = scaleY.doubleValue();
        double halfW = (width - 1) / 2.0;
        double halfH = (height - 1) / 2.0;

        // For BigDecimal fallback on glitched pixels
        BigDecimal finalMinReal = centerReal.subtract(viewReal.divide(BigDecimal.valueOf(2), mc), mc);
        BigDecimal finalMinImag = centerImag.subtract(viewImag.divide(BigDecimal.valueOf(2), mc), mc);

        // 3. Interior pruning (Mariani-Silver): divide image into large blocks,
        //    iterate every boundary pixel with BigDecimal. If all boundary pixels
        //    are interior (maxIterations), the entire block is interior — fill black.
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool;
        int blackRgb = Color.BLACK.getRGB();

        int blockW = 50, blockH = 100;
        int blocksX = (width + blockW - 1) / blockW;
        int blocksY = (height + blockH - 1) / blockH;
        boolean[] interiorBlock = new boolean[blocksX * blocksY];

        if (interiorPruning && !renderCancelled) {
            // Phase 1: Check every boundary pixel of each block using BigDecimal
            pool = Executors.newFixedThreadPool(nThreads);
            for (int by = 0; by < blocksY; by++) {
                for (int bx = 0; bx < blocksX; bx++) {
                    final int fbx = bx, fby = by;
                    pool.submit(() -> {
                        if (renderCancelled) return;
                        int startX = fbx * blockW;
                        int startY = fby * blockH;
                        int endX = Math.min(startX + blockW, width);
                        int endY = Math.min(startY + blockH, height);
                        int bIdx = fby * blocksX + fbx;

                        // Iterate all boundary pixels
                        boolean allInterior = true;
                        // Top and bottom edges
                        for (int px = startX; px < endX && allInterior; px++) {
                            if (renderCancelled) return;
                            allInterior = isBoundaryPixelInterior(px, startY,
                                finalMinReal, finalMinImag, scaleX, scaleY, mc);
                            if (allInterior && endY - 1 > startY) {
                                allInterior = isBoundaryPixelInterior(px, endY - 1,
                                    finalMinReal, finalMinImag, scaleX, scaleY, mc);
                            }
                        }
                        // Left and right edges (excluding corners already checked)
                        for (int py = startY + 1; py < endY - 1 && allInterior; py++) {
                            if (renderCancelled) return;
                            allInterior = isBoundaryPixelInterior(startX, py,
                                finalMinReal, finalMinImag, scaleX, scaleY, mc);
                            if (allInterior && endX - 1 > startX) {
                                allInterior = isBoundaryPixelInterior(endX - 1, py,
                                    finalMinReal, finalMinImag, scaleX, scaleY, mc);
                            }
                        }

                        if (allInterior) {
                            interiorBlock[bIdx] = true;
                            // Fill entire block with black
                            for (int py = startY; py < endY; py++) {
                                for (int px = startX; px < endX; px++) {
                                    rgb[py * width + px] = blackRgb;
                                }
                            }
                        }
                    });
                }
            }
            pool.shutdown();
            try {
                while (!pool.isTerminated()) {
                    pool.awaitTermination(100, TimeUnit.MILLISECONDS);
                    if (renderCancelled) { pool.shutdownNow(); break; }
                }
            } catch (InterruptedException e) {
                renderCancelled = true;
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Phase 2: Perturbation iteration for non-interior pixels
        pool = Executors.newFixedThreadPool(nThreads);

        for (int row = 0; row < height; row++) {
            final int r = row;
            final int rowBlockY = r / blockH;
            pool.submit(() -> {
                if (renderCancelled) return;
                double dci = (r - halfH) * dScaleY;
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    int blockIdx = rowBlockY * blocksX + (col / blockW);
                    if (interiorBlock[blockIdx]) {
                        continue; // Already filled with black
                    }
                    double dcr = (col - halfW) * dScaleX;
                    int idx = r * width + col;

                    int iter = strategy.perturbIterate(refZr, refZi, dcr, dci, refEscapeIter, maxIterations);

                    if (iter == PerturbationStrategy.GLITCH_DETECTED) {
                        // Fallback to full BigDecimal for this pixel
                        BigDecimal cx = finalMinReal.add(scaleX.multiply(new BigDecimal(col), mc), mc);
                        BigDecimal cy = finalMinImag.add(scaleY.multiply(new BigDecimal(r), mc), mc);
                        iter = type.iterateBig(cx, cy, maxIterations, mc);
                    }

                    rgb[idx] = colorForIter(iter, lut);
                }
                bigDecimalCompletedRows.incrementAndGet();
            });
        }

        pool.shutdown();
        try {
            while (!pool.isTerminated()) {
                pool.awaitTermination(100, TimeUnit.MILLISECONDS);
                if (renderCancelled) {
                    pool.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException e) {
            renderCancelled = true;
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, rgb, 0, width);
        progressiveRgb = null;
        return image;
    }

    private boolean isBoundaryPixelInterior(int px, int py,
                                               BigDecimal minR, BigDecimal minI,
                                               BigDecimal scaleX, BigDecimal scaleY,
                                               MathContext mc) {
        BigDecimal cx = minR.add(scaleX.multiply(new BigDecimal(px), mc), mc);
        BigDecimal cy = minI.add(scaleY.multiply(new BigDecimal(py), mc), mc);
        return type.iterateBig(cx, cy, maxIterations, mc) >= maxIterations;
    }

    /**
     * Pure BigDecimal rendering: every pixel computed individually with BigDecimal.
     * No perturbation theory. Used when RenderMode.BIGDECIMAL is forced.
     */
    private BufferedImage renderPureBigDecimal(int width, int height, ColorGradient gradient,
                                                BigDecimal rangeReal, BigDecimal rangeImag) {
        int[] rgb = new int[width * height];
        progressiveRgb = rgb;
        bigDecimalCompletedRows.set(0);
        bigDecimalTotalRows = height;
        renderCancelled = false;

        int[] lut = buildColorLut(gradient);

        double zoom = 4.0 / Math.min(rangeReal.abs().doubleValue(), rangeImag.abs().doubleValue());
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);

        BigDecimal bdAspect = new BigDecimal(width).divide(new BigDecimal(height), mc);
        BigDecimal centerReal = minReal.add(maxReal, mc).divide(BigDecimal.valueOf(2), mc);
        BigDecimal centerImag = minImag.add(maxImag, mc).divide(BigDecimal.valueOf(2), mc);

        BigDecimal ratioRI = rangeReal.divide(rangeImag, mc);
        BigDecimal viewReal, viewImag;
        if (ratioRI.compareTo(bdAspect) > 0) {
            viewReal = rangeReal;
            viewImag = rangeReal.divide(bdAspect, mc);
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag.multiply(bdAspect, mc);
        }

        BigDecimal scaleX = viewReal.divide(new BigDecimal(width - 1), mc);
        BigDecimal scaleY = viewImag.divide(new BigDecimal(height - 1), mc);
        BigDecimal finalMinReal = centerReal.subtract(viewReal.divide(BigDecimal.valueOf(2), mc), mc);
        BigDecimal finalMinImag = centerImag.subtract(viewImag.divide(BigDecimal.valueOf(2), mc), mc);

        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        for (int row = 0; row < height; row++) {
            final int r = row;
            pool.submit(() -> {
                if (renderCancelled) return;
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    BigDecimal cx = finalMinReal.add(scaleX.multiply(new BigDecimal(col), mc), mc);
                    BigDecimal cy = finalMinImag.add(scaleY.multiply(new BigDecimal(r), mc), mc);
                    int iter = type.iterateBig(cx, cy, maxIterations, mc);
                    int idx = r * width + col;
                    rgb[idx] = colorForIter(iter, lut);
                }
                bigDecimalCompletedRows.incrementAndGet();
            });
        }

        pool.shutdown();
        try {
            while (!pool.isTerminated()) {
                pool.awaitTermination(100, TimeUnit.MILLISECONDS);
                if (renderCancelled) {
                    pool.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException e) {
            renderCancelled = true;
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, rgb, 0, width);
        progressiveRgb = null;
        return image;
    }

}

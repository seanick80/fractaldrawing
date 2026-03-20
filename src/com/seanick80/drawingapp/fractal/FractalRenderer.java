package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final double BIGDECIMAL_THRESHOLD = 1e-13;

    private FractalType type = FractalType.MANDELBROT;
    private RenderMode renderMode = RenderMode.AUTO;
    private ColorMode colorMode = ColorMode.MOD;
    private BigDecimal minReal = new BigDecimal("-2");
    private BigDecimal maxReal = new BigDecimal("2");
    private BigDecimal minImag = new BigDecimal("-2");
    private BigDecimal maxImag = new BigDecimal("2");
    private int maxIterations = 256;
    private IterationQuadTree cache = new IterationQuadTree(-4, 4, -4, 4);
    private boolean lastRenderWasBigDecimal = false;
    private boolean interiorPruning = true;

    private final ReentrantLock renderLock = new ReentrantLock();

    // Pre-allocated buffers (resized lazily when dimensions change)
    private int[] renderRgb = null;
    private int[] renderIters = null;
    private boolean[] renderCacheHit = null;
    private double[] refOrbitRe = null;
    private double[] refOrbitIm = null;
    private int bufferWidth, bufferHeight;
    private int refOrbitCapacity;

    // BigDecimal progress tracking
    private final AtomicInteger bigDecimalCompletedRows = new AtomicInteger(0);
    private volatile int bigDecimalTotalRows = 0;
    private volatile boolean renderCancelled = false;
    private volatile int[] progressiveRgb = null;

    private void ensureBuffers(int width, int height) {
        int size = width * height;
        if (renderRgb == null || bufferWidth != width || bufferHeight != height) {
            renderRgb = new int[size];
            renderIters = new int[size];
            renderCacheHit = new boolean[size];
            bufferWidth = width;
            bufferHeight = height;
        }
    }

    private void ensureRefOrbit(int maxIter) {
        int needed = maxIter + 1;
        if (refOrbitRe == null || refOrbitCapacity < needed) {
            refOrbitRe = new double[needed];
            refOrbitIm = new double[needed];
            refOrbitCapacity = needed;
        }
    }

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
                if (useBigDecimalOnly || !type.supportsPerturbation()) {
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

    private FractalColorMapper buildColorMapper(ColorGradient gradient) {
        return new FractalColorMapper(gradient, maxIterations, colorMode);
    }

    private BufferedImage renderDouble(int width, int height, ColorGradient gradient) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ensureBuffers(width, height);
        int[] rgb = renderRgb;
        int[] iters = renderIters;
        boolean[] cacheHit = renderCacheHit;
        java.util.Arrays.fill(cacheHit, false);

        FractalColorMapper mapper = buildColorMapper(gradient);
        ViewportCalculator.DoubleViewport vp = ViewportCalculator.computeDouble(
                minReal, maxReal, minImag, maxImag, width, height);

        cache.resetStats();

        IntStream.range(0, height).parallel().forEach(row -> {
            double cy = vp.minImag + row * vp.scaleY;
            for (int col = 0; col < width; col++) {
                double cx = vp.minReal + col * vp.scaleX;
                int idx = row * width + col;

                int iter = cache.lookup(cx, cy, vp.tolerance);
                if (iter != IterationQuadTree.CACHE_MISS) {
                    cacheHit[idx] = true;
                    iters[idx] = iter;
                } else {
                    iter = type.iterate(cx, cy, maxIterations);
                    iters[idx] = iter;
                }

                rgb[idx] = mapper.colorForIter(iters[idx]);
            }
        });

        for (int row = 0; row < height; row++) {
            double cy = vp.minImag + row * vp.scaleY;
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                if (!cacheHit[idx]) {
                    double cx = vp.minReal + col * vp.scaleX;
                    cache.insert(cx, cy, iters[idx]);
                }
            }
        }

        cache.pruneOutside(
            vp.minReal - vp.viewReal, vp.minReal + vp.viewReal + vp.viewReal,
            vp.minImag - vp.viewImag, vp.minImag + vp.viewImag + vp.viewImag
        );

        image.setRGB(0, 0, width, height, rgb, 0, width);
        return image;
    }

    private BufferedImage renderBigDecimal(int width, int height, ColorGradient gradient,
                                           BigDecimal rangeReal, BigDecimal rangeImag) {
        ensureBuffers(width, height);
        int[] rgb = renderRgb;
        java.util.Arrays.fill(rgb, 0);
        progressiveRgb = rgb;
        bigDecimalCompletedRows.set(0);
        bigDecimalTotalRows = height;
        renderCancelled = false;

        FractalColorMapper mapper = buildColorMapper(gradient);

        // Calculate precision: 20 + log10(zoom)
        double zoom = 4.0 / Math.min(rangeReal.abs().doubleValue(), rangeImag.abs().doubleValue());
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);

        ViewportCalculator.BigViewport vp = ViewportCalculator.computeBig(
                minReal, maxReal, minImag, maxImag, rangeReal, rangeImag, width, height, mc);

        // --- Perturbation theory: one BigDecimal reference orbit, all pixels in double ---

        PerturbationStrategy strategy = type.getPerturbationStrategy();

        // 1. Compute reference orbit at center using BigDecimal
        ensureRefOrbit(maxIterations);
        double[] refZr = refOrbitRe;
        double[] refZi = refOrbitIm;

        int refEscapeIter = strategy.computeReferenceOrbit(
            vp.centerReal, vp.centerImag, maxIterations, mc, refZr, refZi);

        // 2. Per-pixel deltas computed in double (pixel offset from center)
        double dScaleX = vp.scaleX.doubleValue();
        double dScaleY = vp.scaleY.doubleValue();
        double halfW = (width - 1) / 2.0;
        double halfH = (height - 1) / 2.0;

        // Pre-compute BigDecimal pixel coordinates for boundary checks and glitch fallback
        BigDecimal[] pixelCx = new BigDecimal[width];
        BigDecimal[] pixelCy = new BigDecimal[height];
        for (int col = 0; col < width; col++) {
            pixelCx[col] = vp.minReal.add(vp.scaleX.multiply(BigDecimal.valueOf(col), mc), mc);
        }
        for (int row = 0; row < height; row++) {
            pixelCy[row] = vp.minImag.add(vp.scaleY.multiply(BigDecimal.valueOf(row), mc), mc);
        }

        // 3. Hierarchical interior pruning: recursively subdivide the image into blocks.
        //    If a block's boundary pixels are all interior (maxIterations), skip the
        //    entire block. Otherwise subdivide into 4 quadrants down to MIN_BLOCK_SIZE.
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool;
        int blackRgb = Color.BLACK.getRGB();

        boolean[] interiorPixel = new boolean[width * height];

        if (interiorPruning && !renderCancelled) {
            // Submit a grid of initial blocks for parallelism, each with hierarchical subdivision
            int initBlockW = 50, initBlockH = 50;
            pool = Executors.newFixedThreadPool(nThreads);
            for (int by = 0; by < height; by += initBlockH) {
                for (int bx = 0; bx < width; bx += initBlockW) {
                    final int sx = bx, sy = by;
                    final int ex = Math.min(bx + initBlockW, width);
                    final int ey = Math.min(by + initBlockH, height);
                    pool.submit(() -> {
                        if (renderCancelled) return;
                        pruneInteriorHierarchical(sx, sy, ex, ey,
                            pixelCx, pixelCy, mc, interiorPixel, rgb, width, blackRgb);
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
            pool.submit(() -> {
                if (renderCancelled) return;
                double dci = (r - halfH) * dScaleY;
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    if (interiorPixel[r * width + col]) {
                        continue; // Already filled with black by pruning
                    }
                    double dcr = (col - halfW) * dScaleX;
                    int idx = r * width + col;

                    int iter = strategy.perturbIterate(refZr, refZi, dcr, dci, refEscapeIter, maxIterations);

                    if (iter == PerturbationStrategy.GLITCH_DETECTED) {
                        // Fallback to full BigDecimal for this pixel
                        iter = type.iterateBig(pixelCx[col], pixelCy[r], maxIterations, mc);
                    }

                    rgb[idx] = mapper.colorForIter(iter);
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

    private static final int MIN_BLOCK_SIZE = 8;

    /**
     * Hierarchical interior pruning. Checks boundary pixels of the given rectangle.
     * If all are interior (maxIterations), fills the block black and marks pixels
     * as interior. Otherwise subdivides into 4 quadrants and recurses.
     */
    private void pruneInteriorHierarchical(int startX, int startY, int endX, int endY,
                                            BigDecimal[] pixelCx, BigDecimal[] pixelCy,
                                            MathContext mc, boolean[] interiorPixel,
                                            int[] rgb, int imgWidth, int blackRgb) {
        if (renderCancelled) return;
        int blockW = endX - startX;
        int blockH = endY - startY;
        if (blockW <= 1 || blockH <= 1) return;

        // Quick corner check: if no corners are interior, skip this block entirely
        boolean anyCornerInterior =
            type.iterateBig(pixelCx[startX], pixelCy[startY], maxIterations, mc) >= maxIterations
            || type.iterateBig(pixelCx[endX - 1], pixelCy[startY], maxIterations, mc) >= maxIterations
            || type.iterateBig(pixelCx[startX], pixelCy[endY - 1], maxIterations, mc) >= maxIterations
            || type.iterateBig(pixelCx[endX - 1], pixelCy[endY - 1], maxIterations, mc) >= maxIterations;
        if (!anyCornerInterior) return;

        // Check all boundary pixels, tracking both allInterior and anyInterior
        boolean allInterior = true;
        boolean anyInterior = false;

        // Top and bottom edges
        for (int px = startX; px < endX; px++) {
            if (renderCancelled) return;
            if (type.iterateBig(pixelCx[px], pixelCy[startY], maxIterations, mc) >= maxIterations) {
                anyInterior = true;
            } else {
                allInterior = false;
            }
            if (endY - 1 > startY) {
                if (type.iterateBig(pixelCx[px], pixelCy[endY - 1], maxIterations, mc) >= maxIterations) {
                    anyInterior = true;
                } else {
                    allInterior = false;
                }
            }
            // Early exit: if we've seen both types we know it's mixed
            if (anyInterior && !allInterior && (blockW <= MIN_BLOCK_SIZE && blockH <= MIN_BLOCK_SIZE)) return;
        }
        // Left and right edges (excluding corners)
        for (int py = startY + 1; py < endY - 1; py++) {
            if (renderCancelled) return;
            if (type.iterateBig(pixelCx[startX], pixelCy[py], maxIterations, mc) >= maxIterations) {
                anyInterior = true;
            } else {
                allInterior = false;
            }
            if (endX - 1 > startX) {
                if (type.iterateBig(pixelCx[endX - 1], pixelCy[py], maxIterations, mc) >= maxIterations) {
                    anyInterior = true;
                } else {
                    allInterior = false;
                }
            }
            if (anyInterior && !allInterior && (blockW <= MIN_BLOCK_SIZE && blockH <= MIN_BLOCK_SIZE)) return;
        }

        if (allInterior) {
            // Entire block is interior — fill black and mark
            for (int py = startY; py < endY; py++) {
                for (int px = startX; px < endX; px++) {
                    int idx = py * imgWidth + px;
                    interiorPixel[idx] = true;
                    rgb[idx] = blackRgb;
                }
            }
            return;
        }

        // No interior pixels on boundary — no point subdividing, skip
        if (!anyInterior) return;

        // Mixed boundary — subdivide if block is large enough
        if (blockW <= MIN_BLOCK_SIZE && blockH <= MIN_BLOCK_SIZE) return;

        int midX = startX + blockW / 2;
        int midY = startY + blockH / 2;

        if (blockW > MIN_BLOCK_SIZE && blockH > MIN_BLOCK_SIZE) {
            pruneInteriorHierarchical(startX, startY, midX, midY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
            pruneInteriorHierarchical(midX, startY, endX, midY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
            pruneInteriorHierarchical(startX, midY, midX, endY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
            pruneInteriorHierarchical(midX, midY, endX, endY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
        } else if (blockW > MIN_BLOCK_SIZE) {
            pruneInteriorHierarchical(startX, startY, midX, endY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
            pruneInteriorHierarchical(midX, startY, endX, endY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
        } else {
            pruneInteriorHierarchical(startX, startY, endX, midY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
            pruneInteriorHierarchical(startX, midY, endX, endY, pixelCx, pixelCy, mc, interiorPixel, rgb, imgWidth, blackRgb);
        }
    }

    /**
     * Pure BigDecimal rendering: every pixel computed individually with BigDecimal.
     * No perturbation theory. Used when RenderMode.BIGDECIMAL is forced.
     */
    private BufferedImage renderPureBigDecimal(int width, int height, ColorGradient gradient,
                                                BigDecimal rangeReal, BigDecimal rangeImag) {
        ensureBuffers(width, height);
        int[] rgb = renderRgb;
        java.util.Arrays.fill(rgb, 0);
        progressiveRgb = rgb;
        bigDecimalCompletedRows.set(0);
        bigDecimalTotalRows = height;
        renderCancelled = false;

        FractalColorMapper mapper = buildColorMapper(gradient);

        double zoom = 4.0 / Math.min(rangeReal.abs().doubleValue(), rangeImag.abs().doubleValue());
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);

        ViewportCalculator.BigViewport vp = ViewportCalculator.computeBig(
                minReal, maxReal, minImag, maxImag, rangeReal, rangeImag, width, height, mc);

        // Pre-compute BigDecimal pixel coordinates
        BigDecimal[] pxCx = new BigDecimal[width];
        BigDecimal[] pxCy = new BigDecimal[height];
        for (int col = 0; col < width; col++) {
            pxCx[col] = vp.minReal.add(vp.scaleX.multiply(BigDecimal.valueOf(col), mc), mc);
        }
        for (int row = 0; row < height; row++) {
            pxCy[row] = vp.minImag.add(vp.scaleY.multiply(BigDecimal.valueOf(row), mc), mc);
        }

        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        for (int row = 0; row < height; row++) {
            final int r = row;
            pool.submit(() -> {
                if (renderCancelled) return;
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    int iter = type.iterateBig(pxCx[col], pxCy[r], maxIterations, mc);
                    int idx = r * width + col;
                    rgb[idx] = mapper.colorForIter(iter);
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

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
import java.util.stream.IntStream;

/**
 * Renders fractals to BufferedImage using a ColorGradient for coloring.
 * Uses a quadtree cache to avoid recomputing iteration counts for
 * previously visited regions of the complex plane.
 * Auto-switches to BigDecimal arithmetic when zoom exceeds double precision limits.
 */
public class FractalRenderer {

    public enum RenderMode { AUTO, DOUBLE, BIGDECIMAL, PERTURBATION }

    private static final double TOLERANCE_FRACTION = 0.1;
    private static final double BIGDECIMAL_THRESHOLD = 1e-13;

    private FractalType type = FractalType.MANDELBROT;
    private RenderMode renderMode = RenderMode.AUTO;
    private BigDecimal minReal = new BigDecimal("-2");
    private BigDecimal maxReal = new BigDecimal("2");
    private BigDecimal minImag = new BigDecimal("-2");
    private BigDecimal maxImag = new BigDecimal("2");
    private int maxIterations = 256;
    private BigDecimal juliaReal = new BigDecimal("-0.7");
    private BigDecimal juliaImag = new BigDecimal("0.27015");
    private IterationQuadTree cache = new IterationQuadTree(-4, 4, -4, 4);
    private boolean lastRenderWasBigDecimal = false;

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

    public double getJuliaReal() { return juliaReal.doubleValue(); }
    public double getJuliaImag() { return juliaImag.doubleValue(); }

    public void setJuliaConstant(double real, double imag) {
        setJuliaConstant(new BigDecimal(Double.toString(real)),
                         new BigDecimal(Double.toString(imag)));
    }

    public void setJuliaConstant(BigDecimal real, BigDecimal imag) {
        if (!this.juliaReal.equals(real) || !this.juliaImag.equals(imag)) {
            this.juliaReal = real;
            this.juliaImag = imag;
            if (type == FractalType.JULIA) cache.clear();
        }
    }

    public RenderMode getRenderMode() { return renderMode; }
    public void setRenderMode(RenderMode mode) { this.renderMode = mode; }

    public IterationQuadTree getCache() { return cache; }

    public boolean isLastRenderBigDecimal() { return lastRenderWasBigDecimal; }

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
    public synchronized BufferedImage render(int width, int height, ColorGradient gradient) {
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
    }

    private BufferedImage renderDouble(int width, int height, ColorGradient gradient) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rgb = new int[width * height];

        Color[] lut = gradient.toColors(maxIterations);

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

        int black = Color.BLACK.getRGB();

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
                    if (type == FractalType.JULIA) {
                        iter = FractalType.iterateJulia(cx, cy,
                            juliaReal.doubleValue(), juliaImag.doubleValue(), maxIterations);
                    } else {
                        iter = type.iterate(cx, cy, maxIterations);
                    }
                    iters[idx] = iter;
                }

                rgb[idx] = (iters[idx] >= maxIterations) ? black : lut[iters[idx]].getRGB();
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

    private static final int GLITCH_DETECTED = -2;

    private BufferedImage renderBigDecimal(int width, int height, ColorGradient gradient,
                                           BigDecimal rangeReal, BigDecimal rangeImag) {
        int[] rgb = new int[width * height];
        progressiveRgb = rgb;
        bigDecimalCompletedRows.set(0);
        bigDecimalTotalRows = height;
        renderCancelled = false;

        Color[] lut = gradient.toColors(maxIterations);

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

        // 1. Compute reference orbit at center using BigDecimal
        double[] refZr = new double[maxIterations + 1];
        double[] refZi = new double[maxIterations + 1];
        boolean isJulia = (type == FractalType.JULIA);

        if (isJulia) {
            computeReferenceOrbitJulia(centerReal, centerImag, refZr, refZi, mc);
        } else {
            computeReferenceOrbitMandelbrot(centerReal, centerImag, refZr, refZi, mc);
        }

        // 2. Per-pixel deltas computed in double (pixel offset from center)
        double dScaleX = scaleX.doubleValue();
        double dScaleY = scaleY.doubleValue();
        double halfW = (width - 1) / 2.0;
        double halfH = (height - 1) / 2.0;

        int black = Color.BLACK.getRGB();

        // For BigDecimal fallback on glitched pixels
        BigDecimal finalMinReal = centerReal.subtract(viewReal.divide(BigDecimal.valueOf(2), mc), mc);
        BigDecimal finalMinImag = centerImag.subtract(viewImag.divide(BigDecimal.valueOf(2), mc), mc);
        BigDecimal jrBig = juliaReal;
        BigDecimal jiBig = juliaImag;

        // 3. Parallel perturbation iteration
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        for (int row = 0; row < height; row++) {
            final int r = row;
            pool.submit(() -> {
                if (renderCancelled) return;
                double dci = (r - halfH) * dScaleY;
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    double dcr = (col - halfW) * dScaleX;
                    int idx = r * width + col;

                    int iter = perturbIterate(refZr, refZi, dcr, dci, isJulia);

                    if (iter == GLITCH_DETECTED) {
                        // Fallback to full BigDecimal for this pixel
                        BigDecimal cx = finalMinReal.add(scaleX.multiply(new BigDecimal(col), mc), mc);
                        BigDecimal cy = finalMinImag.add(scaleY.multiply(new BigDecimal(r), mc), mc);
                        if (isJulia) {
                            iter = FractalType.iterateJuliaBig(cx, cy, jrBig, jiBig, maxIterations, mc);
                        } else {
                            iter = type.iterateBig(cx, cy, maxIterations, mc);
                        }
                    }

                    rgb[idx] = (iter >= maxIterations) ? black : lut[iter].getRGB();
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

        Color[] lut = gradient.toColors(maxIterations);

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

        boolean isJulia = (type == FractalType.JULIA);
        BigDecimal jrBig = juliaReal;
        BigDecimal jiBig = juliaImag;
        int black = Color.BLACK.getRGB();

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
                    int iter;
                    if (isJulia) {
                        iter = FractalType.iterateJuliaBig(cx, cy, jrBig, jiBig, maxIterations, mc);
                    } else {
                        iter = type.iterateBig(cx, cy, maxIterations, mc);
                    }
                    int idx = r * width + col;
                    rgb[idx] = (iter >= maxIterations) ? black : lut[iter].getRGB();
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

    /**
     * Compute reference orbit for Mandelbrot at (cr, ci) using BigDecimal.
     * Always iterates to maxIterations (even past escape) so perturbation
     * has reference data for all pixels regardless of when they escape.
     * Returns the iteration at which the reference escapes (or maxIterations).
     */
    private int computeReferenceOrbitMandelbrot(BigDecimal cr, BigDecimal ci,
                                                 double[] outZr, double[] outZi, MathContext mc) {
        BigDecimal zr = BigDecimal.ZERO, zi = BigDecimal.ZERO;
        BigDecimal four = BigDecimal.valueOf(4);
        BigDecimal two = BigDecimal.valueOf(2);
        int escapeIter = maxIterations;
        for (int i = 0; i < maxIterations; i++) {
            outZr[i] = zr.doubleValue();
            outZi[i] = zi.doubleValue();
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (escapeIter == maxIterations && zr2.add(zi2, mc).compareTo(four) > 0) {
                escapeIter = i;
            }
            BigDecimal newZi = two.multiply(zr, mc).multiply(zi, mc).add(ci, mc);
            zr = zr2.subtract(zi2, mc).add(cr, mc);
            zi = newZi;
        }
        outZr[maxIterations] = zr.doubleValue();
        outZi[maxIterations] = zi.doubleValue();
        return escapeIter;
    }

    /**
     * Compute reference orbit for Julia at starting point (z0r, z0i) with fixed constant.
     * Always iterates to maxIterations for full reference data.
     */
    private int computeReferenceOrbitJulia(BigDecimal z0r, BigDecimal z0i,
                                            double[] outZr, double[] outZi, MathContext mc) {
        BigDecimal zr = z0r, zi = z0i;
        BigDecimal cr = juliaReal, ci = juliaImag;
        BigDecimal four = BigDecimal.valueOf(4);
        BigDecimal two = BigDecimal.valueOf(2);
        int escapeIter = maxIterations;
        for (int i = 0; i < maxIterations; i++) {
            outZr[i] = zr.doubleValue();
            outZi[i] = zi.doubleValue();
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (escapeIter == maxIterations && zr2.add(zi2, mc).compareTo(four) > 0) {
                escapeIter = i;
            }
            BigDecimal newZi = two.multiply(zr, mc).multiply(zi, mc).add(ci, mc);
            zr = zr2.subtract(zi2, mc).add(cr, mc);
            zi = newZi;
        }
        outZr[maxIterations] = zr.doubleValue();
        outZi[maxIterations] = zi.doubleValue();
        return escapeIter;
    }

    /**
     * Perturbation iteration: compute escape time for a pixel at offset (dcr, dci)
     * from the reference orbit, using fast double arithmetic.
     *
     * Mandelbrot: δz₀ = 0, δz_{n+1} = 2·Z_n·δz_n + δz_n² + δc
     * Julia:      δz₀ = δc, δz_{n+1} = 2·Z_n·δz_n + δz_n²
     *
     * Returns iteration count, or GLITCH_DETECTED if perturbation became unreliable.
     */
    /**
     * Perturbation iteration: compute escape time for a pixel at offset (dcr, dci)
     * from the reference orbit, using fast double arithmetic.
     *
     * Mandelbrot: δz₀ = 0, δz_{n+1} = 2·Z_n·δz_n + δz_n² + δc
     * Julia:      δz₀ = δc, δz_{n+1} = 2·Z_n·δz_n + δz_n²
     *
     * Glitch detection uses the "relative size" test: a glitch occurs when the
     * perturbation δz dominates the full value Z+δz so badly that double precision
     * can't represent the sum accurately. We check |δz|² > |Z+δz|² * 1e6.
     *
     * Returns iteration count, or GLITCH_DETECTED if perturbation became unreliable.
     */
    private int perturbIterate(double[] refZr, double[] refZi,
                                double dcr, double dci, boolean isJulia) {
        double dzr = isJulia ? dcr : 0;
        double dzi = isJulia ? dci : 0;
        double addR = isJulia ? 0 : dcr;
        double addI = isJulia ? 0 : dci;

        for (int i = 0; i < maxIterations; i++) {
            double Zr = refZr[i], Zi = refZi[i];

            // δz_{n+1} = 2·Z_n·δz_n + δz_n² + δc
            double newDzr = 2 * (Zr * dzr - Zi * dzi) + dzr * dzr - dzi * dzi + addR;
            double newDzi = 2 * (Zr * dzi + Zi * dzr) + 2 * dzr * dzi + addI;
            dzr = newDzr;
            dzi = newDzi;

            // Escape check: |Z_n+1 + δz_n+1|² > 4
            double Zr1 = refZr[i + 1], Zi1 = refZi[i + 1];
            double totalR = Zr1 + dzr;
            double totalI = Zi1 + dzi;
            double totalMag2 = totalR * totalR + totalI * totalI;
            if (totalMag2 > 4.0) return i + 1;

            // Glitch: NaN/Inf means double precision completely failed
            if (Double.isNaN(dzr) || Double.isInfinite(dzr)) {
                return GLITCH_DETECTED;
            }
        }

        return maxIterations;
    }
}

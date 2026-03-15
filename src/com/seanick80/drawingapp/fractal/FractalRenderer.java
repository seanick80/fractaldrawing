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

    private static final double TOLERANCE_FRACTION = 0.1;
    private static final double BIGDECIMAL_THRESHOLD = 1e-13;

    private FractalType type = FractalType.MANDELBROT;
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
    public BufferedImage render(int width, int height, ColorGradient gradient) {
        BigDecimal rangeReal = maxReal.subtract(minReal);
        BigDecimal rangeImag = maxImag.subtract(minImag);

        boolean useBigDecimal = rangeReal.abs().doubleValue() < BIGDECIMAL_THRESHOLD
                             || rangeImag.abs().doubleValue() < BIGDECIMAL_THRESHOLD;

        if (useBigDecimal != lastRenderWasBigDecimal) {
            cache.clear();
            lastRenderWasBigDecimal = useBigDecimal;
        }

        if (useBigDecimal) {
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

        BigDecimal aspect = new BigDecimal(width).divide(new BigDecimal(height), mc);
        BigDecimal centerReal = minReal.add(maxReal, mc).divide(new BigDecimal(2), mc);
        BigDecimal centerImag = minImag.add(maxImag, mc).divide(new BigDecimal(2), mc);

        BigDecimal ratioRI = rangeReal.divide(rangeImag, mc);
        BigDecimal viewReal, viewImag;
        if (ratioRI.compareTo(aspect) > 0) {
            viewReal = rangeReal;
            viewImag = rangeReal.divide(aspect, mc);
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag.multiply(aspect, mc);
        }

        BigDecimal two = new BigDecimal(2);
        BigDecimal finalMinReal = centerReal.subtract(viewReal.divide(two, mc), mc);
        BigDecimal finalMinImag = centerImag.subtract(viewImag.divide(two, mc), mc);
        BigDecimal scaleX = viewReal.divide(new BigDecimal(width - 1), mc);
        BigDecimal scaleY = viewImag.divide(new BigDecimal(height - 1), mc);

        int black = Color.BLACK.getRGB();

        BigDecimal jrBig = juliaReal;
        BigDecimal jiBig = juliaImag;

        // Use explicit thread pool for progress tracking
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        for (int row = 0; row < height; row++) {
            final int r = row;
            pool.submit(() -> {
                if (renderCancelled) return;
                BigDecimal cy = finalMinImag.add(scaleY.multiply(new BigDecimal(r), mc), mc);
                for (int col = 0; col < width; col++) {
                    if (renderCancelled) return;
                    BigDecimal cx = finalMinReal.add(scaleX.multiply(new BigDecimal(col), mc), mc);
                    int idx = r * width + col;

                    int iter;
                    if (type == FractalType.JULIA) {
                        iter = FractalType.iterateJuliaBig(cx, cy, jrBig, jiBig, maxIterations, mc);
                    } else {
                        iter = type.iterateBig(cx, cy, maxIterations, mc);
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
}

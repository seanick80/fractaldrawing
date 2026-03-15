package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

/**
 * Renders fractals to BufferedImage using a ColorGradient for coloring.
 * Uses a quadtree cache to avoid recomputing iteration counts for
 * previously visited regions of the complex plane.
 */
public class FractalRenderer {

    private static final double TOLERANCE_FRACTION = 0.1;

    private FractalType type = FractalType.MANDELBROT;
    private double minReal = -2, maxReal = 2, minImag = -2, maxImag = 2;
    private int maxIterations = 256;
    private double juliaReal = -0.7, juliaImag = 0.27015;
    private IterationQuadTree cache = new IterationQuadTree(-4, 4, -4, 4);

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

    public double getMinReal() { return minReal; }
    public double getMaxReal() { return maxReal; }
    public double getMinImag() { return minImag; }
    public double getMaxImag() { return maxImag; }

    public void setBounds(double minReal, double maxReal, double minImag, double maxImag) {
        this.minReal = minReal;
        this.maxReal = maxReal;
        this.minImag = minImag;
        this.maxImag = maxImag;
    }

    public double getJuliaReal() { return juliaReal; }
    public double getJuliaImag() { return juliaImag; }
    public void setJuliaConstant(double real, double imag) {
        if (this.juliaReal != real || this.juliaImag != imag) {
            this.juliaReal = real;
            this.juliaImag = imag;
            if (type == FractalType.JULIA) cache.clear();
        }
    }

    public IterationQuadTree getCache() { return cache; }

    /**
     * Render the fractal into a BufferedImage, coloring with the given gradient.
     * Points inside the set (iterations == maxIterations) are colored black.
     * Uses parallel streams for performance and quadtree cache for reuse.
     */
    public BufferedImage render(int width, int height, ColorGradient gradient) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rgb = new int[width * height];

        // Precompute gradient lookup table
        Color[] lut = gradient.toColors(maxIterations);

        // Maintain aspect ratio
        double rangeReal = maxReal - minReal;
        double rangeImag = maxImag - minImag;
        double aspect = (double) width / height;
        double centerReal = (minReal + maxReal) / 2;
        double centerImag = (minImag + maxImag) / 2;

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

        // Arrays for cache integration (no concurrent data structures needed)
        int[] iters = new int[width * height];
        boolean[] cacheHit = new boolean[width * height];

        // Reset stats for this render
        cache.resetStats();

        // Parallel phase: lookup cache, compute on miss
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
                        iter = FractalType.iterateJulia(cx, cy, juliaReal, juliaImag, maxIterations);
                    } else {
                        iter = type.iterate(cx, cy, maxIterations);
                    }
                    iters[idx] = iter;
                }

                rgb[idx] = (iters[idx] >= maxIterations) ? black : lut[iters[idx]].getRGB();
            }
        });

        // Sequential phase: batch-insert cache misses
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

        // Prune cached points far from the current viewport (2x margin)
        double marginR = viewReal;
        double marginI = viewImag;
        cache.pruneOutside(
            finalMinReal - marginR, finalMinReal + viewReal + marginR,
            finalMinImag - marginI, finalMinImag + viewImag + marginI
        );

        image.setRGB(0, 0, width, height, rgb, 0, width);
        return image;
    }
}

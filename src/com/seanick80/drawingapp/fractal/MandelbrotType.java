package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/** Mandelbrot set: z_{n+1} = z_n^2 + c, starting at z_0 = 0. */
public final class MandelbrotType implements FractalType {

    @Override public String name() { return "MANDELBROT"; }

    @Override
    public int iterate(double cx, double cy, int maxIter) {
        double zr = 0, zi = 0;
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return i;
            zi = 2 * zr * zi + cy;
            zr = zr2 - zi2 + cx;
        }
        return maxIter;
    }

    @Override
    public int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc) {
        BigDecimal zr = BigDecimal.ZERO, zi = BigDecimal.ZERO;
        BigDecimal four = new BigDecimal(4);
        BigDecimal two = new BigDecimal(2);
        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(four) > 0) return i;
            zi = two.multiply(zr, mc).multiply(zi, mc).add(cy, mc);
            zr = zr2.subtract(zi2, mc).add(cx, mc);
        }
        return maxIter;
    }

    @Override
    public boolean supportsPerturbation() { return true; }

    @Override public String toString() { return name(); }
}

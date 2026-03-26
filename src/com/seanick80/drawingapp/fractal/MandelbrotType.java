package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/** Mandelbrot set: z_{n+1} = z_n^2 + c, starting at z_0 = 0. */
public final class MandelbrotType implements FractalType {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final PerturbationStrategy PERTURBATION = new MandelbrotPerturbation();

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
        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(FOUR) > 0) return i;
            // Algebraic squaring trick: zr*zi = ((zr+zi)^2 - zr^2 - zi^2) / 2
            // Replaces one general multiply with one squaring (cheaper at high precision)
            BigDecimal sum = zr.add(zi, mc);
            BigDecimal sum2 = sum.multiply(sum, mc);
            zi = sum2.subtract(zr2, mc).subtract(zi2, mc).add(cy, mc);
            zr = zr2.subtract(zi2, mc).add(cx, mc);
        }
        return maxIter;
    }

    @Override
    public boolean supportsPerturbation() { return true; }

    @Override
    public PerturbationStrategy getPerturbationStrategy() {
        return PERTURBATION;
    }

    @Override public String toString() { return name(); }
}

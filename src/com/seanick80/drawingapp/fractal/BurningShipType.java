package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Burning Ship fractal: z_{n+1} = (|Re(z_n)| + i|Im(z_n)|)^2 + c, starting at z_0 = 0.
 * Differs from Mandelbrot by taking absolute values of real and imaginary parts before squaring.
 */
public final class BurningShipType implements FractalType {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override public String name() { return "BURNING_SHIP"; }

    @Override
    public int iterate(double cx, double cy, int maxIter) {
        double zr = 0, zi = 0;
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return i;
            double newZr = zr2 - zi2 + cx;
            zi = Math.abs(2 * zr * zi) + cy;
            zr = newZr;
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
            BigDecimal newZr = zr2.subtract(zi2, mc).add(cx, mc);
            zi = TWO.multiply(zr, mc).multiply(zi, mc).abs().add(cy, mc);
            zr = newZr;
        }
        return maxIter;
    }

    @Override public String toString() { return name(); }
}

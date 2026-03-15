package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

public enum FractalType {
    MANDELBROT {
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
    },
    JULIA {
        @Override
        public int iterate(double cx, double cy, int maxIter) {
            // Default Julia constant; overridden via FractalRenderer
            return iterateJulia(cx, cy, -0.7, 0.27015, maxIter);
        }

        @Override
        public int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc) {
            // Default Julia constant; overridden via FractalRenderer
            return iterateJuliaBig(cx, cy,
                new BigDecimal("-0.7"), new BigDecimal("0.27015"), maxIter, mc);
        }
    };

    public abstract int iterate(double cx, double cy, int maxIter);

    public abstract int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc);

    public static int iterateJulia(double zr, double zi, double cr, double ci, int maxIter) {
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return i;
            double newZr = zr2 - zi2 + cr;
            zi = 2 * zr * zi + ci;
            zr = newZr;
        }
        return maxIter;
    }

    public static int iterateJuliaBig(BigDecimal zr, BigDecimal zi,
            BigDecimal cr, BigDecimal ci, int maxIter, MathContext mc) {
        BigDecimal four = new BigDecimal(4);
        BigDecimal two = new BigDecimal(2);
        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(four) > 0) return i;
            BigDecimal newZr = zr2.subtract(zi2, mc).add(cr, mc);
            zi = two.multiply(zr, mc).multiply(zi, mc).add(ci, mc);
            zr = newZr;
        }
        return maxIter;
    }
}

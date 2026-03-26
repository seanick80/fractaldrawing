package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Magnet Type I fractal: z_{n+1} = ((z_n^2 + c - 1) / (2*z_n + c - 2))^2
 * Starting at z_0 = 0. Has both escape (|z| > bailout) and convergence (|z - 1| < epsilon)
 * termination conditions. The fixed point at z=1 creates the distinctive "magnetic" pattern.
 */
public final class MagnetTypeIType implements FractalType {

    private static final double BAILOUT = 100.0;
    private static final double CONVERGENCE_EPSILON = 1e-6;
    private static final BigDecimal BD_BAILOUT = BigDecimal.valueOf(BAILOUT);
    private static final BigDecimal BD_EPSILON = new BigDecimal("0.000001");
    private static final BigDecimal BD_TWO = BigDecimal.valueOf(2);
    private static final BigDecimal BD_DENOM_MIN = new BigDecimal("1E-30");

    @Override public String name() { return "MAGNET_I"; }

    @Override
    public int iterate(double cx, double cy, int maxIter) {
        double zr = 0, zi = 0;
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > BAILOUT) return i;

            // Check convergence to fixed point z = 1
            double dr = zr - 1, di = zi;
            if (dr * dr + di * di < CONVERGENCE_EPSILON) return maxIter;

            // Numerator: z^2 + c - 1
            double nr = zr2 - zi2 + cx - 1;
            double ni = 2 * zr * zi + cy;

            // Denominator: 2z + c - 2
            double dr2 = 2 * zr + cx - 2;
            double di2 = 2 * zi + cy;

            // Complex division: n / d
            double denom = dr2 * dr2 + di2 * di2;
            if (denom < 1e-30) return maxIter; // avoid division by zero

            double qr = (nr * dr2 + ni * di2) / denom;
            double qi = (ni * dr2 - nr * di2) / denom;

            // Square the quotient: (n/d)^2
            zr = qr * qr - qi * qi;
            zi = 2 * qr * qi;
        }
        return maxIter;
    }

    @Override
    public int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc) {
        BigDecimal zr = BigDecimal.ZERO, zi = BigDecimal.ZERO;

        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(BD_BAILOUT) > 0) return i;

            // Convergence check: |z - 1|^2 < epsilon
            BigDecimal dr = zr.subtract(BigDecimal.ONE, mc);
            if (dr.multiply(dr, mc).add(zi2, mc).compareTo(BD_EPSILON) < 0) return maxIter;

            // Numerator: z^2 + c - 1, using squaring trick for 2*zr*zi
            BigDecimal nr = zr2.subtract(zi2, mc).add(cx, mc).subtract(BigDecimal.ONE, mc);
            BigDecimal zSum = zr.add(zi, mc);
            BigDecimal zSum2 = zSum.multiply(zSum, mc);
            BigDecimal ni = zSum2.subtract(zr2, mc).subtract(zi2, mc).add(cy, mc);

            // Denominator: 2z + c - 2
            BigDecimal ddr = BD_TWO.multiply(zr, mc).add(cx, mc).subtract(BD_TWO, mc);
            BigDecimal ddi = BD_TWO.multiply(zi, mc).add(cy, mc);

            // |d|^2
            BigDecimal denomSq = ddr.multiply(ddr, mc).add(ddi.multiply(ddi, mc), mc);
            if (denomSq.compareTo(BD_DENOM_MIN) < 0) return maxIter;

            // Complex division n/d
            BigDecimal qr = nr.multiply(ddr, mc).add(ni.multiply(ddi, mc), mc)
                              .divide(denomSq, mc);
            BigDecimal qi = ni.multiply(ddr, mc).subtract(nr.multiply(ddi, mc), mc)
                              .divide(denomSq, mc);

            // Square: (n/d)^2, using squaring trick for 2*qr*qi
            BigDecimal qr2 = qr.multiply(qr, mc);
            BigDecimal qi2 = qi.multiply(qi, mc);
            BigDecimal qSum = qr.add(qi, mc);
            BigDecimal qSum2 = qSum.multiply(qSum, mc);
            zr = qr2.subtract(qi2, mc);
            zi = qSum2.subtract(qr2, mc).subtract(qi2, mc);
        }
        return maxIter;
    }

    @Override public String toString() { return name(); }
}

package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Perturbation theory for Mandelbrot set: z_{n+1} = z_n^2 + c.
 * Reference orbit starts at z_0 = 0 with c = center pixel.
 * Perturbation: δz_0 = 0, δz_{n+1} = 2·Z_n·δz_n + δz_n² + δc
 */
public final class MandelbrotPerturbation implements PerturbationStrategy {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override
    public int computeReferenceOrbit(BigDecimal cr, BigDecimal ci,
                                      int maxIter, MathContext mc,
                                      double[] outZr, double[] outZi) {
        BigDecimal zr = BigDecimal.ZERO, zi = BigDecimal.ZERO;
        int escapeIter = maxIter;
        double dCr = cr.doubleValue(), dCi = ci.doubleValue();
        for (int i = 0; i < maxIter; i++) {
            outZr[i] = zr.doubleValue();
            outZi[i] = zi.doubleValue();
            if (escapeIter < maxIter) {
                double dzr = outZr[i], dzi = outZi[i];
                double dzr2 = dzr * dzr, dzi2 = dzi * dzi;
                double newDzi = 2 * dzr * dzi + dCi;
                outZr[i + 1] = dzr2 - dzi2 + dCr;
                outZi[i + 1] = newDzi;
                continue;
            }
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(FOUR) > 0) {
                escapeIter = i;
                double dzr = zr.doubleValue(), dzi = zi.doubleValue();
                double dzr2 = dzr * dzr, dzi2 = dzi * dzi;
                double newDzi = 2 * dzr * dzi + dCi;
                outZr[i + 1] = dzr2 - dzi2 + dCr;
                outZi[i + 1] = newDzi;
                continue;
            }
            // Algebraic squaring trick: 2*zr*zi = (zr+zi)^2 - zr^2 - zi^2
            BigDecimal sum = zr.add(zi, mc);
            BigDecimal sum2 = sum.multiply(sum, mc);
            BigDecimal newZi = sum2.subtract(zr2, mc).subtract(zi2, mc).add(ci, mc);
            zr = zr2.subtract(zi2, mc).add(cr, mc);
            zi = newZi;
        }
        if (escapeIter == maxIter) {
            outZr[maxIter] = zr.doubleValue();
            outZi[maxIter] = zi.doubleValue();
        }
        return escapeIter;
    }

    @Override
    public int perturbIterate(double[] refZr, double[] refZi,
                               double dcr, double dci,
                               int refEscapeIter, int maxIter) {
        double dzr = 0, dzi = 0;
        int limit = Math.min(maxIter, refEscapeIter);

        for (int i = 0; i < limit; i++) {
            double Zr = refZr[i], Zi = refZi[i];
            double newDzr = 2 * (Zr * dzr - Zi * dzi) + dzr * dzr - dzi * dzi + dcr;
            double newDzi = 2 * (Zr * dzi + Zi * dzr) + 2 * dzr * dzi + dci;
            dzr = newDzr;
            dzi = newDzi;

            double Zr1 = refZr[i + 1], Zi1 = refZi[i + 1];
            double totalR = Zr1 + dzr;
            double totalI = Zi1 + dzi;
            if (totalR * totalR + totalI * totalI > 4.0) return i + 1;

            if (Double.isNaN(dzr) || Double.isInfinite(dzr)) return GLITCH_DETECTED;
        }

        if (refEscapeIter < maxIter) return GLITCH_DETECTED;
        return maxIter;
    }
}

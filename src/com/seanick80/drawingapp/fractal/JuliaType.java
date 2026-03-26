package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/** Julia set: z_{n+1} = z_n^2 + c, where c is a fixed constant and z_0 = pixel coordinate. */
public final class JuliaType implements FractalType {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final double cr, ci;
    private final BigDecimal crBig, ciBig;
    private final PerturbationStrategy perturbation;

    public JuliaType(double cr, double ci) {
        this.cr = cr;
        this.ci = ci;
        this.crBig = new BigDecimal(Double.toString(cr));
        this.ciBig = new BigDecimal(Double.toString(ci));
        this.perturbation = new JuliaPerturbation(crBig, ciBig);
    }

    public JuliaType(BigDecimal cr, BigDecimal ci) {
        this.cr = cr.doubleValue();
        this.ci = ci.doubleValue();
        this.crBig = cr;
        this.ciBig = ci;
        this.perturbation = new JuliaPerturbation(cr, ci);
    }

    public double getCr() { return cr; }
    public double getCi() { return ci; }
    public BigDecimal getCrBig() { return crBig; }
    public BigDecimal getCiBig() { return ciBig; }

    @Override public String name() { return "JULIA"; }

    @Override
    public int iterate(double zr, double zi, int maxIter) {
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return i;
            double newZr = zr2 - zi2 + cr;
            zi = 2 * zr * zi + ci;
            zr = newZr;
        }
        return maxIter;
    }

    @Override
    public int iterateBig(BigDecimal zr, BigDecimal zi, int maxIter, MathContext mc) {
        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(FOUR) > 0) return i;
            BigDecimal newZr = zr2.subtract(zi2, mc).add(crBig, mc);
            // Algebraic squaring trick: 2*zr*zi = (zr+zi)^2 - zr^2 - zi^2
            BigDecimal sum = zr.add(zi, mc);
            BigDecimal sum2 = sum.multiply(sum, mc);
            zi = sum2.subtract(zr2, mc).subtract(zi2, mc).add(ciBig, mc);
            zr = newZr;
        }
        return maxIter;
    }

    @Override
    public boolean supportsPerturbation() { return true; }

    @Override
    public PerturbationStrategy getPerturbationStrategy() {
        return perturbation;
    }

    @Override public String toString() { return name(); }
}

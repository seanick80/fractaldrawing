package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/** Julia set: z_{n+1} = z_n^2 + c, where c is a fixed constant and z_0 = pixel coordinate. */
public final class JuliaType implements FractalType {

    private final double cr, ci;
    private final BigDecimal crBig, ciBig;

    public JuliaType(double cr, double ci) {
        this.cr = cr;
        this.ci = ci;
        this.crBig = new BigDecimal(Double.toString(cr));
        this.ciBig = new BigDecimal(Double.toString(ci));
    }

    public JuliaType(BigDecimal cr, BigDecimal ci) {
        this.cr = cr.doubleValue();
        this.ci = ci.doubleValue();
        this.crBig = cr;
        this.ciBig = ci;
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
        BigDecimal four = new BigDecimal(4);
        BigDecimal two = new BigDecimal(2);
        for (int i = 0; i < maxIter; i++) {
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            if (zr2.add(zi2, mc).compareTo(four) > 0) return i;
            BigDecimal newZr = zr2.subtract(zi2, mc).add(crBig, mc);
            zi = two.multiply(zr, mc).multiply(zi, mc).add(ciBig, mc);
            zr = newZr;
        }
        return maxIter;
    }

    @Override
    public boolean supportsPerturbation() { return true; }

    @Override
    public PerturbationStrategy getPerturbationStrategy() {
        return new JuliaPerturbation(crBig, ciBig);
    }

    @Override public String toString() { return name(); }
}

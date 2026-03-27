package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Interface for fractal iteration types. Each implementation defines
 * how to compute escape-time iteration in both double and BigDecimal precision.
 */
public interface FractalType {

    /** Singleton Mandelbrot instance. */
    FractalType MANDELBROT = new MandelbrotType();

    /** Default Julia instance with c = -0.7 + 0.27015i. */
    FractalType JULIA = new JuliaType(-0.7, 0.27015);

    /** Display name (also used for serialization). */
    String name();

    /** Double-precision escape-time iteration. */
    int iterate(double cx, double cy, int maxIter);

    /** Arbitrary-precision escape-time iteration. */
    int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc);

    /** Whether this type supports perturbation theory acceleration. */
    default boolean supportsPerturbation() { return false; }

    /** Returns the perturbation strategy, or null if not supported. */
    default PerturbationStrategy getPerturbationStrategy() { return null; }

    /** Default viewport center (real axis). Override for off-center fractals. */
    default BigDecimal defaultCenterReal() { return BigDecimal.ZERO; }

    /** Default viewport center (imaginary axis). */
    default BigDecimal defaultCenterImag() { return BigDecimal.ZERO; }

    /** Default max iterations for the overview zoom level. */
    default int defaultMaxIterations() { return 256; }

    /** Look up a type by name. Returns null if not found. */
    static FractalType valueOf(String name) {
        return FractalTypeRegistry.getDefault().getByName(name);
    }
}

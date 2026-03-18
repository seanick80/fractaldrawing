package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Strategy for perturbation theory acceleration of fractal rendering.
 * Each fractal type that supports perturbation provides its own strategy
 * with type-specific reference orbit computation and perturbation formula.
 */
public interface PerturbationStrategy {

    /**
     * Compute a reference orbit at the given center point using BigDecimal.
     * Stores the orbit's real/imaginary parts in outZr/outZi arrays.
     * Returns the iteration at which the reference orbit escapes (or maxIter if it doesn't).
     */
    int computeReferenceOrbit(BigDecimal centerR, BigDecimal centerI,
                               int maxIter, MathContext mc,
                               double[] outZr, double[] outZi);

    /**
     * Compute escape time for a pixel at offset (dcr, dci) from the reference orbit.
     * Returns iteration count, or GLITCH_DETECTED (-2) if perturbation is unreliable.
     */
    int perturbIterate(double[] refZr, double[] refZi,
                        double dcr, double dci,
                        int refEscapeIter, int maxIter);

    int GLITCH_DETECTED = -2;
}

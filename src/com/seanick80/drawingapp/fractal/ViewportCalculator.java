package com.seanick80.drawingapp.fractal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Computes aspect-ratio-corrected viewport parameters from bounds and image dimensions.
 * Shared by all render paths in FractalRenderer.
 */
public final class ViewportCalculator {

    /** Double-precision viewport result. */
    public static final class DoubleViewport {
        public final double minReal, minImag;
        public final double scaleX, scaleY;
        public final double viewReal, viewImag;
        public final double tolerance;

        DoubleViewport(double minReal, double minImag,
                       double scaleX, double scaleY,
                       double viewReal, double viewImag,
                       double tolerance) {
            this.minReal = minReal;
            this.minImag = minImag;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.viewReal = viewReal;
            this.viewImag = viewImag;
            this.tolerance = tolerance;
        }
    }

    /** BigDecimal-precision viewport result. */
    public static final class BigViewport {
        public final BigDecimal minReal, minImag;
        public final BigDecimal scaleX, scaleY;
        public final BigDecimal viewReal, viewImag;
        public final BigDecimal centerReal, centerImag;

        BigViewport(BigDecimal minReal, BigDecimal minImag,
                    BigDecimal scaleX, BigDecimal scaleY,
                    BigDecimal viewReal, BigDecimal viewImag,
                    BigDecimal centerReal, BigDecimal centerImag) {
            this.minReal = minReal;
            this.minImag = minImag;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.viewReal = viewReal;
            this.viewImag = viewImag;
            this.centerReal = centerReal;
            this.centerImag = centerImag;
        }
    }

    private static final double TOLERANCE_FRACTION = 0.1;

    /**
     * Compute a double-precision viewport with aspect-ratio correction.
     */
    public static DoubleViewport computeDouble(
            BigDecimal boundsMinReal, BigDecimal boundsMaxReal,
            BigDecimal boundsMinImag, BigDecimal boundsMaxImag,
            int width, int height) {

        double dMinReal = boundsMinReal.doubleValue();
        double dMaxReal = boundsMaxReal.doubleValue();
        double dMinImag = boundsMinImag.doubleValue();
        double dMaxImag = boundsMaxImag.doubleValue();
        double rangeReal = dMaxReal - dMinReal;
        double rangeImag = dMaxImag - dMinImag;
        double aspect = (double) width / height;
        double centerReal = (dMinReal + dMaxReal) / 2;
        double centerImag = (dMinImag + dMaxImag) / 2;

        double viewReal, viewImag;
        if (rangeReal / rangeImag > aspect) {
            viewReal = rangeReal;
            viewImag = rangeReal / aspect;
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag * aspect;
        }

        double finalMinReal = centerReal - viewReal / 2;
        double finalMinImag = centerImag - viewImag / 2;
        double scaleX = viewReal / (width - 1);
        double scaleY = viewImag / (height - 1);
        double tolerance = Math.min(scaleX, scaleY) * TOLERANCE_FRACTION;

        return new DoubleViewport(finalMinReal, finalMinImag,
                                  scaleX, scaleY, viewReal, viewImag, tolerance);
    }

    /**
     * Compute a BigDecimal-precision viewport with aspect-ratio correction.
     */
    public static BigViewport computeBig(
            BigDecimal boundsMinReal, BigDecimal boundsMaxReal,
            BigDecimal boundsMinImag, BigDecimal boundsMaxImag,
            BigDecimal rangeReal, BigDecimal rangeImag,
            int width, int height, MathContext mc) {

        BigDecimal bdAspect = new BigDecimal(width).divide(new BigDecimal(height), mc);
        BigDecimal centerReal = boundsMinReal.add(boundsMaxReal, mc)
                                             .divide(BigDecimal.valueOf(2), mc);
        BigDecimal centerImag = boundsMinImag.add(boundsMaxImag, mc)
                                             .divide(BigDecimal.valueOf(2), mc);

        BigDecimal ratioRI = rangeReal.divide(rangeImag, mc);
        BigDecimal viewReal, viewImag;
        if (ratioRI.compareTo(bdAspect) > 0) {
            viewReal = rangeReal;
            viewImag = rangeReal.divide(bdAspect, mc);
        } else {
            viewImag = rangeImag;
            viewReal = rangeImag.multiply(bdAspect, mc);
        }

        BigDecimal scaleX = viewReal.divide(new BigDecimal(width - 1), mc);
        BigDecimal scaleY = viewImag.divide(new BigDecimal(height - 1), mc);
        BigDecimal finalMinReal = centerReal.subtract(
                viewReal.divide(BigDecimal.valueOf(2), mc), mc);
        BigDecimal finalMinImag = centerImag.subtract(
                viewImag.divide(BigDecimal.valueOf(2), mc), mc);

        return new BigViewport(finalMinReal, finalMinImag,
                               scaleX, scaleY, viewReal, viewImag,
                               centerReal, centerImag);
    }
}

package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.math.MathContext;

class ViewportCalculatorTest {

    private static final MathContext mc20 = new MathContext(20);

    private ViewportCalculator.DoubleViewport squareViewport() {
        return ViewportCalculator.computeDouble(
            new BigDecimal("-2"), new BigDecimal("1"),
            new BigDecimal("-1.5"), new BigDecimal("1.5"),
            100, 100);
    }

    @Test @SmallTest
    void squareImage_equalViewDimensions() {
        ViewportCalculator.DoubleViewport vp = squareViewport();
        assertEquals(vp.viewReal, vp.viewImag, 1e-10,
            "Square image should have equal viewReal and viewImag");
    }

    @Test @SmallTest
    void scalesArePositive() {
        ViewportCalculator.DoubleViewport vp = squareViewport();
        assertTrue(vp.scaleX > 0, "scaleX should be positive");
        assertTrue(vp.scaleY > 0, "scaleY should be positive");
        assertTrue(vp.tolerance > 0, "tolerance should be positive");
    }

    @Test @SmallTest
    void wideImage_expandsRealAxis() {
        ViewportCalculator.DoubleViewport vp = ViewportCalculator.computeDouble(
            new BigDecimal("-2"), new BigDecimal("1"),
            new BigDecimal("-1.5"), new BigDecimal("1.5"),
            200, 100);
        assertTrue(vp.viewReal > vp.viewImag,
            "Wide image should expand real axis: viewReal=" + vp.viewReal + " viewImag=" + vp.viewImag);
    }

    @Test @SmallTest
    void bigViewport_correctCenter() {
        BigDecimal minReal = new BigDecimal("-2");
        BigDecimal maxReal = new BigDecimal("1");
        BigDecimal minImag = new BigDecimal("-1.5");
        BigDecimal maxImag = new BigDecimal("1.5");
        BigDecimal rangeReal = maxReal.subtract(minReal, mc20);
        BigDecimal rangeImag = maxImag.subtract(minImag, mc20);

        ViewportCalculator.BigViewport vp = ViewportCalculator.computeBig(
            minReal, maxReal, minImag, maxImag,
            rangeReal, rangeImag,
            100, 100, mc20);

        assertEquals(-0.5, vp.centerReal.doubleValue(), 1e-10,
            "Center real should be -0.5");
        assertEquals(0.0, vp.centerImag.doubleValue(), 1e-10,
            "Center imag should be 0.0");
    }

    @Test @SmallTest
    void doubleAndBig_scaleXAgree() {
        BigDecimal minReal = new BigDecimal("-2");
        BigDecimal maxReal = new BigDecimal("1");
        BigDecimal minImag = new BigDecimal("-1.5");
        BigDecimal maxImag = new BigDecimal("1.5");
        BigDecimal rangeReal = maxReal.subtract(minReal, mc20);
        BigDecimal rangeImag = maxImag.subtract(minImag, mc20);

        ViewportCalculator.DoubleViewport dvp = ViewportCalculator.computeDouble(
            minReal, maxReal, minImag, maxImag, 100, 100);
        ViewportCalculator.BigViewport bvp = ViewportCalculator.computeBig(
            minReal, maxReal, minImag, maxImag,
            rangeReal, rangeImag,
            100, 100, mc20);

        double dScale = dvp.scaleX;
        double bScale = bvp.scaleX.doubleValue();
        double relDiff = Math.abs(dScale - bScale) / Math.max(Math.abs(dScale), 1e-300);
        assertTrue(relDiff < 1e-10,
            "double and BigDecimal scaleX should agree within 1e-10, got relDiff=" + relDiff);
    }
}

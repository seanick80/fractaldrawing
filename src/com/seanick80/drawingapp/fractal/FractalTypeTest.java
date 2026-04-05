package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.math.MathContext;

class FractalTypeTest {

    private static final MathContext mc20 = new MathContext(20);

    @Test @SmallTest
    void mandelbrot_originIsInterior() {
        assertEquals(256, FractalType.MANDELBROT.iterate(0, 0, 256));
    }

    @Test @SmallTest
    void mandelbrot_farPointEscapes() {
        assertTrue(FractalType.MANDELBROT.iterate(2, 2, 256) < 5);
    }

    @Test @SmallTest
    void julia_originEscapesSlowly() {
        int iter = new JuliaType(-0.7, 0.27015).iterate(0, 0, 256);
        assertTrue(iter > 5 && iter < 256,
            "Expected iter in (5, 256) but got " + iter);
    }

    @Test @SmallTest
    void julia_farPointEscapesImmediately() {
        int iter = new JuliaType(-0.7, 0.27015).iterate(10, 10, 256);
        assertTrue(iter < 3, "Expected iter < 3 but got " + iter);
    }

    @Test @SmallTest
    void mandelbrot_bigDecimalMatchesDouble() {
        int doublePrecision = FractalType.MANDELBROT.iterate(0, 0, 256);
        int bigPrecision = FractalType.MANDELBROT.iterateBig(
            BigDecimal.ZERO, BigDecimal.ZERO, 256, mc20);
        assertEquals(doublePrecision, bigPrecision);
    }

    @Test @SmallTest
    void burningShip_knownPoints() {
        FractalType bs = new BurningShipType();
        int escaping = bs.iterate(0.5, 0.5, 256);
        assertTrue(escaping < 256 && escaping > 0,
            "Expected (0.5,0.5) to escape but got " + escaping);
        assertEquals(256, bs.iterate(0, 0, 256));
        assertTrue(bs.iterate(10, 10, 256) < 3);
    }

    @Test @SmallTest
    void burningShip_bigDecimalMatchesDouble() {
        FractalType bs = new BurningShipType();
        int expected = bs.iterate(0.5, 0.5, 256);
        int actual = bs.iterateBig(new BigDecimal("0.5"), new BigDecimal("0.5"), 256, mc20);
        assertEquals(expected, actual);
    }

    @Test @SmallTest
    void burningShip_isAsymmetric() {
        FractalType bs = new BurningShipType();
        assertNotEquals(bs.iterate(-1.5, 0.1, 256), bs.iterate(-1.5, -0.1, 256));
    }

    @Test @SmallTest
    void tricorn_originIsInterior() {
        assertEquals(256, new TricornType().iterate(0, 0, 256));
    }

    @Test @SmallTest
    void tricorn_conjugationSymmetry() {
        TricornType t = new TricornType();
        assertEquals(t.iterate(-0.5, 0.5, 256), t.iterate(-0.5, -0.5, 256));
    }

    @Test @SmallTest
    void tricorn_bigDecimalMatchesDouble() {
        TricornType t = new TricornType();
        int expected = t.iterate(10, 10, 256);
        int actual = t.iterateBig(new BigDecimal("10"), new BigDecimal("10"), 256, mc20);
        assertEquals(expected, actual);
    }

    @Test @SmallTest
    void magnet_farPointConverges() {
        assertEquals(256, new MagnetTypeIType().iterate(10, 10, 256));
    }

    @Test @SmallTest
    void magnet_bigDecimalMatchesDouble() {
        MagnetTypeIType m = new MagnetTypeIType();
        int expected = m.iterate(10, 10, 256);
        int actual = m.iterateBig(new BigDecimal("10"), new BigDecimal("10"), 256, mc20);
        assertEquals(expected, actual);
    }

    @Test @SmallTest
    void typeRegistry_roundTrip() {
        for (FractalType type : FractalTypeRegistry.getDefault().getAll()) {
            String name = type.name();
            FractalType looked = FractalType.valueOf(name);
            assertNotNull(looked, "Expected to find type with name: " + name);
            assertEquals(name, looked.name());
        }
    }
}

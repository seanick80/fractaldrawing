package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;

class PixelGuessingTest {

    private static final int SIZE = TestHelpers.DEFAULT_SIZE;

    @Test @LargeTest @Timeout(10)
    void nearIdentical() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(200, 200, TestHelpers.gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(200, 200, TestHelpers.gradient());

        int[] pe = TestHelpers.getPixels(exact);
        int[] pg = TestHelpers.getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        assertTrue(diffPct < 5.0, "Pixel guessing should be <5% diff (got " + String.format("%.1f%%", diffPct) + ")");
        assertTrue(TestHelpers.countUniqueColors(guessed) >= TestHelpers.countUniqueColors(exact) - 5);
    }

    @Test @LargeTest @Timeout(10)
    void onOffToggle() {
        FractalRenderer r = TestHelpers.newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(SIZE, SIZE, TestHelpers.gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(SIZE, SIZE, TestHelpers.gradient());

        int[] pe = TestHelpers.getPixels(exact);
        int[] pg = TestHelpers.getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        assertTrue(diffPct < 5.0, "Perturbation guessing should be <5% diff (got " + String.format("%.1f%%", diffPct) + ")");
    }

    @Test @LargeTest @Timeout(10)
    void filamentRegion() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(
            new BigDecimal("-1.158514308230042888196567"),
            new BigDecimal("-1.158479463315670184097907"),
            new BigDecimal("-0.2686450286351049685909021"),
            new BigDecimal("-0.2686101837207322644922435")
        );
        r.setMaxIterations(256);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(200, 150, TestHelpers.gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(200, 150, TestHelpers.gradient());

        int[] pe = TestHelpers.getPixels(exact);
        int[] pg = TestHelpers.getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        assertTrue(diffPct < 1.0, "Filament region guessing should be <1% diff (got " + String.format("%.1f%%", diffPct) + ")");
    }
}

package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;

class FractalTypeRenderTest {

    private static final int SIZE = TestHelpers.DEFAULT_SIZE;

    @Test @LargeTest @Timeout(10)
    void burningShipRendersValidImage() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setType(new BurningShipType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertTrue(TestHelpers.countUniqueColors(img) > 15);
        int black = TestHelpers.countColor(img, Color.BLACK.getRGB());
        assertTrue(black > 0 && black < SIZE * SIZE);
    }

    @Test @LargeTest @Timeout(10)
    void tricornRendersValidImage() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setType(new TricornType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertTrue(TestHelpers.countUniqueColors(img) > 15);
        int black = TestHelpers.countColor(img, Color.BLACK.getRGB());
        assertTrue(black > 0 && black < SIZE * SIZE);
    }

    @Test @LargeTest @Timeout(10)
    void magnetRendersValidImage() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setType(new MagnetTypeIType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertTrue(TestHelpers.countUniqueColors(img) > 10);
    }

    @Test @LargeTest @Timeout(30)
    void newTypesInAllRenderModes() {
        FractalType[] newTypes = { new BurningShipType(), new TricornType(), new MagnetTypeIType() };
        FractalRenderer.RenderMode[] modes = {
            FractalRenderer.RenderMode.DOUBLE,
            FractalRenderer.RenderMode.BIGDECIMAL
        };
        for (FractalType ft : newTypes) {
            for (FractalRenderer.RenderMode mode : modes) {
                FractalRenderer r = TestHelpers.newRenderer();
                r.setType(ft);
                r.setBounds(-2.0, 2.0, -2.0, 2.0);
                r.setMaxIterations(64);
                r.setRenderMode(mode);
                r.getCache().clear();
                BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
                assertNotNull(img, ft.name() + " " + mode + " should not return null");
                assertTrue(TestHelpers.countUniqueColors(img) > 1, ft.name() + " " + mode + " should have >1 color");
            }
        }
    }
}

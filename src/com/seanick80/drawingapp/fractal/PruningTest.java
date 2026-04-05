package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

class PruningTest {

    @Test @LargeTest @Timeout(10)
    void identicalOutput() {
        FractalRenderer rOn = TestHelpers.newRenderer();
        FractalRenderer rOff = TestHelpers.newRenderer();
        rOn.setBounds(-0.75, -0.74, 0.10, 0.11);
        rOff.setBounds(-0.75, -0.74, 0.10, 0.11);
        rOn.setMaxIterations(256);
        rOff.setMaxIterations(256);
        rOn.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOff.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, TestHelpers.gradient());
        BufferedImage imgOff = rOff.render(200, 150, TestHelpers.gradient());
        assertTrue(TestHelpers.imagesEqual(imgOn, imgOff), "Pruning on/off should be identical at cardioid edge");
    }

    @Test @LargeTest @Timeout(10)
    void identicalAtSpikyEdges() {
        FractalRenderer rOn = TestHelpers.newRenderer();
        FractalRenderer rOff = TestHelpers.newRenderer();

        BigDecimal minR = new BigDecimal("-1.7690645");
        BigDecimal maxR = new BigDecimal("-1.7690625");
        BigDecimal minI = new BigDecimal("-0.0000010");
        BigDecimal maxI = new BigDecimal("0.0000010");

        rOn.setBounds(minR, maxR, minI, maxI);
        rOff.setBounds(minR, maxR, minI, maxI);
        rOn.setMaxIterations(512);
        rOff.setMaxIterations(512);
        rOn.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOff.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, TestHelpers.gradient());
        BufferedImage imgOff = rOff.render(200, 150, TestHelpers.gradient());

        int[] pxOn = TestHelpers.getPixels(imgOn);
        int[] pxOff = TestHelpers.getPixels(imgOff);
        int diff = 0;
        for (int i = 0; i < pxOn.length; i++) {
            if (pxOn[i] != pxOff[i]) diff++;
        }
        assertEquals(0, diff, "Pruning on/off should be identical at spiky antenna");
    }

    @Test @LargeTest @Timeout(10)
    void mandelbrotInterior() {
        FractalRenderer rOn = TestHelpers.newRenderer();
        FractalRenderer rOff = TestHelpers.newRenderer();
        rOn.setBounds(-1.26, -1.24, -0.01, 0.01);
        rOff.setBounds(-1.26, -1.24, -0.01, 0.01);
        rOn.setMaxIterations(512);
        rOff.setMaxIterations(512);
        rOn.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOff.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, TestHelpers.gradient());
        BufferedImage imgOff = rOff.render(200, 150, TestHelpers.gradient());

        assertTrue(TestHelpers.imagesEqual(imgOn, imgOff), "Pruning on/off should be identical at bulb edge");

        int blackOn = TestHelpers.countColor(imgOn, Color.BLACK.getRGB());
        assertTrue(blackOn > 200 * 150 * 0.1, "Should have significant interior");
    }

    @Test @LargeTest @Timeout(10)
    void speedupOnInterior() {
        BigDecimal minR = new BigDecimal("-0.7500100000000000");
        BigDecimal maxR = new BigDecimal("-0.7499900000000000");
        BigDecimal minI = new BigDecimal("-0.0000100000000000");
        BigDecimal maxI = new BigDecimal("0.0000100000000000");

        FractalRenderer rOn = TestHelpers.newRenderer();
        rOn.setBounds(minR, maxR, minI, maxI);
        rOn.setMaxIterations(256);
        rOn.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOn.setInteriorPruning(true);
        BufferedImage imgOn = rOn.render(200, 150, TestHelpers.gradient());

        FractalRenderer rOff = TestHelpers.newRenderer();
        rOff.setBounds(minR, maxR, minI, maxI);
        rOff.setMaxIterations(256);
        rOff.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOff.setInteriorPruning(false);
        BufferedImage imgOff = rOff.render(200, 150, TestHelpers.gradient());

        assertTrue(TestHelpers.imagesEqual(imgOn, imgOff), "Pruning on/off should be identical at deep cardioid");

        int blackCount = TestHelpers.countColor(imgOn, Color.BLACK.getRGB());
        assertTrue(blackCount > 200 * 150 * 0.5, "Should have >50% interior");
    }
}

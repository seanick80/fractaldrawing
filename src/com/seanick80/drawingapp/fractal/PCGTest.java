package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.gradient.ColorGradient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;

class PCGTest {

    private static final int SIZE = TestHelpers.DEFAULT_SIZE;

    // -----------------------------------------------------------------------
    // Heightmap conversion
    // -----------------------------------------------------------------------

    @Test @MediumTest
    void resampleToHeightmap() {
        // Create a simple iteration array: gradient from 0 to maxIter
        int w = 100, h = 100, maxIter = 256;
        int[] iters = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                iters[y * w + x] = x * maxIter / w;
            }
        }

        int mapSize = 65; // 2^6 + 1
        float[] heightmap = PCGController.resampleToHeightmap(iters, w, h, mapSize, maxIter);
        assertEquals(mapSize * mapSize, heightmap.length);

        // Left edge should be near 0, right edge near 1
        assertTrue(heightmap[0] < 0.05f, "Left edge should be low");
        assertTrue(heightmap[mapSize - 1] > 0.9f, "Right edge should be high");

        // All values should be in [0, 1]
        for (float v : heightmap) {
            assertTrue(v >= 0f && v <= 1.01f, "Heightmap values should be normalized");
        }
    }

    @Test @MediumTest
    void buildIterationColormap() {
        int w = 50, h = 50, maxIter = 256;
        int[] iters = new int[w * h];
        for (int i = 0; i < iters.length; i++) iters[i] = i % maxIter;

        int mapSize = 33;
        ColorGradient grad = ColorGradient.fractalDefault();
        int[] colormap = PCGController.buildIterationColormap(iters, w, h, mapSize, maxIter, grad);
        assertEquals(mapSize * mapSize, colormap.length);

        // All entries should be valid RGB (non-zero for a colorful gradient)
        boolean hasColor = false;
        for (int c : colormap) {
            if (c != 0) hasColor = true;
        }
        assertTrue(hasColor, "Colormap should have non-black entries");
    }

    // -----------------------------------------------------------------------
    // L-System parameter derivation
    // -----------------------------------------------------------------------

    @Test @MediumTest
    void deriveTreeParams() {
        int w = 100, h = 100, maxIter = 256;
        int[] iters = new int[w * h];
        // Create a pattern with boundary-like features
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dist = (int) Math.sqrt((x - 50) * (x - 50) + (y - 50) * (y - 50));
                iters[y * w + x] = Math.min(maxIter, dist * maxIter / 50);
            }
        }

        LSystemParams params = PCGController.deriveTreeParams(iters, w, h, maxIter);
        assertNotNull(params);
        assertTrue(params.getAngle() >= 15 && params.getAngle() <= 45,
                "Angle should be 15-45: " + params.getAngle());
        assertTrue(params.getDepth() >= 4 && params.getDepth() <= 8,
                "Depth should be 4-8: " + params.getDepth());
        assertTrue(params.getLengthDecay() >= 0.5f && params.getLengthDecay() <= 1.0f,
                "Decay should be 0.5-1.0: " + params.getLengthDecay());
        assertTrue(params.getBranchProb() >= 0.5f && params.getBranchProb() <= 1.0f,
                "BranchProb should be 0.5-1.0: " + params.getBranchProb());
    }

    // -----------------------------------------------------------------------
    // L-System generation and rendering
    // -----------------------------------------------------------------------

    @Test @MediumTest
    void lsystemGeneration() {
        LSystemParams params = new LSystemParams(25f, 0.7f, 4, 1.0f, 0);
        LSystemRenderer renderer = new LSystemRenderer(params, ColorGradient.fractalDefault());
        String result = renderer.generate();
        assertNotNull(result);
        assertTrue(result.length() > 10, "Generated string should be substantial");
        assertTrue(result.contains("["), "Should contain branch pushes");
        assertTrue(result.contains("]"), "Should contain branch pops");
    }

    @Test @LargeTest @Timeout(10)
    void lsystemRender() {
        LSystemParams params = new LSystemParams(25f, 0.7f, 5, 0.9f, 1);
        LSystemRenderer renderer = new LSystemRenderer(params, ColorGradient.fractalDefault());
        BufferedImage img = renderer.render(400, 400);
        assertNotNull(img);
        assertEquals(400, img.getWidth());
        assertEquals(400, img.getHeight());

        // Image should not be blank (all one color)
        assertFalse(TestHelpers.isAllColor(img, 0x0A0A14),
                "Rendered tree should have visible content");
    }

    @Test @MediumTest
    void differentParamsProduceDifferentTrees() {
        ColorGradient grad = ColorGradient.fractalDefault();
        LSystemParams p1 = new LSystemParams(20f, 0.7f, 4, 1.0f, 0);
        LSystemParams p2 = new LSystemParams(35f, 0.8f, 4, 1.0f, 1);

        LSystemRenderer r1 = new LSystemRenderer(p1, grad);
        LSystemRenderer r2 = new LSystemRenderer(p2, grad);

        String s1 = r1.generate();
        String s2 = r2.generate();
        assertNotEquals(s1, s2, "Different params should produce different L-System strings");
    }

    // -----------------------------------------------------------------------
    // End-to-end with real fractal renderer
    // -----------------------------------------------------------------------

    @Test @LargeTest @Timeout(15)
    void iterationTerrainFromRenderer() {
        FractalRenderer renderer = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();
        renderer.render(SIZE, SIZE, grad);

        int[] iters = renderer.getLastRenderIters();
        assertNotNull(iters, "Render should produce iteration data");

        int[] sz = renderer.getLastRenderSize();
        int mapSize = 65;
        float[] heightmap = PCGController.resampleToHeightmap(
                iters, sz[0], sz[1], mapSize, renderer.getMaxIterations());
        int[] colormap = PCGController.buildIterationColormap(
                iters, sz[0], sz[1], mapSize, renderer.getMaxIterations(), grad);

        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);
        BufferedImage frame = tr.render(320, 240, mapSize / 2f, mapSize / 2f,
                80f, 0f, 0f);
        assertNotNull(frame);
        assertEquals(320, frame.getWidth());
    }

    @Test @LargeTest @Timeout(15)
    void lsystemFromRenderer() {
        FractalRenderer renderer = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();
        renderer.render(SIZE, SIZE, grad);

        int[] iters = renderer.getLastRenderIters();
        int[] sz = renderer.getLastRenderSize();
        LSystemParams params = PCGController.deriveTreeParams(
                iters, sz[0], sz[1], renderer.getMaxIterations());
        assertNotNull(params);

        LSystemRenderer lsr = new LSystemRenderer(params, grad);
        BufferedImage img = lsr.render(300, 300);
        assertNotNull(img);
    }
}

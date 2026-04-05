package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;

class TerrainRenderTest {

    @Test @LargeTest @Timeout(10)
    void terrainGeneration() {
        int power = 6;
        int mapSize = (1 << power) + 1;
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        assertEquals(mapSize * mapSize, heightmap.length);

        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (float v : heightmap) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        assertTrue(min >= -0.01f && min <= 0.01f, "Min should be ~0");
        assertTrue(max >= 0.99f && max <= 1.01f, "Max should be ~1");

        float sum = 0;
        for (float v : heightmap) sum += v;
        float mean = sum / heightmap.length;
        assertTrue(mean > 0.2f && mean < 0.8f, "Mean should be reasonable");
    }

    @Test @LargeTest @Timeout(10)
    void colormap() {
        int power = 6;
        int mapSize = (1 << power) + 1;
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        int[] colormap = TerrainRenderer.buildColorMap(heightmap, TestHelpers.gradient());
        assertEquals(heightmap.length, colormap.length);

        boolean hasVariety = false;
        int first = colormap[0];
        for (int c : colormap) {
            if (c != first) { hasVariety = true; break; }
        }
        assertTrue(hasVariety, "Colormap should have variety");
    }

    @Test @LargeTest @Timeout(10)
    void rendering() {
        int power = 6;
        int mapSize = (1 << power) + 1;
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        int[] colormap = TerrainRenderer.buildColorMap(heightmap, TestHelpers.gradient());

        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);
        BufferedImage frame = tr.render(160, 120, mapSize / 2f, mapSize / 4f, 80, 1.57f, 0);
        assertNotNull(frame);
        assertEquals(160, frame.getWidth());
        assertEquals(120, frame.getHeight());

        int firstPixel = frame.getRGB(0, 0);
        boolean hasVariety = false;
        for (int y = 0; y < 120 && !hasVariety; y += 10) {
            for (int x = 0; x < 160 && !hasVariety; x += 10) {
                if (frame.getRGB(x, y) != firstPixel) hasVariety = true;
            }
        }
        assertTrue(hasVariety, "Rendered frame should have color variety");
    }

    @Test @LargeTest @Timeout(10)
    void fogBlending() {
        int fogResult = TerrainRenderer.blendColor(0xFFFFFF, 0x000000, 0.5f);
        int fogR = (fogResult >> 16) & 0xFF;
        assertTrue(fogR >= 125 && fogR <= 129, "50% fog blend should give ~127");
    }

    @Test @LargeTest @Timeout(10)
    void startPosition() {
        int power = 6;
        int mapSize = (1 << power) + 1;
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        int[] colormap = TerrainRenderer.buildColorMap(heightmap, TestHelpers.gradient());
        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);

        float[] startPos = tr.findStartPosition();
        assertEquals(3, startPos.length);
        assertTrue(startPos[0] >= 0 && startPos[0] < mapSize);
        assertTrue(startPos[1] >= 0 && startPos[1] < mapSize);
        assertTrue(startPos[2] > 0);
    }

    @Test @LargeTest @Timeout(10)
    void determinism() {
        int power = 6;
        float[] h1 = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        float[] h2 = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        for (int i = 0; i < h1.length; i++) {
            assertEquals(h1[i], h2[i], "Same seed should produce identical terrain");
        }

        float[] h3 = TerrainRenderer.generateTerrain(power, 0.5f, 99);
        boolean different = false;
        for (int i = 0; i < h1.length; i++) {
            if (h1[i] != h3[i]) { different = true; break; }
        }
        assertTrue(different, "Different seed should produce different terrain");
    }

    @Test @LargeTest @Timeout(10)
    void rendererExposesIterationData() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.render(50, 50, TestHelpers.gradient());
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();
        assertNotNull(iters);
        assertEquals(2500, iters.length);
        assertEquals(50, size[0]);
        assertEquals(50, size[1]);
    }
}

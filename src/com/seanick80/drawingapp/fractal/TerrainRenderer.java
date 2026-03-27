package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;

/**
 * Voxel space terrain renderer with diamond-square fractal terrain generation.
 * Renders a perspective view from a camera position using column-based
 * raycasting (Comanche-style).
 */
public class TerrainRenderer {

    private final float[] heightmap;    // normalized 0..1
    private final int[] colormap;       // ARGB per pixel
    private final int mapSize;          // always power-of-2 + 1

    // Rendering parameters
    private float heightScale = 120f;
    private float renderDistance = 600f;
    private float fogStart = 300f;
    private int fogColor = 0x1a1a2e;
    private int skyColor = 0x0a0a1a;

    public TerrainRenderer(float[] heightmap, int[] colormap, int mapSize) {
        this.heightmap = heightmap;
        this.colormap = colormap;
        this.mapSize = mapSize;
    }

    public void setHeightScale(float s) { this.heightScale = s; }
    public float getHeightScale() { return heightScale; }
    public void setRenderDistance(float d) { this.renderDistance = d; }
    public void setFogStart(float f) { this.fogStart = f; }
    public void setFogColor(int c) { this.fogColor = c; }
    public void setSkyColor(int c) { this.skyColor = c; }
    public float[] getHeightmap() { return heightmap; }
    public int getMapSize() { return mapSize; }

    /**
     * Render a single frame from the given camera state.
     */
    public BufferedImage render(int screenW, int screenH,
                                float camX, float camY, float camAlt,
                                float heading, float pitch) {
        int[] pixels = new int[screenW * screenH];
        Arrays.fill(pixels, skyColor);

        float sinH = (float) Math.sin(heading);
        float cosH = (float) Math.cos(heading);
        float fov = 1.0f;

        for (int col = 0; col < screenW; col++) {
            float rayAngle = (col - screenW / 2.0f) / screenW * fov;
            float dx = cosH - sinH * rayAngle;
            float dy = sinH + cosH * rayAngle;

            renderColumn(pixels, screenW, screenH, col,
                         camX, camY, camAlt, dx, dy, pitch);
        }

        BufferedImage img = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, screenW, screenH, pixels, 0, screenW);
        return img;
    }

    private void renderColumn(int[] pixels, int screenW, int screenH, int col,
                               float camX, float camY, float camAlt,
                               float dx, float dy, float pitch) {
        int yMax = screenH;
        float stepSize = 1.0f;
        float dist = 1.0f;

        while (dist < renderDistance && yMax > 0) {
            float sampleX = camX + dx * dist;
            float sampleY = camY + dy * dist;

            // Wrap coordinates to tile the heightmap
            int ix = ((int) sampleX % mapSize + mapSize) % mapSize;
            int iy = ((int) sampleY % mapSize + mapSize) % mapSize;
            int idx = iy * mapSize + ix;

            float terrainH = heightmap[idx] * heightScale;

            // Perspective projection
            float relHeight = (camAlt - terrainH);
            int screenY = (int) (screenH / 2.0f - (relHeight / dist) * screenH + pitch);

            if (screenY < yMax) {
                int baseColor = colormap[idx];
                int color = applyFog(baseColor, dist);

                int drawFrom = Math.max(0, screenY);
                int drawTo = Math.min(screenH, yMax);
                for (int y = drawFrom; y < drawTo; y++) {
                    pixels[y * screenW + col] = color;
                }
                yMax = screenY;
            }

            dist += stepSize;
            if (dist > 50) stepSize = 2.0f;
            if (dist > 150) stepSize = 4.0f;
        }
    }

    private int applyFog(int color, float dist) {
        if (dist <= fogStart) return color;
        float fogFactor = Math.min(1.0f, (dist - fogStart) / (renderDistance - fogStart));
        return blendColor(color, fogColor, fogFactor);
    }

    static int blendColor(int c1, int c2, float t) {
        float s = 1.0f - t;
        int r = (int) (((c1 >> 16) & 0xFF) * s + ((c2 >> 16) & 0xFF) * t);
        int g = (int) (((c1 >> 8) & 0xFF) * s + ((c2 >> 8) & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * s + (c2 & 0xFF) * t);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // -----------------------------------------------------------------------
    // Diamond-square fractal terrain generation
    // -----------------------------------------------------------------------

    /**
     * Generate a fractal terrain heightmap using diamond-square algorithm.
     *
     * @param power    map size = 2^power + 1 (e.g., 9 → 513x513)
     * @param roughness how jagged the terrain is (0.4 = smooth hills, 0.7 = mountains)
     * @param seed     random seed for reproducibility
     * @return normalized heightmap (values 0..1), size = (2^power+1)^2
     */
    public static float[] generateTerrain(int power, float roughness, long seed) {
        int size = (1 << power) + 1;
        float[] map = new float[size * size];
        Random rng = new Random(seed);

        // Seed corners
        map[0] = rng.nextFloat();
        map[size - 1] = rng.nextFloat();
        map[(size - 1) * size] = rng.nextFloat();
        map[(size - 1) * size + size - 1] = rng.nextFloat();

        float range = 1.0f;
        for (int step = size - 1; step >= 2; step /= 2) {
            int half = step / 2;

            // Diamond step
            for (int y = 0; y < size - 1; y += step) {
                for (int x = 0; x < size - 1; x += step) {
                    float avg = (map[y * size + x]
                            + map[y * size + x + step]
                            + map[(y + step) * size + x]
                            + map[(y + step) * size + x + step]) / 4.0f;
                    map[(y + half) * size + (x + half)] = avg + (rng.nextFloat() - 0.5f) * range;
                }
            }

            // Square step
            for (int y = 0; y < size; y += half) {
                for (int x = (y / half % 2 == 0 ? half : 0); x < size; x += step) {
                    float sum = 0;
                    int count = 0;
                    if (y >= half) { sum += map[(y - half) * size + x]; count++; }
                    if (y + half < size) { sum += map[(y + half) * size + x]; count++; }
                    if (x >= half) { sum += map[y * size + x - half]; count++; }
                    if (x + half < size) { sum += map[y * size + x + half]; count++; }
                    map[y * size + x] = sum / count + (rng.nextFloat() - 0.5f) * range;
                }
            }

            range *= roughness;
        }

        // Normalize to 0..1
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (float v : map) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float span = max - min;
        if (span > 0) {
            for (int i = 0; i < map.length; i++) {
                map[i] = (map[i] - min) / span;
            }
        }

        return map;
    }

    /**
     * Build a color map from a heightmap using a gradient.
     * Maps elevation 0..1 to gradient colors.
     */
    public static int[] buildColorMap(float[] heightmap, ColorGradient gradient) {
        Color[] lut = gradient.toColors(256);
        int[] colors = new int[heightmap.length];
        for (int i = 0; i < heightmap.length; i++) {
            int lutIdx = Math.min(255, Math.max(0, (int) (heightmap[i] * 255)));
            Color c = lut[lutIdx];
            colors[i] = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
        }
        return colors;
    }


    /**
     * Find a good starting camera position: scan for terrain with good
     * height variation and start above it.
     */
    public float[] findStartPosition() {
        int bestX = mapSize / 2, bestY = mapSize / 4;
        float bestVariance = 0;
        int scanStep = mapSize / 20;
        int radius = mapSize / 30;

        for (int y = radius; y < mapSize - radius; y += scanStep) {
            for (int x = radius; x < mapSize - radius; x += scanStep) {
                float sum = 0, sumSq = 0;
                int n = 0;
                for (int dy = -radius; dy <= radius; dy += 2) {
                    for (int dx = -radius; dx <= radius; dx += 2) {
                        float h = heightmap[(y + dy) * mapSize + (x + dx)];
                        sum += h;
                        sumSq += h * h;
                        n++;
                    }
                }
                float mean = sum / n;
                float variance = sumSq / n - mean * mean;
                if (variance > bestVariance) {
                    bestVariance = variance;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        float groundH = heightmap[bestY * mapSize + bestX] * heightScale;
        return new float[] { bestX, bestY, groundH + heightScale * 0.6f };
    }
}

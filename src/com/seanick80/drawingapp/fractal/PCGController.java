package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;

/**
 * Procedural Content Generation from fractal iteration data.
 * Provides methods to transform the iteration cache into terrain
 * heightmaps and L-System tree parameters.
 */
public final class PCGController {

    private PCGController() {}

    /**
     * Open a 3D terrain flyover using the fractal's iteration cache as the heightmap.
     * The iteration counts become terrain elevation, and the gradient provides color.
     */
    public static void openIterationTerrain(FractalRenderer renderer, ColorGradient gradient) {
        int[] iters = renderer.getLastRenderIters();
        int[] size = renderer.getLastRenderSize();
        if (iters == null || size[0] <= 0) return;

        int srcW = size[0], srcH = size[1];
        int maxIter = renderer.getMaxIterations();

        // TerrainRenderer needs power-of-2+1 square maps. Pick the largest
        // power that fits within our iteration data, capped at 9 (513x513).
        int power = 9;
        int mapSize = (1 << power) + 1; // 513

        float[] heightmap = resampleToHeightmap(iters, srcW, srcH, mapSize, maxIter);
        int[] colormap = buildIterationColormap(iters, srcW, srcH, mapSize, maxIter, gradient);

        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);
        tr.setHeightScale(150f);
        TerrainViewer viewer = new TerrainViewer(tr);
        viewer.setTitle("Fractal Iteration Terrain");
        viewer.start();
    }

    /**
     * Open an L-System tree viewer parameterized by the fractal's iteration data.
     */
    public static void openLSystemTree(FractalRenderer renderer, ColorGradient gradient) {
        int[] iters = renderer.getLastRenderIters();
        int[] size = renderer.getLastRenderSize();
        if (iters == null || size[0] <= 0) return;

        int srcW = size[0], srcH = size[1];
        int maxIter = renderer.getMaxIterations();

        LSystemParams params = deriveTreeParams(iters, srcW, srcH, maxIter);
        LSystemRenderer lsr = new LSystemRenderer(params, gradient);
        LSystemViewer.open(lsr);
    }

    // -----------------------------------------------------------------------
    // Heightmap conversion
    // -----------------------------------------------------------------------

    /**
     * Resample the iteration array into a square heightmap normalized to 0..1.
     * Uses bilinear interpolation for smooth results.
     */
    static float[] resampleToHeightmap(int[] iters, int srcW, int srcH,
                                        int mapSize, int maxIter) {
        float[] heightmap = new float[mapSize * mapSize];

        for (int my = 0; my < mapSize; my++) {
            float srcY = (float) my / (mapSize - 1) * (srcH - 1);
            int sy0 = Math.min((int) srcY, srcH - 2);
            int sy1 = sy0 + 1;
            float fy = srcY - sy0;

            for (int mx = 0; mx < mapSize; mx++) {
                float srcX = (float) mx / (mapSize - 1) * (srcW - 1);
                int sx0 = Math.min((int) srcX, srcW - 2);
                int sx1 = sx0 + 1;
                float fx = srcX - sx0;

                // Bilinear interpolation of iteration counts
                float v00 = iters[sy0 * srcW + sx0];
                float v10 = iters[sy0 * srcW + sx1];
                float v01 = iters[sy1 * srcW + sx0];
                float v11 = iters[sy1 * srcW + sx1];

                float v = v00 * (1 - fx) * (1 - fy)
                        + v10 * fx * (1 - fy)
                        + v01 * (1 - fx) * fy
                        + v11 * fx * fy;

                // Normalize: interior points (maxIter) become peaks
                heightmap[my * mapSize + mx] = v / maxIter;
            }
        }
        return heightmap;
    }

    /**
     * Build a colormap by sampling the gradient at iteration-based positions.
     */
    static int[] buildIterationColormap(int[] iters, int srcW, int srcH,
                                         int mapSize, int maxIter,
                                         ColorGradient gradient) {
        Color[] lut = gradient.toColors(256);
        int[] colormap = new int[mapSize * mapSize];

        for (int my = 0; my < mapSize; my++) {
            int sy = Math.min((int) ((float) my / (mapSize - 1) * (srcH - 1)), srcH - 1);
            for (int mx = 0; mx < mapSize; mx++) {
                int sx = Math.min((int) ((float) mx / (mapSize - 1) * (srcW - 1)), srcW - 1);
                int iter = iters[sy * srcW + sx];
                int lutIdx = Math.min(255, iter * 255 / Math.max(1, maxIter));
                Color c = lut[lutIdx];
                colormap[my * mapSize + mx] = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
            }
        }
        return colormap;
    }

    // -----------------------------------------------------------------------
    // L-System parameter derivation
    // -----------------------------------------------------------------------

    /**
     * Derive L-System tree parameters from the iteration field.
     * Samples the center region and extracts statistics that drive
     * branching angle, length, depth, and style.
     */
    static LSystemParams deriveTreeParams(int[] iters, int w, int h, int maxIter) {
        // Sample a region around the center
        int cx = w / 2, cy = h / 2;
        int radius = Math.min(w, h) / 4;

        float sum = 0, sumSq = 0;
        int count = 0;
        int interiorCount = 0;
        float maxGradient = 0;

        for (int y = cy - radius; y <= cy + radius; y += 2) {
            for (int x = cx - radius; x <= cx + radius; x += 2) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                int iter = iters[y * w + x];
                float normalized = (float) iter / maxIter;
                sum += normalized;
                sumSq += normalized * normalized;
                count++;
                if (iter >= maxIter) interiorCount++;

                // Compute gradient magnitude
                if (x + 1 < w && y + 1 < h) {
                    float dx = Math.abs(iters[y * w + x + 1] - iter);
                    float dy = Math.abs(iters[(y + 1) * w + x] - iter);
                    float grad = (float) Math.sqrt(dx * dx + dy * dy) / maxIter;
                    if (grad > maxGradient) maxGradient = grad;
                }
            }
        }

        float mean = sum / Math.max(1, count);
        float variance = sumSq / Math.max(1, count) - mean * mean;
        float interiorRatio = (float) interiorCount / Math.max(1, count);

        // Map statistics to L-System parameters
        float angle = 15f + mean * 30f;                         // 15-45 degrees
        float lengthDecay = 0.6f + variance * 0.3f;             // 0.6-0.9
        int depth = 4 + Math.round(mean * 4);                   // 4-8 generations
        depth = Math.min(8, Math.max(4, depth));
        float branchProb = 0.7f + maxGradient * 0.3f;           // 0.7-1.0
        int ruleVariant = interiorRatio > 0.3f ? 1 : 0;         // bushy vs sparse

        return new LSystemParams(angle, lengthDecay, depth, branchProb, ruleVariant);
    }
}

package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;

/**
 * Builds a colour look-up table from a gradient and maps iteration counts to RGB values.
 */
public final class FractalColorMapper {

    private final int[] lut;
    private final int maxIterations;
    private final FractalRenderer.ColorMode colorMode;

    public FractalColorMapper(ColorGradient gradient, int maxIterations,
                              FractalRenderer.ColorMode colorMode) {
        this.maxIterations = maxIterations;
        this.colorMode = colorMode;
        int size = (colorMode == FractalRenderer.ColorMode.MOD) ? 64 : maxIterations;
        Color[] colors = gradient.toColors(size);
        this.lut = new int[size];
        for (int i = 0; i < size; i++) lut[i] = colors[i].getRGB();
    }

    /** Map an iteration count to an ARGB colour value. */
    public int colorForIter(int iter) {
        if (iter >= maxIterations) return Color.BLACK.getRGB();
        if (colorMode == FractalRenderer.ColorMode.MOD) return lut[iter % lut.length];
        return lut[iter];
    }

    /** Expose the raw LUT for tests. */
    public int[] getLut() { return lut; }
}

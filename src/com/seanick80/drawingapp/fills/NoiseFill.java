package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

public class NoiseFill implements FillProvider {
    @Override public String getName() { return "Noise"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        int cellSize = 32;
        BufferedImage pattern = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);

        int r = baseColor.getRed();
        int g = baseColor.getGreen();
        int b = baseColor.getBlue();

        // Seeded random for deterministic pattern at same position
        Random rng = new Random((long) x * 31 + y);
        for (int py = 0; py < cellSize; py++) {
            for (int px = 0; px < cellSize; px++) {
                float noise = rng.nextFloat();
                int alpha = (int) (noise * 200) + 55; // 55-255 range
                pattern.setRGB(px, py, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return new TexturePaint(pattern, new Rectangle(x, y, cellSize, cellSize));
    }
}

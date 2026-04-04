package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class DotGridFill implements FillProvider {
    @Override public String getName() { return "Dot Grid"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        int spacing = 12;
        int dotRadius = 3;
        int cellSize = spacing;
        BufferedImage pattern = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pattern.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Light background
        Color bg = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 50);
        g.setColor(bg);
        g.fillRect(0, 0, cellSize, cellSize);

        // Draw dot at center of cell
        g.setColor(baseColor);
        int cx = cellSize / 2;
        int cy = cellSize / 2;
        g.fillOval(cx - dotRadius, cy - dotRadius, dotRadius * 2, dotRadius * 2);

        g.dispose();
        return new TexturePaint(pattern, new Rectangle(x, y, cellSize, cellSize));
    }
}

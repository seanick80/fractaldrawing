package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CheckerboardFill implements FillProvider {
    @Override public String getName() { return "Checkerboard"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        int cellSize = 8;
        BufferedImage pattern = new BufferedImage(cellSize * 2, cellSize * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pattern.createGraphics();

        Color light = baseColor;
        Color dark = baseColor.darker().darker();

        g.setColor(light);
        g.fillRect(0, 0, cellSize * 2, cellSize * 2);
        g.setColor(dark);
        g.fillRect(0, 0, cellSize, cellSize);
        g.fillRect(cellSize, cellSize, cellSize, cellSize);
        g.dispose();

        return new TexturePaint(pattern, new Rectangle(x, y, cellSize * 2, cellSize * 2));
    }
}

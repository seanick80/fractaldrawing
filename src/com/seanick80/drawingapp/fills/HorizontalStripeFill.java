package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class HorizontalStripeFill implements AngledFillProvider {
    private int angleDegrees = 0;

    @Override public String getName() { return "Horizontal Stripes"; }
    @Override public int getAngleDegrees() { return angleDegrees; }
    @Override public void setAngleDegrees(int angle) { this.angleDegrees = angle; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return baseColor;

        int spacing = 10;
        int cellSize = spacing * 2;
        BufferedImage pattern = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pattern.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Light background
        Color bg = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 60);
        g.setColor(bg);
        g.fillRect(0, 0, cellSize, cellSize);

        // Draw horizontal stripe rotated by angle
        g.rotate(Math.toRadians(angleDegrees), cellSize / 2.0, cellSize / 2.0);
        g.setColor(baseColor);
        g.setStroke(new BasicStroke(3));

        int diag = (int) Math.ceil(Math.sqrt(cellSize * cellSize * 2));
        int cx = cellSize / 2;
        int cy = cellSize / 2;
        for (int i = -diag; i <= diag; i += spacing) {
            g.drawLine(cx - diag, cy + i, cx + diag, cy + i);
        }

        g.dispose();
        return new TexturePaint(pattern, new Rectangle(x, y, cellSize, cellSize));
    }
}

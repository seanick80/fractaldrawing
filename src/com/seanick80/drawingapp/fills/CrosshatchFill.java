package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CrosshatchFill implements AngledFillProvider {
    private int angleDegrees = 45;

    @Override public String getName() { return "Crosshatch"; }
    @Override public int getAngleDegrees() { return angleDegrees; }
    @Override public void setAngleDegrees(int angle) { this.angleDegrees = angle; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return baseColor;

        int spacing = 12;
        int cellSize = spacing * 2;
        BufferedImage pattern = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pattern.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Light background
        Color bg = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 40);
        g.setColor(bg);
        g.fillRect(0, 0, cellSize, cellSize);

        // Draw crosshatch lines
        g.setColor(baseColor);
        g.setStroke(new BasicStroke(1.5f));

        double rad = Math.toRadians(angleDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        int cx = cellSize / 2;
        int cy = cellSize / 2;
        int diag = cellSize * 2;

        // First set of parallel lines
        for (int i = -diag; i <= diag; i += spacing) {
            int x1 = (int) (cx + (-diag) * cos - (cy + i) * sin + cx) - cx;
            int y1 = (int) (cy + (-diag) * sin + (cy + i) * cos + cy) - cy;
            int x2 = (int) (cx + diag * cos - (cy + i) * sin + cx) - cx;
            int y2 = (int) (cy + diag * sin + (cy + i) * cos + cy) - cy;
            g.drawLine(x1, y1, x2, y2);
        }

        // Perpendicular set
        double rad2 = rad + Math.PI / 2;
        double cos2 = Math.cos(rad2);
        double sin2 = Math.sin(rad2);
        for (int i = -diag; i <= diag; i += spacing) {
            int x1 = (int) (cx + (-diag) * cos2 - (cy + i) * sin2 + cx) - cx;
            int y1 = (int) (cy + (-diag) * sin2 + (cy + i) * cos2 + cy) - cy;
            int x2 = (int) (cx + diag * cos2 - (cy + i) * sin2 + cx) - cx;
            int y2 = (int) (cy + diag * sin2 + (cy + i) * cos2 + cy) - cy;
            g.drawLine(x1, y1, x2, y2);
        }

        g.dispose();
        return new TexturePaint(pattern, new Rectangle(x, y, cellSize, cellSize));
    }
}

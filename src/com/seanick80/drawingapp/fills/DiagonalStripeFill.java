package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class DiagonalStripeFill implements AngledFillProvider {
    private int angleDegrees = 45;

    @Override public String getName() { return "Diagonal Stripes"; }
    @Override public int getAngleDegrees() { return angleDegrees; }
    @Override public void setAngleDegrees(int angle) { this.angleDegrees = angle; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return baseColor;

        int spacing = 16;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill background
        Color bg = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 80);
        g.setColor(bg);
        g.fillRect(0, 0, width, height);

        // Draw stripes rotated around the center of the fill area
        g.rotate(Math.toRadians(angleDegrees), width / 2.0, height / 2.0);
        g.setColor(baseColor);
        g.setStroke(new BasicStroke(3));

        // Diagonal of the bounding box — enough to cover all rotations
        int diag = (int) Math.ceil(Math.sqrt(width * width + height * height));
        int cx = width / 2;
        int cy = height / 2;
        for (int i = -diag; i <= diag; i += spacing) {
            g.drawLine(cx - diag, cy + i, cx + diag, cy + i);
        }
        g.dispose();

        return new TexturePaint(img, new java.awt.geom.Rectangle2D.Float(x, y, width, height));
    }
}

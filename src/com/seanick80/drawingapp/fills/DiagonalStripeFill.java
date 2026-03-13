package com.seanick80.drawingapp.fills;

import java.awt.*;
import java.awt.image.BufferedImage;

public class DiagonalStripeFill implements FillProvider {
    @Override public String getName() { return "Diagonal Stripes"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        int size = 16;
        BufferedImage pattern = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pattern.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 80);
        g.setColor(bg);
        g.fillRect(0, 0, size, size);

        g.setColor(baseColor);
        g.setStroke(new BasicStroke(3));
        g.drawLine(0, size, size, 0);
        g.drawLine(-size / 2, size / 2, size / 2, -size / 2);
        g.drawLine(size / 2, size + size / 2, size + size / 2, size / 2);
        g.dispose();

        return new TexturePaint(pattern, new Rectangle(x, y, size, size));
    }
}

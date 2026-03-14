package com.seanick80.drawingapp.fills;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Fill provider that uses a user-defined ColorGradient.
 * The gradient is applied along a configurable angle.
 */
public class CustomGradientFill implements FillProvider {

    private ColorGradient gradient = new ColorGradient();
    private int angleDegrees = 0; // 0=left-to-right, 90=top-to-bottom, etc.

    @Override
    public String getName() { return "Custom Gradient"; }

    public ColorGradient getGradient() { return gradient; }
    public void setGradient(ColorGradient gradient) { this.gradient = gradient; }

    public int getAngleDegrees() { return angleDegrees; }
    public void setAngleDegrees(int angleDegrees) { this.angleDegrees = angleDegrees; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return baseColor;

        // Render the gradient into a 2D texture at the specified angle
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        double rad = Math.toRadians(angleDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // The gradient axis projects each pixel onto the direction vector.
        // Calculate the projected range so the gradient spans the full shape.
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int[] corner : new int[][]{{0, 0}, {width, 0}, {0, height}, {width, height}}) {
            double proj = corner[0] * cos + corner[1] * sin;
            min = Math.min(min, proj);
            max = Math.max(max, proj);
        }
        double range = max - min;
        if (range == 0) range = 1;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double proj = px * cos + py * sin;
                float t = (float) ((proj - min) / range);
                img.setRGB(px, py, gradient.getColorAt(t).getRGB());
            }
        }

        return new TexturePaint(img, new java.awt.geom.Rectangle2D.Float(x, y, width, height));
    }
}

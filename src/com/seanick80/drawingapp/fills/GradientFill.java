package com.seanick80.drawingapp.fills;

import java.awt.*;

public class GradientFill implements AngledFillProvider {
    private int angleDegrees = 0;

    @Override public String getName() { return "Gradient"; }
    @Override public int getAngleDegrees() { return angleDegrees; }
    @Override public void setAngleDegrees(int angle) { this.angleDegrees = angle; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return baseColor;

        Color lighter = new Color(
            baseColor.getRed() + (255 - baseColor.getRed()) * 3 / 4,
            baseColor.getGreen() + (255 - baseColor.getGreen()) * 3 / 4,
            baseColor.getBlue() + (255 - baseColor.getBlue()) * 3 / 4
        );

        double rad = Math.toRadians(angleDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // Project corners to find gradient extent
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int[] corner : new int[][]{{0, 0}, {width, 0}, {0, height}, {width, height}}) {
            double proj = corner[0] * cos + corner[1] * sin;
            min = Math.min(min, proj);
            max = Math.max(max, proj);
        }

        // Map projected extremes back to start/end points
        float x1 = (float) (x + min * cos);
        float y1 = (float) (y + min * sin);
        float x2 = (float) (x + max * cos);
        float y2 = (float) (y + max * sin);

        return new GradientPaint(x1, y1, lighter, x2, y2, baseColor);
    }
}

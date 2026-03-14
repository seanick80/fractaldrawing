package com.seanick80.drawingapp.fills;

import java.awt.*;

public class GradientFill implements FillProvider {
    @Override public String getName() { return "Gradient"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        Color lighter = new Color(
            baseColor.getRed() + (255 - baseColor.getRed()) * 3 / 4,
            baseColor.getGreen() + (255 - baseColor.getGreen()) * 3 / 4,
            baseColor.getBlue() + (255 - baseColor.getBlue()) * 3 / 4
        );
        return new GradientPaint(x, y, lighter, x + width, y + height, baseColor);
    }
}

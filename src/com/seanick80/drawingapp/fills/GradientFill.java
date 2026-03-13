package com.seanick80.drawingapp.fills;

import java.awt.*;

public class GradientFill implements FillProvider {
    @Override public String getName() { return "Gradient"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        Color lighter = baseColor.brighter().brighter();
        return new GradientPaint(x, y, lighter, x + width, y + height, baseColor);
    }
}

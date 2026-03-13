package com.seanick80.drawingapp.fills;

import java.awt.Color;
import java.awt.Paint;

public class SolidFill implements FillProvider {
    @Override public String getName() { return "Solid"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int width, int height) {
        return baseColor;
    }
}

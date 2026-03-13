package com.seanick80.drawingapp.fills;

import java.awt.Color;
import java.awt.Paint;

/**
 * Interface for pluggable fill patterns. Implement this to add custom fills.
 *
 * To add a new fill:
 * 1. Create a class implementing FillProvider
 * 2. Register it in DrawingApp.registerDefaultFills()
 *
 * The fill will automatically appear in the toolbar's fill dropdown.
 */
public interface FillProvider {
    String getName();
    Paint createPaint(Color baseColor, int x, int y, int width, int height);
}

package com.seanick80.drawingapp.fills;

/**
 * A fill provider that supports a configurable angle.
 * Fills implementing this interface will show the angle dial in the toolbar.
 */
public interface AngledFillProvider extends FillProvider {
    int getAngleDegrees();
    void setAngleDegrees(int angle);
}

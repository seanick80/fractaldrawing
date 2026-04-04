package com.seanick80.drawingapp.tools;

import java.awt.BasicStroke;
import java.awt.Stroke;

public enum StrokeStyle {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted"),
    DASH_DOT("Dash-Dot"),
    ROUGH("Rough");

    private final String displayName;

    StrokeStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    public Stroke createStroke(float size) {
        switch (this) {
            case DASHED:
                return new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10.0f, new float[]{size * 4, size * 3}, 0);
            case DOTTED:
                return new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10.0f, new float[]{1.0f, size * 2.5f}, 0);
            case DASH_DOT:
                return new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10.0f, new float[]{size * 4, size * 2, 1.0f, size * 2}, 0);
            case ROUGH:
            case SOLID:
            default:
                return new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
    }

    @Override
    public String toString() { return displayName; }
}

package com.seanick80.drawingapp.layers;

/**
 * Blend modes for layer compositing.
 * Each mode defines how source pixels combine with destination pixels.
 */
public enum BlendMode {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
    HARD_LIGHT("Hard Light"),
    DIFFERENCE("Difference"),
    ADD("Add");

    private final String displayName;

    BlendMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

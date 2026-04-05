package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fills.FillRegistry;
import com.seanick80.drawingapp.gradient.GradientToolbar;

/**
 * Context passed to tools when building their settings panels.
 * Provides access to shared resources (fill registry, gradient toolbar).
 */
public interface ToolSettingsContext {
    /** The fill registry for creating fill option panels. */
    FillRegistry getFillRegistry();

    /** The gradient toolbar for gradient sync. */
    GradientToolbar getGradientToolbar();
}

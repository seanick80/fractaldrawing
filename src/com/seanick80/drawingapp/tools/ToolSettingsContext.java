package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fills.FillRegistry;
import com.seanick80.drawingapp.gradient.GradientToolbar;
import javax.swing.JPanel;

/**
 * Provides reusable settings components that tools can compose
 * into their custom settings panels.
 */
public interface ToolSettingsContext {
    /** Panel with stroke size spinner + dot preview. */
    JPanel getStrokeSizePanel();

    /** Panel with fill checkbox + fill type dropdown. */
    JPanel getFillOptionsPanel();

    /** The fill registry for creating fill option panels. */
    FillRegistry getFillRegistry();

    /** The gradient toolbar for gradient sync. */
    GradientToolbar getGradientToolbar();
}

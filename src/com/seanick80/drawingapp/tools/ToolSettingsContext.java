package com.seanick80.drawingapp.tools;

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
}

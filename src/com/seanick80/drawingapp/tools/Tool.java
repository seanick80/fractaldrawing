package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fills.FillProvider;
import javax.swing.JMenu;
import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface Tool {
    String getName();
    void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void drawPreview(Graphics2D g);

    /** Called when the mouse wheel is scrolled over the canvas. */
    default void mouseWheelMoved(BufferedImage image, int x, int y, int wheelRotation, DrawingCanvas canvas) {}

    /** Called when this tool becomes the active tool. */
    default void onActivated(BufferedImage image, DrawingCanvas canvas) {}

    /** Called when the tool is deactivated (another tool is selected). */
    default void onDeactivated() {}

    /** Returns a JMenu to add to the menu bar when this tool is active, or null. */
    default JMenu getMenu() { return null; }

    /** Default stroke size for this tool. */
    default int getDefaultStrokeSize() { return 2; }

    /** Whether this tool supports stroke size adjustment. */
    default boolean hasStrokeSize() { return false; }

    /** Set the stroke size. Tools that support stroke size should override this. */
    default void setStrokeSize(int size) {}

    /** Whether this tool supports fill settings. */
    default boolean hasFill() { return false; }

    /** Set whether shapes are filled. */
    default void setFilled(boolean filled) {}

    /** Set the fill provider. */
    default void setFillProvider(FillProvider provider) {}

    /**
     * Build a custom settings panel for this tool.
     * Return null to use the default (size + preview).
     * Use ctx to get reusable setting components.
     */
    default JPanel createSettingsPanel(ToolSettingsContext ctx) { return null; }

    /**
     * Returns a callback to invoke when the gradient changes, or null if this tool
     * doesn't respond to gradient changes. Used to wire the gradient toolbar.
     */
    default Runnable getGradientChangeCallback() { return null; }
}

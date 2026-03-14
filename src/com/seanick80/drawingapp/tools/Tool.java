package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface Tool {
    String getName();
    void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void drawPreview(Graphics2D g);

    /** Default stroke size for this tool. */
    default int getDefaultStrokeSize() { return 2; }

    /**
     * Build a custom settings panel for this tool.
     * Return null to use the default (size + preview).
     * Use ctx to get reusable setting components.
     */
    default JPanel createSettingsPanel(ToolSettingsContext ctx) { return null; }
}

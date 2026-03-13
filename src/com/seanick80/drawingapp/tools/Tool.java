package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface Tool {
    String getName();
    void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas);
    void drawPreview(Graphics2D g);
}

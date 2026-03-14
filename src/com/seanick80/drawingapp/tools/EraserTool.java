package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import java.awt.*;
import java.awt.image.BufferedImage;

public class EraserTool implements Tool {

    private int lastX, lastY;
    private int size = 16;

    @Override public String getName() { return "Eraser"; }
    @Override public int getDefaultStrokeSize() { return 18; }

    public void setSize(int size) { this.size = size; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastX = x;
        lastY = y;
        erase(image, x, y, canvas);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        // Draw a thick white line to avoid gaps
        Graphics2D g = image.createGraphics();
        g.setColor(canvas.getBackgroundColor());
        g.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(lastX, lastY, x, y);
        g.dispose();
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}

    private void erase(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        Graphics2D g = image.createGraphics();
        g.setColor(canvas.getBackgroundColor());
        g.fillRect(x - size / 2, y - size / 2, size, size);
        g.dispose();
    }
}

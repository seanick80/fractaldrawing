package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LineTool implements Tool {

    private int startX, startY, endX, endY;
    private Color color;
    private boolean active;
    private int strokeSize = 2;

    @Override public String getName() { return "Line"; }

    public void setStrokeSize(int size) { this.strokeSize = size; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        startX = endX = x;
        startY = endY = y;
        color = canvas.getForegroundColor();
        active = true;
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        endX = x;
        endY = y;
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        active = false;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(startX, startY, x, y);
        g.dispose();
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (!active) return;
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(startX, startY, endX, endY);
    }
}

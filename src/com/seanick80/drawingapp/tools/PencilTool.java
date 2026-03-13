package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PencilTool implements Tool {

    private int lastX, lastY;
    private int strokeSize = 2;

    @Override public String getName() { return "Pencil"; }

    public void setStrokeSize(int size) { this.strokeSize = size; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastX = x;
        lastY = y;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(canvas.getForegroundColor());
        g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x, y, x, y);
        g.dispose();
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(canvas.getForegroundColor());
        g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(lastX, lastY, x, y);
        g.dispose();
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}
}

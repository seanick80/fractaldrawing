package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fills.FillProvider;
import java.awt.*;
import java.awt.image.BufferedImage;

public class OvalTool implements Tool {

    private int startX, startY, endX, endY;
    private Color color;
    private boolean active;
    private boolean filled;
    private FillProvider fillProvider;
    private int strokeSize = 2;

    @Override public String getName() { return "Oval"; }

    public void setFilled(boolean filled) { this.filled = filled; }
    public void setFillProvider(FillProvider fp) { this.fillProvider = fp; }
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
        drawOval(image.createGraphics(), x, y, true);
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (!active) return;
        drawOval(g, endX, endY, false);
    }

    private void drawOval(Graphics2D g, int ex, int ey, boolean dispose) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int rx = Math.min(startX, ex);
        int ry = Math.min(startY, ey);
        int rw = Math.abs(ex - startX);
        int rh = Math.abs(ey - startY);

        if (filled && fillProvider != null) {
            g.setPaint(fillProvider.createPaint(color, rx, ry, rw, rh));
            g.fillOval(rx, ry, rw, rh);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeSize));
        g.drawOval(rx, ry, rw, rh);
        if (dispose) g.dispose();
    }
}

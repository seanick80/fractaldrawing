package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fills.FillProvider;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class RectangleTool implements Tool {

    private int startX, startY, endX, endY;
    private Color color;
    private boolean active;
    private boolean filled;
    private FillProvider fillProvider;
    private int strokeSize = 2;
    private StrokeStyle strokeStyle = StrokeStyle.SOLID;

    @Override public String getName() { return "Rectangle"; }
    @Override public boolean hasStrokeSize() { return true; }
    @Override public boolean hasFill() { return true; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(ToolSettingsBuilder.createStrokeSizePanel(strokeSize, this::setStrokeSize));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ToolSettingsBuilder.createStrokeStylePanel(strokeStyle, s -> this.strokeStyle = s));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ToolSettingsBuilder.createFillOptionsPanel(
                ctx.getFillRegistry(), ctx.getGradientToolbar(), true,
                fillProvider, this::setFilled, this::setFillProvider));
        return panel;
    }

    @Override public void setFilled(boolean filled) { this.filled = filled; }
    @Override public void setFillProvider(FillProvider fp) { this.fillProvider = fp; }
    @Override public void setStrokeSize(int size) { this.strokeSize = size; }

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
        drawRect(image.createGraphics(), x, y, true);
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (!active) return;
        drawRect(g, endX, endY, false);
    }

    private void drawRect(Graphics2D g, int ex, int ey, boolean dispose) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int rx = Math.min(startX, ex);
        int ry = Math.min(startY, ey);
        int rw = Math.abs(ex - startX);
        int rh = Math.abs(ey - startY);

        if (filled && fillProvider != null) {
            g.setPaint(fillProvider.createPaint(color, rx, ry, rw, rh));
            g.fillRect(rx, ry, rw, rh);
        }
        g.setColor(color);
        g.setStroke(strokeStyle.createStroke(strokeSize));
        g.drawRect(rx, ry, rw, rh);
        if (dispose) g.dispose();
    }
}

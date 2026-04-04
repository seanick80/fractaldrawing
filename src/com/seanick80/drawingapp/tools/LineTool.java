package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

public class LineTool implements Tool {

    private int startX, startY, endX, endY;
    private Color color;
    private boolean active;
    private int strokeSize = 2;
    private StrokeStyle strokeStyle = StrokeStyle.SOLID;

    @Override public String getName() { return "Line"; }
    @Override public boolean hasStrokeSize() { return true; }

    @Override
    public void setStrokeSize(int size) { this.strokeSize = size; }

    public StrokeStyle getStrokeStyle() { return strokeStyle; }
    public void setStrokeStyle(StrokeStyle style) { this.strokeStyle = style; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel sizePanel = ToolSettingsBuilder.createStrokeSizePanel(strokeSize, this::setStrokeSize);
        JPanel stylePanel = ToolSettingsBuilder.createStrokeStylePanel(strokeStyle, this::setStrokeStyle);

        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sizePanel);
        panel.add(javax.swing.Box.createVerticalStrut(8));
        panel.add(stylePanel);
        return panel;
    }

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
        if (strokeStyle == StrokeStyle.ROUGH) {
            drawRoughLine(g, startX, startY, x, y);
        } else {
            g.setStroke(strokeStyle.createStroke(strokeSize));
            g.drawLine(startX, startY, x, y);
        }
        g.dispose();
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (!active) return;
        g.setColor(color);
        g.setStroke(strokeStyle.createStroke(strokeSize));
        g.drawLine(startX, startY, endX, endY);
    }

    private void drawRoughLine(Graphics2D g, int x1, int y1, int x2, int y2) {
        Random rng = new Random((long) x1 * 31 + y1 * 17 + x2 * 13 + y2);
        float jitter = strokeSize * 0.5f;
        g.setStroke(new BasicStroke(strokeSize * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int passes = 3;
        for (int i = 0; i < passes; i++) {
            int jx1 = x1 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jy1 = y1 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jx2 = x2 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jy2 = y2 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            g.drawLine(jx1, jy1, jx2, jy2);
        }
    }
}

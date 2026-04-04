package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

public class PencilTool implements Tool {

    private int lastX, lastY;
    private int strokeSize = 2;
    private StrokeStyle strokeStyle = StrokeStyle.SOLID;

    @Override public String getName() { return "Pencil"; }
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
        lastX = x;
        lastY = y;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(canvas.getForegroundColor());
        if (strokeStyle == StrokeStyle.ROUGH) {
            drawRoughSegment(g, x, y, x, y);
        } else {
            g.setStroke(strokeStyle.createStroke(strokeSize));
            g.drawLine(x, y, x, y);
        }
        g.dispose();
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(canvas.getForegroundColor());
        if (strokeStyle == StrokeStyle.ROUGH) {
            drawRoughSegment(g, lastX, lastY, x, y);
        } else {
            g.setStroke(strokeStyle.createStroke(strokeSize));
            g.drawLine(lastX, lastY, x, y);
        }
        g.dispose();
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}

    private void drawRoughSegment(Graphics2D g, int x1, int y1, int x2, int y2) {
        Random rng = new Random((long) x1 * 31 + y1 * 17 + x2 * 13 + y2);
        float jitter = strokeSize * 0.4f;
        g.setStroke(new BasicStroke(strokeSize * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Draw 2-3 slightly offset strokes for a rough/sketchy effect
        int passes = 2 + (strokeSize > 5 ? 1 : 0);
        for (int i = 0; i < passes; i++) {
            int jx1 = x1 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jy1 = y1 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jx2 = x2 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            int jy2 = y2 + (int) ((rng.nextFloat() - 0.5f) * jitter);
            g.drawLine(jx1, jy1, jx2, jy2);
        }
    }
}

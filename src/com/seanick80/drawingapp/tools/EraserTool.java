package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;

public class EraserTool implements Tool {

    private int lastX, lastY;
    private int size = 16;

    @Override public String getName() { return "Eraser"; }
    @Override public int getDefaultStrokeSize() { return 18; }
    @Override public boolean hasStrokeSize() { return true; }

    public void setSize(int size) { this.size = size; }

    @Override
    public void setStrokeSize(int size) { setSize(size); }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        return ToolSettingsBuilder.createStrokeSizePanel(size, this::setSize);
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastX = x;
        lastY = y;
        erase(image, x, y, canvas);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
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
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x - size / 2, y - size / 2, size, size);
        g.dispose();
    }
}

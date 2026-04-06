package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class EyedropperTool implements Tool {

    @Override
    public String getName() { return "Eyedropper"; }

    @Override
    public boolean hasStrokeSize() { return false; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) { return null; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        sampleColor(x, y, canvas);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        sampleColor(x, y, canvas);
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}

    private void sampleColor(int x, int y, DrawingCanvas canvas) {
        BufferedImage composite = canvas.getImage();
        if (composite == null) return;
        if (x < 0 || y < 0 || x >= composite.getWidth() || y >= composite.getHeight()) return;

        int rgb = composite.getRGB(x, y);
        Color sampled = new Color(rgb, false);

        if (canvas.getColorPicker() == null) return;

        if (canvas.getLastMouseButton() == MouseEvent.BUTTON3) {
            canvas.getColorPicker().setBackgroundColor(sampled);
        } else {
            canvas.getColorPicker().setForegroundColor(sampled);
        }
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

public class LassoTool implements Tool {

    private GeneralPath currentPath;
    private Area selectionArea;
    private boolean dragging;
    private long animFrame;
    private Timer antTimer;

    // Move state
    private boolean moving;
    private int startX, startY;
    private int moveOffX, moveOffY;
    private BufferedImage floatingContent;

    @Override
    public String getName() { return "Lasso"; }

    @Override
    public boolean needsPersistentPreview() { return true; }

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        antTimer = new Timer(100, e -> {
            animFrame++;
            canvas.repaint();
        });
        antTimer.start();
    }

    @Override
    public void onDeactivated() {
        if (antTimer != null) antTimer.stop();
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        // Click inside existing selection → start moving
        if (hasSelection() && selectionArea.contains(x - moveOffX, y - moveOffY)) {
            if (floatingContent == null) {
                floatingContent = copySelection(image);
                deleteSelection(image);
            }
            moving = true;
            startX = x;
            startY = y;
            return;
        }

        // Click outside → commit any floating content, then start new lasso
        commitFloating(image);
        selectionArea = null;
        moveOffX = 0;
        moveOffY = 0;
        currentPath = new GeneralPath();
        currentPath.moveTo(x, y);
        dragging = true;
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragging && currentPath != null) {
            currentPath.lineTo(x, y);
        } else if (moving) {
            moveOffX += x - startX;
            moveOffY += y - startY;
            startX = x;
            startY = y;
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragging && currentPath != null) {
            currentPath.closePath();
            selectionArea = new Area(currentPath);
            currentPath = null;
            dragging = false;
        } else if (moving) {
            moving = false;
        }
    }

    @Override
    public void drawPreview(Graphics2D g) {
        // Draw floating content at offset
        if (floatingContent != null && selectionArea != null) {
            Rectangle bounds = selectionArea.getBounds();
            g.drawImage(floatingContent, bounds.x + moveOffX, bounds.y + moveOffY, null);
        }
        // Draw the path being drawn
        if (dragging && currentPath != null) {
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{4f, 4f}, 0f));
            g.draw(currentPath);
        }
        // Draw marching ants around completed selection (with offset)
        if (selectionArea != null && !selectionArea.isEmpty()) {
            float dashOffset = (float) (animFrame % 8);
            float[] dash = { 4f, 4f };
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(moveOffX, moveOffY);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, dashOffset));
            g2.draw(selectionArea);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, (dashOffset + 4f) % 8f));
            g2.draw(selectionArea);
            g2.dispose();
        }
    }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Draw freehand to select"));
        return panel;
    }

    // --- Public API for Edit menu integration ---

    public boolean hasSelection() {
        return selectionArea != null && !selectionArea.isEmpty();
    }

    public Area getSelectionArea() {
        return selectionArea;
    }

    public Rectangle getSelectionBounds() {
        return selectionArea != null ? selectionArea.getBounds() : null;
    }

    public void clearSelection() {
        selectionArea = null;
        currentPath = null;
        floatingContent = null;
        moveOffX = 0;
        moveOffY = 0;
        moving = false;
    }

    /** Commits floating content back onto the image at its current offset. */
    public void commitFloating(BufferedImage image) {
        if (floatingContent == null || selectionArea == null) return;
        Rectangle bounds = selectionArea.getBounds();
        Graphics2D g = image.createGraphics();
        g.drawImage(floatingContent, bounds.x + moveOffX, bounds.y + moveOffY, null);
        g.dispose();
        floatingContent = null;
        moveOffX = 0;
        moveOffY = 0;
    }

    public BufferedImage copySelection(BufferedImage image) {
        if (!hasSelection()) return null;
        Rectangle bounds = selectionArea.getBounds();
        int bx = bounds.x, by = bounds.y, bw = bounds.width, bh = bounds.height;
        BufferedImage result = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int ix = bx + x, iy = by + y;
                if (ix >= 0 && ix < image.getWidth() && iy >= 0 && iy < image.getHeight()) {
                    if (selectionArea.contains(ix, iy)) {
                        result.setRGB(x, y, image.getRGB(ix, iy));
                    }
                }
            }
        }
        return result;
    }

    public BufferedImage cutSelection(BufferedImage image) {
        BufferedImage copied = copySelection(image);
        deleteSelection(image);
        return copied;
    }

    public void deleteSelection(BufferedImage image) {
        if (!hasSelection()) return;
        Rectangle bounds = selectionArea.getBounds();
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.setClip(selectionArea);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.dispose();
    }
}

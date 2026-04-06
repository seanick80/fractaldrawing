package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class MagicWandTool implements Tool {

    private int tolerance = 0;
    private GeneralPath selectionOutline;
    private Rectangle selectionBounds;
    private boolean[][] selectionMask;
    private int maskW, maskH;
    private long animFrame;
    private Timer antTimer;

    // Move state
    private boolean moving;
    private int startX, startY;
    private int moveOffX, moveOffY;
    private BufferedImage floatingContent;

    // Canvas reference for committing floating content on deactivation.
    private DrawingCanvas activeCanvas;

    @Override
    public String getName() { return "Magic Wand"; }

    @Override
    public boolean needsPersistentPreview() { return true; }

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        activeCanvas = canvas;
        antTimer = new Timer(100, e -> {
            animFrame++;
            canvas.repaint();
        });
        antTimer.start();
    }

    @Override
    public void onDeactivated() {
        if (antTimer != null) antTimer.stop();
        if (activeCanvas != null && floatingContent != null) {
            commitFloating(activeCanvas.getActiveLayerImage());
        }
        clearSelection();
        activeCanvas = null;
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;

        // Click inside existing selection → start moving
        if (hasSelection() && isInsideSelection(x, y)) {
            if (floatingContent == null) {
                floatingContent = copySelection(image);
                deleteSelection(image);
            }
            moving = true;
            startX = x;
            startY = y;
            return;
        }

        // Click outside → commit any floating content, then make new selection
        commitFloating(image);

        int targetColor = image.getRGB(x, y);
        int w = image.getWidth();
        int h = image.getHeight();
        selectionMask = floodFillMask(image, x, y, targetColor, w, h);
        maskW = w;
        maskH = h;
        selectionOutline = buildOutline(selectionMask, w, h);
        selectionBounds = computeBounds(selectionMask, w, h);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving) {
            int dx = x - startX;
            int dy = y - startY;
            moveOffX += dx;
            moveOffY += dy;
            startX = x;
            startY = y;
            // Shift the outline and bounds to match
            selectionOutline = buildOutlineWithOffset(selectionMask, maskW, maskH, moveOffX, moveOffY);
            if (selectionBounds != null) {
                selectionBounds = computeBoundsWithOffset(selectionMask, maskW, maskH, moveOffX, moveOffY);
            }
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving) {
            moving = false;
        }
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (floatingContent != null && selectionBounds != null) {
            g.drawImage(floatingContent, selectionBounds.x, selectionBounds.y, null);
        }
        if (selectionOutline == null) return;
        float dashOffset = (float) (animFrame % 8);
        float[] dash = { 4f, 4f };
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, dashOffset));
        g.draw(selectionOutline);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, (dashOffset + 4f) % 8f));
        g.draw(selectionOutline);
    }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Tolerance:");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSlider slider = new JSlider(0, 128, tolerance);
        slider.setFont(slider.getFont().deriveFont(10f));
        slider.setMaximumSize(new Dimension(120, 28));
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLabel = new JLabel(String.valueOf(tolerance));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        slider.addChangeListener(e -> {
            tolerance = slider.getValue();
            valueLabel.setText(String.valueOf(tolerance));
        });

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(slider);
        panel.add(valueLabel);
        return panel;
    }

    // --- Public API for Edit menu integration ---

    public boolean hasSelection() {
        return selectionMask != null && selectionBounds != null;
    }

    public Rectangle getSelectionBounds() {
        return selectionBounds;
    }

    public void clearSelection() {
        selectionOutline = null;
        selectionBounds = null;
        selectionMask = null;
        floatingContent = null;
        moveOffX = 0;
        moveOffY = 0;
        moving = false;
    }

    /** Commits floating content back onto the image at its current offset. */
    public void commitFloating(BufferedImage image) {
        if (floatingContent == null || selectionBounds == null) return;
        Graphics2D g = image.createGraphics();
        g.drawImage(floatingContent, selectionBounds.x, selectionBounds.y, null);
        g.dispose();
        floatingContent = null;
        moveOffX = 0;
        moveOffY = 0;
    }

    public BufferedImage copySelection(BufferedImage image) {
        if (!hasSelection()) return null;
        int bx = selectionBounds.x, by = selectionBounds.y;
        int bw = selectionBounds.width, bh = selectionBounds.height;
        BufferedImage result = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int mx = bx + x, my = by + y;
                if (mx >= 0 && mx < maskW && my >= 0 && my < maskH && selectionMask[mx][my]) {
                    if (mx < image.getWidth() && my < image.getHeight()) {
                        result.setRGB(x, y, image.getRGB(mx, my));
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
        if (!hasSelection() || selectionMask == null) return;
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        for (int y = 0; y < maskH && y < image.getHeight(); y++) {
            for (int x = 0; x < maskW && x < image.getWidth(); x++) {
                if (selectionMask[x][y]) {
                    g.fillRect(x, y, 1, 1);
                }
            }
        }
        g.dispose();
    }

    // --- Private helpers ---

    private boolean[][] floodFillMask(BufferedImage image, int startX, int startY, int targetColor, int w, int h) {
        boolean[][] mask = new boolean[w][h];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        while (!stack.isEmpty()) {
            int[] point = stack.pop();
            int px = point[0], py = point[1];
            if (px < 0 || px >= w || py < 0 || py >= h) continue;
            if (mask[px][py]) continue;
            if (!colorMatch(image.getRGB(px, py), targetColor)) continue;
            mask[px][py] = true;
            stack.push(new int[]{px + 1, py});
            stack.push(new int[]{px - 1, py});
            stack.push(new int[]{px, py + 1});
            stack.push(new int[]{px, py - 1});
        }
        return mask;
    }

    private boolean colorMatch(int c1, int c2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return Math.abs(r1 - r2) <= tolerance && Math.abs(g1 - g2) <= tolerance && Math.abs(b1 - b2) <= tolerance;
    }

    /**
     * Traces the boundary of the mask as merged horizontal and vertical runs
     * so that the dash pattern flows smoothly (marching ants effect).
     */
    private GeneralPath buildOutline(boolean[][] mask, int w, int h) {
        return buildOutlineWithOffset(mask, w, h, 0, 0);
    }

    private boolean isInsideSelection(int x, int y) {
        if (selectionMask == null) return false;
        int mx = x - moveOffX, my = y - moveOffY;
        return mx >= 0 && mx < maskW && my >= 0 && my < maskH && selectionMask[mx][my];
    }

    private GeneralPath buildOutlineWithOffset(boolean[][] mask, int w, int h, int ox, int oy) {
        GeneralPath path = new GeneralPath();
        // Merge horizontal boundary edges into runs
        for (int y = 0; y <= h; y++) {
            int runStart = -1;
            for (int x = 0; x < w; x++) {
                boolean above = (y > 0 && mask[x][y - 1]);
                boolean below = (y < h && mask[x][y]);
                boolean isEdge = above != below;
                if (isEdge && runStart < 0) {
                    runStart = x;
                } else if (!isEdge && runStart >= 0) {
                    path.moveTo(runStart + ox, y + oy);
                    path.lineTo(x + ox, y + oy);
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                path.moveTo(runStart + ox, y + oy);
                path.lineTo(w + ox, y + oy);
            }
        }
        // Merge vertical boundary edges into runs
        for (int x = 0; x <= w; x++) {
            int runStart = -1;
            for (int y = 0; y < h; y++) {
                boolean left = (x > 0 && mask[x - 1][y]);
                boolean right = (x < w && mask[x][y]);
                boolean isEdge = left != right;
                if (isEdge && runStart < 0) {
                    runStart = y;
                } else if (!isEdge && runStart >= 0) {
                    path.moveTo(x + ox, runStart + oy);
                    path.lineTo(x + ox, y + oy);
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                path.moveTo(x + ox, runStart + oy);
                path.lineTo(x + ox, h + oy);
            }
        }
        return path;
    }

    private Rectangle computeBoundsWithOffset(boolean[][] mask, int w, int h, int ox, int oy) {
        Rectangle r = computeBounds(mask, w, h);
        if (r != null) r.translate(ox, oy);
        return r;
    }

    private Rectangle computeBounds(boolean[][] mask, int w, int h) {
        int minX = w, minY = h, maxX = 0, maxY = 0;
        boolean found = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[x][y]) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    found = true;
                }
            }
        }
        if (!found) return null;
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fills.CustomGradientFill;
import com.seanick80.drawingapp.fills.FillProvider;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class FillTool implements Tool {

    private static final int TOLERANCE = 32;
    private FillProvider fillProvider;

    // Drag state for gradient line
    private boolean dragActive;
    private int startX, startY, endX, endY;

    @Override public String getName() { return "Fill"; }
    @Override public boolean hasFill() { return true; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        return ToolSettingsBuilder.createFillOptionsPanel(
                ctx.getFillRegistry(), ctx.getGradientToolbar(), false,
                null, this::setFillProvider);
    }

    @Override
    public void setFillProvider(FillProvider fp) { this.fillProvider = fp; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;

        if (fillProvider instanceof CustomGradientFill) {
            // Start drag to define gradient line
            startX = endX = x;
            startY = endY = y;
            dragActive = true;
        } else {
            // Immediate fill for non-gradient providers
            applyFill(image, x, y, canvas);
        }
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragActive) {
            endX = x;
            endY = y;
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragActive) {
            dragActive = false;
            endX = x;
            endY = y;

            // Compute angle from drag vector
            if (fillProvider instanceof CustomGradientFill cgf) {
                int dx = endX - startX;
                int dy = endY - startY;
                if (dx != 0 || dy != 0) {
                    int angle = (int) Math.round(Math.toDegrees(Math.atan2(dy, dx)));
                    if (angle < 0) angle += 360;
                    cgf.setAngleDegrees(angle);
                }
            }

            applyFill(image, startX, startY, canvas);
        }
    }

    @Override
    public void drawPreview(Graphics2D g) {
        if (!dragActive) return;

        // Draw the gradient direction line
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10, new float[]{6, 4}, 0));
        g.drawLine(startX, startY, endX, endY);

        // Draw start/end markers
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        g.fillOval(startX - 4, startY - 4, 8, 8);
        g.setColor(Color.BLACK);
        g.drawOval(startX - 4, startY - 4, 8, 8);
        g.fillOval(endX - 3, endY - 3, 6, 6);
    }

    private void applyFill(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;
        int targetColor = image.getRGB(x, y);
        Color fgColor = canvas.getForegroundColor();
        if (targetColor == fgColor.getRGB() && fillProvider == null) return;

        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] mask = floodFillMask(image, x, y, targetColor, w, h);
        expandMaskForAliasing(image, mask, targetColor, w, h);

        // Find bounding box of the filled region
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                if (mask[px][py]) {
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);
                }
            }
        }
        if (maxX < minX) return;

        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;

        // Render the fill pattern into a temp image
        BufferedImage temp = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = temp.createGraphics();
        if (fillProvider != null) {
            g.setPaint(fillProvider.createPaint(fgColor, 0, 0, bw, bh));
        } else {
            g.setColor(fgColor);
        }
        g.fillRect(0, 0, bw, bh);
        g.dispose();

        // Copy only the masked pixels from temp to the main image
        for (int px = minX; px <= maxX; px++) {
            for (int py = minY; py <= maxY; py++) {
                if (mask[px][py]) {
                    image.setRGB(px, py, temp.getRGB(px - minX, py - minY));
                }
            }
        }
    }

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

    private void expandMaskForAliasing(BufferedImage image, boolean[][] mask, int targetColor, int w, int h) {
        int strokeColor = findStrokeColor(image, mask, targetColor, w, h);
        if (strokeColor == -1) return;

        boolean[][] border = new boolean[w][h];
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                if (mask[px][py]) continue;
                boolean adjacentToMask = (px > 0 && mask[px - 1][py]) || (px < w - 1 && mask[px + 1][py])
                    || (py > 0 && mask[px][py - 1]) || (py < h - 1 && mask[px][py + 1]);
                if (!adjacentToMask) continue;

                int c = image.getRGB(px, py);
                if (isBlendBetween(c, targetColor, strokeColor)) {
                    border[px][py] = true;
                }
            }
        }
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                if (border[px][py]) mask[px][py] = true;
            }
        }
    }

    private int findStrokeColor(BufferedImage image, boolean[][] mask, int targetColor, int w, int h) {
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                if (!mask[px][py]) continue;
                int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : dirs) {
                    int nx = px + d[0], ny = py + d[1];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h || mask[nx][ny]) continue;
                    int fx = nx + d[0], fy = ny + d[1];
                    int c;
                    if (fx >= 0 && fx < w && fy >= 0 && fy < h && !mask[fx][fy]) {
                        c = image.getRGB(fx, fy);
                    } else {
                        c = image.getRGB(nx, ny);
                    }
                    if (!colorMatch(c, targetColor)) return c;
                }
            }
        }
        return -1;
    }

    private boolean isBlendBetween(int c, int c1, int c2) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int margin = 5;
        return isBetween(r, r1, r2, margin)
            && isBetween(g, g1, g2, margin)
            && isBetween(b, b1, b2, margin);
    }

    private boolean isBetween(int v, int a, int b, int margin) {
        int lo = Math.min(a, b) - margin;
        int hi = Math.max(a, b) + margin;
        return v >= lo && v <= hi;
    }

    private boolean colorMatch(int c1, int c2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return Math.abs(r1 - r2) <= TOLERANCE
            && Math.abs(g1 - g2) <= TOLERANCE
            && Math.abs(b1 - b2) <= TOLERANCE;
    }
}

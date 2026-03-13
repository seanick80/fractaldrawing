package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class FillTool implements Tool {

    private static final int TOLERANCE = 32;

    @Override public String getName() { return "Fill"; }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) return;
        int targetColor = image.getRGB(x, y);
        int fillColor = canvas.getForegroundColor().getRGB();
        if (targetColor == fillColor) return;
        floodFill(image, x, y, targetColor, fillColor);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}

    private void floodFill(BufferedImage image, int startX, int startY, int targetColor, int fillColor) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] visited = new boolean[w][h];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});

        while (!stack.isEmpty()) {
            int[] point = stack.pop();
            int px = point[0], py = point[1];
            if (px < 0 || px >= w || py < 0 || py >= h) continue;
            if (visited[px][py]) continue;
            if (!colorMatch(image.getRGB(px, py), targetColor)) continue;

            visited[px][py] = true;
            image.setRGB(px, py, fillColor);

            stack.push(new int[]{px + 1, py});
            stack.push(new int[]{px - 1, py});
            stack.push(new int[]{px, py + 1});
            stack.push(new int[]{px, py - 1});
        }
    }

    private boolean colorMatch(int c1, int c2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return Math.abs(r1 - r2) <= TOLERANCE
            && Math.abs(g1 - g2) <= TOLERANCE
            && Math.abs(b1 - b2) <= TOLERANCE;
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class SelectionTool implements Tool {

    // Current selection bounds; (0,0,0,0) means no selection.
    private int selX, selY, selW, selH;

    // Drag-start coordinates used for both selecting and moving.
    private int startX, startY;

    // True while the user is dragging to draw a new selection rectangle.
    private boolean active;

    // True while the user is dragging the floating content.
    private boolean moving;

    // Accumulated offset applied to the floating content during a move.
    private int moveOffX, moveOffY;

    // Pixels cut from the canvas and currently being dragged.
    private BufferedImage floatingContent;

    // Incremented each timer tick to animate the dashes.
    private long animFrame;

    // Fires every 100 ms to advance the marching-ants animation.
    private Timer antTimer;

    // -------------------------------------------------------------------------
    // Tool identity
    // -------------------------------------------------------------------------

    @Override
    public String getName() { return "Select"; }

    @Override
    public boolean needsPersistentPreview() { return true; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

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
        if (antTimer != null) {
            antTimer.stop();
        }
        // Floating content cannot be committed here because we have no image
        // reference. It will be committed on the next mousePressed or via
        // commitSelection().
    }

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (hasSelection() && isInsideSelection(x, y)) {
            // Start moving the selected region.
            if (floatingContent == null) {
                floatingContent = copyRegion(image, selX, selY, selW, selH);
                clearRegion(image, selX, selY, selW, selH);
            }
            moving = true;
            startX = x;
            startY = y;
        } else {
            // Start a new selection. Commit any floating content first.
            commitSelection(image);
            clearSelection();
            startX = x;
            startY = y;
            active = true;
        }
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (active) {
            int x1 = Math.min(startX, x);
            int y1 = Math.min(startY, y);
            int x2 = Math.max(startX, x);
            int y2 = Math.max(startY, y);
            selX = x1;
            selY = y1;
            selW = x2 - x1;
            selH = y2 - y1;
        } else if (moving) {
            moveOffX += x - startX;
            moveOffY += y - startY;
            startX = x;
            startY = y;
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (active) {
            active = false;
        } else if (moving) {
            moving = false;
            selX += moveOffX;
            selY += moveOffY;
            moveOffX = 0;
            moveOffY = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Preview (marching ants)
    // -------------------------------------------------------------------------

    @Override
    public void drawPreview(Graphics2D g) {
        if (selW <= 0 || selH <= 0) return;

        int drawX = selX + moveOffX;
        int drawY = selY + moveOffY;

        // Draw floating content while it is being moved.
        if (floatingContent != null) {
            g.drawImage(floatingContent, drawX, drawY, null);
        }

        // Marching-ants stroke: 4-px on, 4-px off, animated offset.
        float dashOffset = (float) (animFrame % 8);
        float[] dash = { 4f, 4f };

        // White stroke first, then black offset by 4 pixels of dash phase for
        // contrast on any background colour.
        BasicStroke whiteStroke = new BasicStroke(
                1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, dash, dashOffset);
        BasicStroke blackStroke = new BasicStroke(
                1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, dash, (dashOffset + 4f) % 8f);

        g.setColor(Color.WHITE);
        g.setStroke(whiteStroke);
        g.drawRect(drawX, drawY, selW, selH);

        g.setColor(Color.BLACK);
        g.setStroke(blackStroke);
        g.drawRect(drawX, drawY, selW, selH);
    }

    // -------------------------------------------------------------------------
    // Settings panel
    // -------------------------------------------------------------------------

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Drag to select, click inside to move"));
        return panel;
    }

    // -------------------------------------------------------------------------
    // Public API for Edit-menu integration
    // -------------------------------------------------------------------------

    /** Returns true when a non-empty selection rectangle exists. */
    public boolean hasSelection() {
        return selW > 0 && selH > 0;
    }

    /**
     * Returns a copy of the selected region from the image without modifying it.
     */
    public BufferedImage copySelection(BufferedImage image) {
        return copyRegion(image, selX, selY, selW, selH);
    }

    /**
     * Copies the selected region into floatingContent and clears that area on
     * the image.
     */
    public void cutSelection(BufferedImage image) {
        if (!hasSelection()) return;
        floatingContent = copyRegion(image, selX, selY, selW, selH);
        clearRegion(image, selX, selY, selW, selH);
    }

    /**
     * Clears the selected region on the image and discards any floating content.
     */
    public void deleteSelection(BufferedImage image) {
        if (!hasSelection()) return;
        clearRegion(image, selX, selY, selW, selH);
        floatingContent = null;
    }

    /**
     * Pastes content as new floating content with the selection anchored at the
     * top-left corner of the canvas.
     */
    public void pasteContent(BufferedImage content) {
        floatingContent = content;
        selX = 0;
        selY = 0;
        selW = content.getWidth();
        selH = content.getHeight();
        moveOffX = 0;
        moveOffY = 0;
    }

    /** Sets the selection to cover the entire image. */
    public void selectAll(BufferedImage image) {
        selX = 0;
        selY = 0;
        selW = image.getWidth();
        selH = image.getHeight();
        moveOffX = 0;
        moveOffY = 0;
        floatingContent = null;
    }

    /**
     * Draws the floating content back onto the image at its current offset
     * position and resets the move state.
     */
    public void commitSelection(BufferedImage image) {
        if (floatingContent == null) return;
        Graphics2D g = image.createGraphics();
        g.drawImage(floatingContent, selX + moveOffX, selY + moveOffY, null);
        g.dispose();
        floatingContent = null;
        moveOffX = 0;
        moveOffY = 0;
    }

    /** Resets all selection state without modifying the image. */
    public void clearSelection() {
        selX = 0;
        selY = 0;
        selW = 0;
        selH = 0;
        startX = 0;
        startY = 0;
        active = false;
        moving = false;
        moveOffX = 0;
        moveOffY = 0;
        floatingContent = null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isInsideSelection(int x, int y) {
        return x >= selX && x <= selX + selW && y >= selY && y <= selY + selH;
    }

    /**
     * Returns a new BufferedImage containing the pixels from the given region
     * of {@code src}. Coordinates are clamped to the image bounds.
     */
    private BufferedImage copyRegion(BufferedImage src, int x, int y, int w, int h) {
        int cx = Math.max(0, x);
        int cy = Math.max(0, y);
        int cw = Math.min(w, src.getWidth() - cx);
        int ch = Math.min(h, src.getHeight() - cy);
        if (cw <= 0 || ch <= 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage copy = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, cw, ch, cx, cy, cx + cw, cy + ch, null);
        g.dispose();
        return copy;
    }

    /** Fills the given region with transparent pixels using AlphaComposite.Clear. */
    private void clearRegion(BufferedImage image, int x, int y, int w, int h) {
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.dispose();
    }
}

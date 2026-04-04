package com.seanick80.drawingapp.gradient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Visual gradient editor with a preview bar and per-channel (R/G/B) curve editors.
 *
 * Interactions:
 * - Click a control point to select it
 * - Drag a control point to move it (horizontal = position, vertical = channel value)
 * - Shift+click on empty area to add a new stop
 * - Right-click a control point to delete it
 * - Double-click a control point to open a color chooser
 */
public class GradientEditorPanel extends JPanel {

    private static final int PREVIEW_HEIGHT = 40;
    private static final int CHANNEL_GAP = 2;
    private static final int POINT_RADIUS = 5;
    private static final int HIT_RADIUS = 8;

    private ColorGradient gradient;
    private ColorGradient.Stop selectedStop;
    private int dragChannel = -1; // 0=R, 1=G, 2=B, -1=none
    private boolean dragging;
    private final Color[] channelColors = { new Color(220, 60, 60), new Color(60, 180, 60), new Color(60, 80, 220) };
    private Runnable editCallback;

    public GradientEditorPanel(ColorGradient gradient) {
        this.gradient = gradient;
        setPreferredSize(new Dimension(500, 320));
        setBackground(Color.DARK_GRAY);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePress(e); }
            @Override
            public void mouseDragged(MouseEvent e) { handleDrag(e); }
            @Override
            public void mouseReleased(MouseEvent e) { dragging = false; repaint(); }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectedStop != null) {
                    Color c = JColorChooser.showDialog(GradientEditorPanel.this, "Stop Color", selectedStop.getColor());
                    if (c != null) {
                        selectedStop.setColor(c);
                        repaint();
                        fireEditCallback();
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public ColorGradient getGradient() { return gradient; }

    /** Set a callback that fires on every edit (drag, add, delete, color change). */
    public void setEditCallback(Runnable callback) { this.editCallback = callback; }

    private void fireEditCallback() {
        if (editCallback != null) editCallback.run();
    }

    /** Switch which gradient this panel is editing. */
    public void setGradient(ColorGradient gradient) {
        this.gradient = gradient;
        this.selectedStop = null;
        repaint();
    }

    public ColorGradient.Stop getSelectedStop() { return selectedStop; }

    /** Set the selected stop externally (e.g. from a color chooser button). */
    public void setSelectedStop(ColorGradient.Stop stop) {
        this.selectedStop = stop;
        repaint();
    }

    // --- Layout helpers ---

    private int getPreviewTop() { return 0; }
    private int getPreviewBottom() { return PREVIEW_HEIGHT; }

    private int getChannelTop(int ch) {
        int areaTop = getPreviewBottom() + CHANNEL_GAP;
        int areaHeight = getHeight() - areaTop;
        int chHeight = (areaHeight - CHANNEL_GAP * 2) / 3;
        return areaTop + ch * (chHeight + CHANNEL_GAP);
    }

    private int getChannelBottom(int ch) {
        int areaTop = getPreviewBottom() + CHANNEL_GAP;
        int areaHeight = getHeight() - areaTop;
        int chHeight = (areaHeight - CHANNEL_GAP * 2) / 3;
        return areaTop + ch * (chHeight + CHANNEL_GAP) + chHeight;
    }

    private int getChannelHeight(int ch) {
        return getChannelBottom(ch) - getChannelTop(ch);
    }

    private float xToPosition(int x) {
        return (float) x / Math.max(1, getWidth() - 1);
    }

    private int positionToX(float pos) {
        return Math.round(pos * (getWidth() - 1));
    }

    private int channelValueToY(int ch, int value) {
        // 255 at top, 0 at bottom
        int top = getChannelTop(ch);
        int height = getChannelHeight(ch);
        return top + (int) ((255 - value) * (height - 1) / 255.0);
    }

    private int yToChannelValue(int ch, int y) {
        int top = getChannelTop(ch);
        int height = getChannelHeight(ch);
        int value = 255 - (int) ((y - top) * 255.0 / (height - 1));
        return Math.max(0, Math.min(255, value));
    }

    private int getChannel(int ch, Color c) {
        return switch (ch) { case 0 -> c.getRed(); case 1 -> c.getGreen(); default -> c.getBlue(); };
    }

    private Color setChannel(Color c, int ch, int value) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        value = Math.max(0, Math.min(255, value));
        return switch (ch) {
            case 0 -> new Color(value, g, b);
            case 1 -> new Color(r, value, b);
            default -> new Color(r, g, value);
        };
    }

    // --- Hit testing ---

    /** Find the nearest stop to a given x coordinate (ignoring y), within HIT_RADIUS. */
    private ColorGradient.Stop hitStopByX(int x) {
        List<ColorGradient.Stop> stops = gradient.getStops();
        ColorGradient.Stop closest = null;
        double bestDist = HIT_RADIUS;
        for (ColorGradient.Stop s : stops) {
            int sx = positionToX(s.getPosition());
            double dist = Math.abs(x - sx);
            if (dist < bestDist) {
                bestDist = dist;
                closest = s;
            }
        }
        return closest;
    }

    private int hitChannel(int y) {
        for (int ch = 0; ch < 3; ch++) {
            if (y >= getChannelTop(ch) && y <= getChannelBottom(ch)) return ch;
        }
        return -1;
    }

    private ColorGradient.Stop hitStop(int x, int y, int ch) {
        List<ColorGradient.Stop> stops = gradient.getStops();
        ColorGradient.Stop closest = null;
        double bestDist = HIT_RADIUS;
        for (ColorGradient.Stop s : stops) {
            int sx = positionToX(s.getPosition());
            int sy = channelValueToY(ch, getChannel(ch, s.getColor()));
            double dist = Math.sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy));
            if (dist < bestDist) {
                bestDist = dist;
                closest = s;
            }
        }
        return closest;
    }

    // --- Mouse handling ---

    private void handlePress(MouseEvent e) {
        // Handle clicks in the preview bar area
        if (e.getY() >= getPreviewTop() && e.getY() <= getPreviewBottom()) {
            if (SwingUtilities.isRightMouseButton(e)) {
                ColorGradient.Stop hit = hitStopByX(e.getX());
                if (hit != null) {
                    gradient.removeStop(hit);
                    if (selectedStop == hit) selectedStop = null;
                    repaint();
                    fireEditCallback();
                }
                return;
            }
            ColorGradient.Stop hit = hitStopByX(e.getX());
            if (hit != null) {
                selectedStop = hit;
            } else if (e.isShiftDown()) {
                float pos = xToPosition(e.getX());
                Color interpolated = gradient.getColorAt(pos);
                selectedStop = gradient.addStop(pos, interpolated);
                fireEditCallback();
            } else {
                selectedStop = null;
            }
            repaint();
            return;
        }

        int ch = hitChannel(e.getY());
        if (ch < 0) return;

        if (SwingUtilities.isRightMouseButton(e)) {
            ColorGradient.Stop hit = hitStop(e.getX(), e.getY(), ch);
            if (hit != null) {
                gradient.removeStop(hit);
                if (selectedStop == hit) selectedStop = null;
                repaint();
                fireEditCallback();
            }
            return;
        }

        ColorGradient.Stop hit = hitStop(e.getX(), e.getY(), ch);
        if (hit != null) {
            selectedStop = hit;
            dragChannel = ch;
            dragging = true;
        } else if (e.isShiftDown()) {
            // Add new stop
            float pos = xToPosition(e.getX());
            Color interpolated = gradient.getColorAt(pos);
            // Set the clicked channel to match the Y position
            int val = yToChannelValue(ch, e.getY());
            interpolated = setChannel(interpolated, ch, val);
            selectedStop = gradient.addStop(pos, interpolated);
            dragChannel = ch;
            dragging = true;
            fireEditCallback();
        } else {
            selectedStop = null;
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!dragging || selectedStop == null || dragChannel < 0) return;

        float pos = xToPosition(e.getX());
        selectedStop.setPosition(pos);

        int val = yToChannelValue(dragChannel, e.getY());
        selectedStop.setColor(setChannel(selectedStop.getColor(), dragChannel, val));

        repaint();
        fireEditCallback();
    }

    // --- Painting ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGradientPreview(g2);
        for (int ch = 0; ch < 3; ch++) {
            drawChannelEditor(g2, ch);
        }
    }

    private void drawGradientPreview(Graphics2D g) {
        int w = getWidth();
        for (int x = 0; x < w; x++) {
            float t = (float) x / Math.max(1, w - 1);
            g.setColor(gradient.getColorAt(t));
            g.drawLine(x, getPreviewTop(), x, getPreviewBottom() - 1);
        }
        // Draw stop markers below the preview
        g.setStroke(new BasicStroke(1));
        List<ColorGradient.Stop> stops = gradient.getStops();
        for (ColorGradient.Stop s : stops) {
            int sx = positionToX(s.getPosition());
            int by = getPreviewBottom() - 1;
            int[] xpts = { sx - 4, sx + 4, sx };
            int[] ypts = { by - 8, by - 8, by };
            g.setColor(s == selectedStop ? Color.YELLOW : Color.WHITE);
            g.fillPolygon(xpts, ypts, 3);
            g.setColor(Color.BLACK);
            g.drawPolygon(xpts, ypts, 3);
        }
    }

    private void drawChannelEditor(Graphics2D g, int ch) {
        int top = getChannelTop(ch);
        int height = getChannelHeight(ch);

        // Background
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, top, getWidth(), height);

        // Grid lines at 0, 64, 128, 192, 255
        g.setColor(new Color(50, 50, 50));
        for (int v = 0; v <= 255; v += 64) {
            int y = channelValueToY(ch, v);
            g.drawLine(0, y, getWidth(), y);
        }

        // Draw interpolated curve
        g.setColor(channelColors[ch]);
        g.setStroke(new BasicStroke(1.5f));
        int prevX = 0;
        int prevY = channelValueToY(ch, getChannel(ch, gradient.getColorAt(0)));
        int w = getWidth();
        for (int px = 1; px < w; px++) {
            float t = (float) px / (w - 1);
            int y = channelValueToY(ch, getChannel(ch, gradient.getColorAt(t)));
            g.drawLine(prevX, prevY, px, y);
            prevX = px;
            prevY = y;
        }

        // Draw control points
        List<ColorGradient.Stop> stops = gradient.getStops();
        for (ColorGradient.Stop s : stops) {
            int sx = positionToX(s.getPosition());
            int sy = channelValueToY(ch, getChannel(ch, s.getColor()));

            if (s == selectedStop) {
                g.setColor(Color.YELLOW);
                g.fillOval(sx - POINT_RADIUS, sy - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(1));
                g.drawOval(sx - POINT_RADIUS, sy - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
                g.setStroke(new BasicStroke(1.5f));
            } else {
                g.setColor(Color.WHITE);
                g.fillOval(sx - POINT_RADIUS + 1, sy - POINT_RADIUS + 1, POINT_RADIUS * 2 - 2, POINT_RADIUS * 2 - 2);
                g.setColor(channelColors[ch]);
                g.setStroke(new BasicStroke(1));
                g.drawOval(sx - POINT_RADIUS, sy - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
                g.setStroke(new BasicStroke(1.5f));
            }
        }

        // Channel label
        g.setColor(channelColors[ch].brighter());
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        String label = switch (ch) { case 0 -> "R"; case 1 -> "G"; default -> "B"; };
        g.drawString(label, 4, top + 14);
    }
}

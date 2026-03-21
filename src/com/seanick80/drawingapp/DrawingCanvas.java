package com.seanick80.drawingapp;

import com.seanick80.drawingapp.tools.Tool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class DrawingCanvas extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    private BufferedImage image;
    private Tool activeTool;
    private StatusBar statusBar;
    private ColorPicker colorPicker;
    private final UndoManager undoManager;
    private boolean drawing;
    private int lastMouseButton;
    private int panOffsetX, panOffsetY;

    // View zoom (image inspection, no re-render)
    private double viewZoom = 1.0;
    private double viewPanX, viewPanY; // offset in image coords

    public DrawingCanvas(int width, int height, UndoManager undoManager) {
        this.undoManager = undoManager;
        setPreferredSize(new Dimension(width, height));
        newImage(width, height);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void newImage(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        clearCanvas();
        setPreferredSize(new Dimension(width, height));
        revalidate();
        undoManager.clear();
    }

    public void loadImage(BufferedImage img) {
        image = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
        revalidate();
        repaint();
        undoManager.clear();
    }

    public void clearCanvas() {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    public void setActiveTool(Tool tool) {
        this.activeTool = tool;
    }

    public void setStatusBar(StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    public void setColorPicker(ColorPicker colorPicker) {
        this.colorPicker = colorPicker;
    }

    public ColorPicker getColorPicker() {
        return colorPicker;
    }

    public Color getForegroundColor() {
        return colorPicker != null ? colorPicker.getForegroundColor() : Color.BLACK;
    }

    public Color getBackgroundColor() {
        return colorPicker != null ? colorPicker.getBackgroundColor() : Color.WHITE;
    }

    public void saveUndoState() {
        undoManager.saveState(image);
    }

    public void setPanOffset(int dx, int dy) {
        panOffsetX = dx;
        panOffsetY = dy;
    }

    public double getViewZoom() { return viewZoom; }

    public void resetViewZoom() {
        viewZoom = 1.0;
        viewPanX = 0;
        viewPanY = 0;
        repaint();
    }

    /**
     * Zoom the view centered on the given screen coordinates.
     * Adjusts viewPanX/Y so the image point under the cursor stays fixed.
     */
    private void applyViewZoom(int screenX, int screenY, double factor) {
        // Image point under cursor before zoom
        double imgX = (screenX - viewPanX) / viewZoom;
        double imgY = (screenY - viewPanY) / viewZoom;

        viewZoom *= factor;
        viewZoom = Math.max(0.25, Math.min(viewZoom, 32.0));

        // Adjust pan so the same image point stays under the cursor
        viewPanX = screenX - imgX * viewZoom;
        viewPanY = screenY - imgY * viewZoom;

        repaint();
    }

    /** Convert screen X to image X accounting for view zoom and pan. */
    private int toImageX(int screenX) {
        return (int) ((screenX - viewPanX) / viewZoom);
    }

    /** Convert screen Y to image Y accounting for view zoom and pan. */
    private int toImageY(int screenY) {
        return (int) ((screenY - viewPanY) / viewZoom);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (panOffsetX != 0 || panOffsetY != 0) {
            // During fractal pan: fill background black, draw image shifted
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.drawImage(image, panOffsetX, panOffsetY, null);
        } else if (viewZoom != 1.0 || viewPanX != 0 || viewPanY != 0) {
            // View zoom: apply transform
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.translate(viewPanX, viewPanY);
            g2.scale(viewZoom, viewZoom);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    viewZoom > 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                   : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, 0, 0, null);
        } else {
            g2.drawImage(image, 0, 0, null);
        }
        if (activeTool != null && drawing) {
            activeTool.drawPreview(g2);
        }
    }

    public int getLastMouseButton() {
        return lastMouseButton;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int imgX = toImageX(e.getX());
        int imgY = toImageY(e.getY());
        if (activeTool != null && imgX >= 0 && imgX < image.getWidth()
                && imgY >= 0 && imgY < image.getHeight()) {
            lastMouseButton = e.getButton();
            saveUndoState();
            drawing = true;
            activeTool.mousePressed(image, imgX, imgY, this);
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (activeTool != null && drawing) {
            drawing = false;
            activeTool.mouseReleased(image, toImageX(e.getX()), toImageY(e.getY()), this);
            repaint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (activeTool != null && drawing) {
            activeTool.mouseDragged(image, toImageX(e.getX()), toImageY(e.getY()), this);
            repaint();
        }
        updateStatus(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateStatus(e);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isControlDown()) {
            // Ctrl+scroll: fractal zoom (delegate to tool with image-space coords)
            int imgX = toImageX(e.getX());
            int imgY = toImageY(e.getY());
            if (activeTool != null && imgX >= 0 && imgX < image.getWidth()
                    && imgY >= 0 && imgY < image.getHeight()) {
                saveUndoState();
                activeTool.mouseWheelMoved(image, imgX, imgY, e.getWheelRotation(), this);
                repaint();
            }
        } else {
            // Plain scroll: image view zoom (canvas handles, no re-render)
            double factor = (e.getWheelRotation() < 0) ? 1.25 : 0.8;
            applyViewZoom(e.getX(), e.getY(), factor);
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {
        if (statusBar != null) statusBar.clearCoordinates();
    }

    private boolean inBounds(MouseEvent e) {
        return e.getX() >= 0 && e.getX() < image.getWidth()
            && e.getY() >= 0 && e.getY() < image.getHeight();
    }

    private void updateStatus(MouseEvent e) {
        if (statusBar != null) {
            statusBar.setCoordinates(toImageX(e.getX()), toImageY(e.getY()));
            statusBar.setCanvasSize(image.getWidth(), image.getHeight());
        }
    }
}

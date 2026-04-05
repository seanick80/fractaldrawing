package com.seanick80.drawingapp;

import com.seanick80.drawingapp.layers.LayerManager;
import com.seanick80.drawingapp.tools.Tool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class DrawingCanvas extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    private BufferedImage image; // composite image for display and save
    private final LayerManager layerManager;
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
        this.layerManager = new LayerManager(width, height);
        setPreferredSize(new Dimension(width, height));
        compositeImage();
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    /** Composite all layers into the display image. */
    private void compositeImage() {
        image = layerManager.composite();
    }

    public LayerManager getLayerManager() {
        return layerManager;
    }

    public void newImage(int width, int height) {
        layerManager.reset(width, height);
        compositeImage();
        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();
        undoManager.clear();
    }

    public void loadImage(BufferedImage img) {
        layerManager.reset(img.getWidth(), img.getHeight());
        // Draw loaded image onto the background layer
        Graphics2D g = layerManager.getLayer(0).getImage().createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        compositeImage();
        setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
        revalidate();
        repaint();
        undoManager.clear();
    }

    public void clearCanvas() {
        layerManager.getActiveLayer().fill(Color.WHITE);
        compositeImage();
        repaint();
    }

    /** Returns the composite (flattened) image for saving and display. */
    public BufferedImage getImage() {
        return image;
    }

    /** Returns the active layer's image for tools to draw on. */
    public BufferedImage getActiveLayerImage() {
        return layerManager.getActiveLayer().getImage();
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
        undoManager.saveState(layerManager);
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

    private void applyViewZoom(int screenX, int screenY, double factor) {
        double imgX = (screenX - viewPanX) / viewZoom;
        double imgY = (screenY - viewPanY) / viewZoom;
        viewZoom *= factor;
        viewZoom = Math.max(0.25, Math.min(viewZoom, 32.0));
        viewPanX = screenX - imgX * viewZoom;
        viewPanY = screenY - imgY * viewZoom;
        repaint();
    }

    private int toImageX(int screenX) {
        return (int) ((screenX - viewPanX) / viewZoom);
    }

    private int toImageY(int screenY) {
        return (int) ((screenY - viewPanY) / viewZoom);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Recomposite before painting
        compositeImage();

        if (panOffsetX != 0 || panOffsetY != 0) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.drawImage(image, panOffsetX, panOffsetY, null);
        } else if (viewZoom != 1.0 || viewPanX != 0 || viewPanY != 0) {
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
        BufferedImage layerImg = getActiveLayerImage();
        if (activeTool != null && imgX >= 0 && imgX < layerImg.getWidth()
                && imgY >= 0 && imgY < layerImg.getHeight()) {
            if (layerManager.getActiveLayer().isLocked()) return;
            if (!layerManager.getActiveLayer().isVisible()) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "The active layer is hidden. Make it visible to draw on it.",
                        "Hidden Layer", javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            lastMouseButton = e.getButton();
            saveUndoState();
            drawing = true;
            activeTool.mousePressed(layerImg, imgX, imgY, this);
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (activeTool != null && drawing) {
            drawing = false;
            activeTool.mouseReleased(getActiveLayerImage(), toImageX(e.getX()), toImageY(e.getY()), this);
            repaint();
            layerManager.fireChange();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (activeTool != null && drawing) {
            activeTool.mouseDragged(getActiveLayerImage(), toImageX(e.getX()), toImageY(e.getY()), this);
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
            int imgX = toImageX(e.getX());
            int imgY = toImageY(e.getY());
            BufferedImage layerImg = getActiveLayerImage();
            if (activeTool != null && imgX >= 0 && imgX < layerImg.getWidth()
                    && imgY >= 0 && imgY < layerImg.getHeight()) {
                saveUndoState();
                activeTool.mouseWheelMoved(layerImg, imgX, imgY, e.getWheelRotation(), this);
                repaint();
            }
        } else {
            double factor = (e.getWheelRotation() < 0) ? 1.25 : 0.8;
            applyViewZoom(e.getX(), e.getY(), factor);
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {
        if (statusBar != null) statusBar.clearCoordinates();
    }

    private void updateStatus(MouseEvent e) {
        if (statusBar != null) {
            statusBar.setCoordinates(toImageX(e.getX()), toImageY(e.getY()));
            BufferedImage layerImg = getActiveLayerImage();
            statusBar.setCanvasSize(layerImg.getWidth(), layerImg.getHeight());
        }
    }
}

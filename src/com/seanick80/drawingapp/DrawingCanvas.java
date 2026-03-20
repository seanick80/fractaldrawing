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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (panOffsetX != 0 || panOffsetY != 0) {
            // During pan: fill background black, draw image shifted
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.drawImage(image, panOffsetX, panOffsetY, null);
        } else {
            g.drawImage(image, 0, 0, null);
        }
        if (activeTool != null && drawing) {
            activeTool.drawPreview((Graphics2D) g);
        }
    }

    public int getLastMouseButton() {
        return lastMouseButton;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (activeTool != null && inBounds(e)) {
            lastMouseButton = e.getButton();
            saveUndoState();
            drawing = true;
            activeTool.mousePressed(image, e.getX(), e.getY(), this);
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (activeTool != null && drawing) {
            drawing = false;
            activeTool.mouseReleased(image, e.getX(), e.getY(), this);
            repaint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (activeTool != null && drawing) {
            activeTool.mouseDragged(image, e.getX(), e.getY(), this);
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
        if (activeTool != null && inBounds(e)) {
            saveUndoState();
            activeTool.mouseWheelMoved(image, e.getX(), e.getY(), e.getWheelRotation(), this);
            repaint();
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
            statusBar.setCoordinates(e.getX(), e.getY());
            statusBar.setCanvasSize(image.getWidth(), image.getHeight());
        }
    }
}

package com.seanick80.drawingapp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class UndoManager {

    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private final Deque<BufferedImage> redoStack = new ArrayDeque<>();
    private final int maxStates;

    public UndoManager(int maxStates) {
        this.maxStates = maxStates;
    }

    public void saveState(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        undoStack.push(copy);
        if (undoStack.size() > maxStates) {
            // Remove oldest by converting to remove last
            ((ArrayDeque<BufferedImage>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    public void undo(BufferedImage current) {
        if (undoStack.isEmpty()) return;
        BufferedImage state = undoStack.pop();
        // Save current to redo
        BufferedImage copy = new BufferedImage(current.getWidth(), current.getHeight(), current.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(current, 0, 0, null);
        g.dispose();
        redoStack.push(copy);
        // Restore
        Graphics2D g2 = current.createGraphics();
        g2.drawImage(state, 0, 0, null);
        g2.dispose();
    }

    public void redo(BufferedImage current) {
        if (redoStack.isEmpty()) return;
        BufferedImage state = redoStack.pop();
        // Save current to undo
        BufferedImage copy = new BufferedImage(current.getWidth(), current.getHeight(), current.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(current, 0, 0, null);
        g.dispose();
        undoStack.push(copy);
        // Restore
        Graphics2D g2 = current.createGraphics();
        g2.drawImage(state, 0, 0, null);
        g2.dispose();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}

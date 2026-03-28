package com.seanick80.drawingapp;

import com.seanick80.drawingapp.layers.Layer;
import com.seanick80.drawingapp.layers.LayerManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Undo/redo manager that snapshots all layers.
 * Compacts after 80 states by discarding the oldest, keeping 50.
 */
public class UndoManager {

    private static final int COMPACT_THRESHOLD = 80;
    private static final int COMPACT_TARGET = 50;

    private final Deque<LayerSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<LayerSnapshot> redoStack = new ArrayDeque<>();
    private final int maxStates;

    public UndoManager(int maxStates) {
        this.maxStates = maxStates;
    }

    /** Snapshot all layers for undo. */
    public void saveState(LayerManager lm) {
        undoStack.push(snapshot(lm));
        if (undoStack.size() > maxStates) {
            ((ArrayDeque<LayerSnapshot>) undoStack).removeLast();
        }
        redoStack.clear();

        // Compact if we've accumulated too many states
        if (undoStack.size() >= COMPACT_THRESHOLD) {
            compact();
        }
    }

    /** Undo: restore the previous layer state. */
    public void undo(LayerManager lm) {
        if (undoStack.isEmpty()) return;
        redoStack.push(snapshot(lm));
        restore(undoStack.pop(), lm);
    }

    /** Redo: restore the next layer state. */
    public void redo(LayerManager lm) {
        if (redoStack.isEmpty()) return;
        undoStack.push(snapshot(lm));
        restore(redoStack.pop(), lm);
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    /** Discard oldest undo states to keep only COMPACT_TARGET. */
    private void compact() {
        while (undoStack.size() > COMPACT_TARGET) {
            ((ArrayDeque<LayerSnapshot>) undoStack).removeLast();
        }
    }

    // --- Snapshot internals ---

    private static LayerSnapshot snapshot(LayerManager lm) {
        List<LayerState> states = new ArrayList<>();
        for (int i = 0; i < lm.getLayerCount(); i++) {
            states.add(new LayerState(lm.getLayer(i)));
        }
        return new LayerSnapshot(states, lm.getActiveIndex());
    }

    private static void restore(LayerSnapshot snap, LayerManager lm) {
        // Remove all existing layers and rebuild from snapshot
        while (lm.getLayerCount() > 1) {
            lm.removeLayer(lm.getLayerCount() - 1);
        }

        // Restore first layer
        if (!snap.layers.isEmpty()) {
            snap.layers.get(0).restoreTo(lm.getLayer(0));
        }

        // Add remaining layers
        for (int i = 1; i < snap.layers.size(); i++) {
            Layer added = lm.addLayer();
            if (added != null) {
                snap.layers.get(i).restoreTo(added);
            }
        }

        lm.setActiveIndex(snap.activeIndex);
        lm.fireChange();
    }

    /** Immutable snapshot of all layers at a point in time. */
    private record LayerSnapshot(List<LayerState> layers, int activeIndex) {}

    /** Snapshot of a single layer's state. */
    private static class LayerState {
        final String name;
        final BufferedImage imageCopy;
        final float opacity;
        final com.seanick80.drawingapp.layers.BlendMode blendMode;
        final boolean visible;
        final boolean locked;

        LayerState(Layer layer) {
            this.name = layer.getName();
            this.opacity = layer.getOpacity();
            this.blendMode = layer.getBlendMode();
            this.visible = layer.isVisible();
            this.locked = layer.isLocked();
            // Deep copy the image
            BufferedImage src = layer.getImage();
            this.imageCopy = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
            Graphics2D g = imageCopy.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }

        void restoreTo(Layer layer) {
            layer.setName(name);
            layer.setOpacity(opacity);
            layer.setBlendMode(blendMode);
            layer.setVisible(visible);
            layer.setLocked(locked);
            Graphics2D g = layer.getImage().createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(imageCopy, 0, 0, null);
            g.dispose();
        }
    }
}

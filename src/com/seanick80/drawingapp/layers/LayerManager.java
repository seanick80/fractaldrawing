package com.seanick80.drawingapp.layers;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages an ordered list of layers and provides compositing.
 * Layer index 0 is the bottom (background) layer.
 */
public class LayerManager {

    public static final int MAX_LAYERS = 20;

    private final List<Layer> layers = new ArrayList<>();
    private int activeIndex = 0;
    private final List<LayerChangeListener> listeners = new ArrayList<>();

    public LayerManager(int width, int height) {
        Layer bg = new Layer("Background", width, height);
        bg.fill(Color.WHITE);
        layers.add(bg);
    }

    // --- Layer access ---

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public int getLayerCount() { return layers.size(); }

    public Layer getLayer(int index) { return layers.get(index); }

    public Layer getActiveLayer() { return layers.get(activeIndex); }

    public int getActiveIndex() { return activeIndex; }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < layers.size()) {
            activeIndex = index;
            fireChange();
        }
    }

    // --- Layer operations ---

    public Layer addLayer() {
        if (layers.size() >= MAX_LAYERS) return null;
        Layer first = layers.get(0);
        int num = layers.size() + 1;
        Layer layer = new Layer("Layer " + num, first.getWidth(), first.getHeight());
        layers.add(activeIndex + 1, layer);
        activeIndex = activeIndex + 1;
        fireChange();
        return layer;
    }

    public void removeLayer(int index) {
        if (layers.size() <= 1) return; // must keep at least one layer
        layers.remove(index);
        if (activeIndex >= layers.size()) activeIndex = layers.size() - 1;
        fireChange();
    }

    public Layer duplicateLayer(int index) {
        if (layers.size() >= MAX_LAYERS) return null;
        Layer copy = layers.get(index).duplicate();
        layers.add(index + 1, copy);
        activeIndex = index + 1;
        fireChange();
        return copy;
    }

    public void moveLayerUp(int index) {
        if (index >= layers.size() - 1) return;
        Collections.swap(layers, index, index + 1);
        if (activeIndex == index) activeIndex = index + 1;
        else if (activeIndex == index + 1) activeIndex = index;
        fireChange();
    }

    public void moveLayerDown(int index) {
        if (index <= 0) return;
        Collections.swap(layers, index, index - 1);
        if (activeIndex == index) activeIndex = index - 1;
        else if (activeIndex == index - 1) activeIndex = index;
        fireChange();
    }

    /** Merge the given layer down into the layer below it. */
    public void mergeDown(int index) {
        if (index <= 0) return;
        Layer upper = layers.get(index);
        Layer lower = layers.get(index - 1);

        Graphics2D g = lower.getImage().createGraphics();
        if (upper.getBlendMode() == BlendMode.NORMAL) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, upper.getOpacity()));
        } else {
            g.setComposite(new BlendComposite(upper.getBlendMode(), upper.getOpacity()));
        }
        g.drawImage(upper.getImage(), 0, 0, null);
        g.dispose();

        layers.remove(index);
        if (activeIndex >= index) activeIndex = Math.max(0, activeIndex - 1);
        fireChange();
    }

    /** Flatten all layers into a single layer. */
    public void flattenAll() {
        BufferedImage flat = composite();
        layers.clear();
        Layer bg = new Layer("Background", flat);
        layers.add(bg);
        activeIndex = 0;
        fireChange();
    }

    // --- Compositing ---

    /** Composite all visible layers into a single image. */
    public BufferedImage composite() {
        Layer first = layers.get(0);
        BufferedImage result = new BufferedImage(
            first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();

        for (Layer layer : layers) {
            if (!layer.isVisible()) continue;
            if (layer.getBlendMode() == BlendMode.NORMAL) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.getOpacity()));
            } else {
                g.setComposite(new BlendComposite(layer.getBlendMode(), layer.getOpacity()));
            }
            g.drawImage(layer.getImage(), 0, 0, null);
        }
        g.dispose();
        return result;
    }

    // --- Resize ---

    /** Resize all layers (content lost — caller should handle undo). */
    public void resizeAll(int width, int height) {
        for (Layer layer : layers) {
            layer.resize(width, height);
        }
        layers.get(0).fill(Color.WHITE);
        fireChange();
    }

    /** Reset to a single white background layer at the given size. */
    public void reset(int width, int height) {
        layers.clear();
        Layer bg = new Layer("Background", width, height);
        bg.fill(Color.WHITE);
        layers.add(bg);
        activeIndex = 0;
        fireChange();
    }

    // --- Change notification ---

    public void addChangeListener(LayerChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(LayerChangeListener listener) {
        listeners.remove(listener);
    }

    public void fireChange() {
        for (LayerChangeListener l : listeners) {
            l.layersChanged();
        }
    }

    public interface LayerChangeListener {
        void layersChanged();
    }
}

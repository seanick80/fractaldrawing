package com.seanick80.drawingapp.layers;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A single layer in the drawing. Holds a BufferedImage and compositing properties.
 */
public class Layer {

    private String name;
    private BufferedImage image;
    private float opacity;
    private BlendMode blendMode;
    private boolean visible;
    private boolean locked;

    public Layer(String name, int width, int height) {
        this.name = name;
        this.opacity = 1.0f;
        this.blendMode = BlendMode.NORMAL;
        this.visible = true;
        this.locked = false;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /** Create a layer from an existing image (e.g., loading a file). */
    public Layer(String name, BufferedImage source) {
        this.name = name;
        this.opacity = 1.0f;
        this.blendMode = BlendMode.NORMAL;
        this.visible = true;
        this.locked = false;
        this.image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BufferedImage getImage() { return image; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = Math.max(0f, Math.min(1f, opacity)); }

    public BlendMode getBlendMode() { return blendMode; }
    public void setBlendMode(BlendMode mode) { this.blendMode = mode; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }

    /** Clear the layer to fully transparent. */
    public void clear() {
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
    }

    /** Fill layer with a solid color (used for background layer). */
    public void fill(Color color) {
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
    }

    /** Resize the layer image (content is lost). */
    public void resize(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /** Create a deep copy of this layer. */
    public Layer duplicate() {
        Layer copy = new Layer(name + " copy", image);
        copy.opacity = this.opacity;
        copy.blendMode = this.blendMode;
        copy.visible = this.visible;
        copy.locked = this.locked;
        return copy;
    }

    /** Create a small thumbnail of this layer for the layer panel. */
    public BufferedImage createThumbnail(int thumbWidth, int thumbHeight) {
        BufferedImage thumb = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, thumbWidth, thumbHeight, null);
        g.dispose();
        return thumb;
    }
}

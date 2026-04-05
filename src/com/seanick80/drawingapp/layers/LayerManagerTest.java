package com.seanick80.drawingapp.layers;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class LayerManagerTest {

    private LayerManager lm;

    @BeforeEach
    void setUp() {
        lm = new LayerManager(100, 80);
    }

    @Test @SmallTest
    void initialState() {
        assertEquals(1, lm.getLayerCount());
        assertEquals("Background", lm.getLayer(0).getName());
        assertEquals(0, lm.getActiveIndex());
        // Center pixel of background layer should be fully opaque white
        int pixel = lm.getLayer(0).getImage().getRGB(50, 40);
        assertEquals(0xFFFFFFFF, pixel);
    }

    @Test @SmallTest
    void addLayer_basics() {
        Layer newLayer = lm.addLayer();
        assertNotNull(newLayer);
        assertEquals(2, lm.getLayerCount());
        assertEquals(1, lm.getActiveIndex());
        // New layer should be transparent at (0,0)
        int alpha = (newLayer.getImage().getRGB(0, 0) >> 24) & 0xFF;
        assertEquals(0, alpha);
    }

    @Test @SmallTest
    void addLayer_maxLimit() {
        // Add layers until the max is reached
        while (lm.getLayerCount() < LayerManager.MAX_LAYERS) {
            assertNotNull(lm.addLayer());
        }
        // One more should return null
        assertNull(lm.addLayer());
    }

    @Test @SmallTest
    void layerProperties_defaults() {
        Layer layer = lm.getLayer(0);
        assertEquals(1.0f, layer.getOpacity(), 0.001f);
        assertEquals(BlendMode.NORMAL, layer.getBlendMode());
        assertTrue(layer.isVisible());
        assertFalse(layer.isLocked());
    }

    @Test @SmallTest
    void opacity_clamping() {
        Layer layer = lm.getLayer(0);
        layer.setOpacity(0.5f);
        assertEquals(0.5f, layer.getOpacity(), 0.001f);
        layer.setOpacity(1.5f);
        assertEquals(1.0f, layer.getOpacity(), 0.001f);
        layer.setOpacity(-0.5f);
        assertEquals(0.0f, layer.getOpacity(), 0.001f);
    }

    @Test @SmallTest
    void compositing_showsTopLayer() {
        Layer top = lm.addLayer();
        // Draw a red rectangle on the top layer
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        BufferedImage result = lm.composite();
        // Inside the red rect — should be red
        Color atCenter = new Color(result.getRGB(20, 20), true);
        assertEquals(255, atCenter.getRed());
        assertEquals(0, atCenter.getGreen());
        assertEquals(0, atCenter.getBlue());
        // Outside the red rect — should be white (background)
        Color outside = new Color(result.getRGB(5, 5), true);
        assertEquals(255, outside.getRed());
        assertEquals(255, outside.getGreen());
        assertEquals(255, outside.getBlue());
    }

    @Test @SmallTest
    void compositing_hidesInvisibleLayer() {
        Layer top = lm.addLayer();
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        top.setVisible(false);
        BufferedImage result = lm.composite();
        // Invisible top layer — composite at (20,20) should show white background
        Color atCenter = new Color(result.getRGB(20, 20), true);
        assertEquals(255, atCenter.getRed());
        assertEquals(255, atCenter.getGreen());
        assertEquals(255, atCenter.getBlue());
    }

    @Test @SmallTest
    void compositing_blends50percentOpacity() {
        Layer top = lm.addLayer();
        top.setOpacity(0.5f);
        // Fill top layer with red
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        BufferedImage result = lm.composite();
        // Red (255,0,0) at 50% over white (255,255,255) => R stays high, G drops
        Color blended = new Color(result.getRGB(50, 40), true);
        assertTrue(blended.getRed() > 200, "Red channel should be > 200, was " + blended.getRed());
        int green = blended.getGreen();
        assertTrue(green >= 100 && green <= 160, "Green channel should be 100-160, was " + green);
    }

    @Test @SmallTest
    void moveLayer_upAndDown() {
        lm.addLayer(); // index 1 is active
        lm.setActiveIndex(0);

        lm.moveLayerUp(0);
        assertEquals(1, lm.getActiveIndex());

        lm.moveLayerDown(1);
        assertEquals(0, lm.getActiveIndex());
    }

    @Test @SmallTest
    void removeLayer_keepsMinimumOne() {
        lm.addLayer(); // now 2 layers
        lm.removeLayer(1);
        assertEquals(1, lm.getLayerCount());
        // Removing the only remaining layer should be a no-op
        lm.removeLayer(0);
        assertEquals(1, lm.getLayerCount());
    }

    @Test @SmallTest
    void duplicateLayer_copiesContent() {
        // Draw blue on background
        Graphics2D g = lm.getLayer(0).getImage().createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        int countBefore = lm.getLayerCount();
        Layer dup = lm.duplicateLayer(0);
        assertNotNull(dup);
        assertEquals(countBefore + 1, lm.getLayerCount());
        // Duplicate should contain the blue pixel
        Color dupPixel = new Color(dup.getImage().getRGB(25, 25), true);
        assertEquals(0, dupPixel.getRed());
        assertEquals(0, dupPixel.getGreen());
        assertEquals(255, dupPixel.getBlue());
    }

    @Test @SmallTest
    void mergeDown_reducesCount() {
        Layer top = lm.addLayer();
        // Draw green on the top layer
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        lm.mergeDown(1);
        assertEquals(1, lm.getLayerCount());
        // The merged result should show green at (50,40)
        Color merged = new Color(lm.getLayer(0).getImage().getRGB(50, 40), true);
        assertEquals(0, merged.getRed());
        assertTrue(merged.getGreen() > 200, "Green channel should be dominant");
    }

    @Test @SmallTest
    void flattenAll_toSingleLayer() {
        lm.addLayer();
        lm.addLayer(); // total 3 layers
        assertEquals(3, lm.getLayerCount());
        lm.flattenAll();
        assertEquals(1, lm.getLayerCount());
    }

    @Test @SmallTest
    void blendModes_count() {
        assertEquals(8, BlendMode.values().length);
        assertEquals("Normal", BlendMode.NORMAL.getDisplayName());
    }

    @Test @SmallTest
    void blendComposite_multiplyDarkens() {
        // Base: white background
        // Top layer: 50% gray (128,128,128) with MULTIPLY blend
        Layer top = lm.addLayer();
        top.setBlendMode(BlendMode.MULTIPLY);
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(new Color(128, 128, 128, 255));
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        BufferedImage result = lm.composite();
        Color pixel = new Color(result.getRGB(50, 40), true);
        // Multiply: white (1.0) * gray (0.5) = 0.5 → R should be around 128
        assertTrue(pixel.getRed() < 200, "Multiply should darken; R was " + pixel.getRed());
        assertTrue(pixel.getRed() > 30, "Multiply of white*gray should not be black; R was " + pixel.getRed());
    }

    @Test @SmallTest
    void clear_makesTransparent() {
        Layer top = lm.addLayer();
        // Fill with red first
        Graphics2D g = top.getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 80);
        g.dispose();
        // Clear should make it transparent
        top.clear();
        int alpha = (top.getImage().getRGB(50, 40) >> 24) & 0xFF;
        assertEquals(0, alpha);
    }

    @Test @SmallTest
    void thumbnail_correctSize() {
        LayerManager big = new LayerManager(200, 150);
        BufferedImage thumb = big.getLayer(0).createThumbnail(40, 30);
        assertEquals(40, thumb.getWidth());
        assertEquals(30, thumb.getHeight());
    }

    @Test @SmallTest
    void bug1_invisibleLayerBlocked() {
        Layer bg = lm.getLayer(0);
        bg.fill(Color.WHITE);
        bg.setVisible(false);
        assertFalse(bg.isVisible());
        // Pixel is unchanged in the layer image itself
        Color pixel = new Color(bg.getImage().getRGB(50, 40), true);
        assertEquals(255, pixel.getRed());
        bg.setVisible(true);
        assertTrue(bg.isVisible());
    }

    @Test @SmallTest
    void bug6_dragReorder() {
        // Setup: 3 layers — Background(0), Layer A(1), Layer B(2)
        Layer layerA = lm.addLayer();
        layerA.setName("Layer A");
        Layer layerB = lm.addLayer();
        layerB.setName("Layer B");
        // Active is now at index 2 (Layer B)
        lm.setActiveIndex(2);

        // Move Layer B (index 2) to index 0 → order: B, Background, A
        lm.moveLayer(2, 0);
        assertEquals("Layer B", lm.getLayer(0).getName());
        assertEquals(0, lm.getActiveIndex()); // active follows the moved layer

        // Move Layer B back from index 0 to index 2 → order: Background, A, B
        lm.moveLayer(0, 2);
        assertEquals("Layer B", lm.getLayer(2).getName());
        assertEquals(2, lm.getActiveIndex());

        // Same-index move is a no-op
        int activeBefore = lm.getActiveIndex();
        lm.moveLayer(2, 2);
        assertEquals(activeBefore, lm.getActiveIndex());
        assertEquals("Layer B", lm.getLayer(2).getName());

        // Invalid indices are ignored
        lm.moveLayer(-1, 1);
        lm.moveLayer(0, 99);
        assertEquals(3, lm.getLayerCount()); // count unchanged

        // When a non-active layer crosses the active layer, active adjusts
        // Current state: Background(0), LayerA(1), LayerB(2), active=2
        lm.setActiveIndex(1); // active = LayerA at index 1
        lm.moveLayer(0, 2);   // move Background from 0 to 2: LayerA(0), LayerB(1), Background(2)
        // Active was at index 1 (LayerA); fromIndex(0) < activeIndex(1) and toIndex(2) >= activeIndex(1)
        // so active should shift down by 1 to index 0
        assertEquals(0, lm.getActiveIndex());
        assertEquals("Layer A", lm.getLayer(0).getName());
    }
}

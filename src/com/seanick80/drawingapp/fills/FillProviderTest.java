package com.seanick80.drawingapp.fills;

import com.seanick80.drawingapp.SmallTest;
import com.seanick80.drawingapp.gradient.ColorGradient;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static java.awt.Color.*;
import static org.junit.jupiter.api.Assertions.*;

class FillProviderTest {

    // -------------------------------------------------------------------------
    // FillRegistry
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void registry_empty() {
        FillRegistry registry = new FillRegistry();
        assertTrue(registry.getAll().isEmpty());
    }

    @Test @SmallTest
    void registry_registerAndLookup() {
        FillRegistry registry = new FillRegistry();
        registry.register(new SolidFill());
        registry.register(new GradientFill());
        registry.register(new CheckerboardFill());

        assertEquals(3, registry.getAll().size());
        assertEquals("Solid", registry.getByName("Solid").getName());
        assertEquals("Gradient", registry.getByName("Gradient").getName());
        assertEquals("Checkerboard", registry.getByName("Checkerboard").getName());
        // Unknown name returns first registered fill
        assertEquals("Solid", registry.getByName("Nonexistent").getName());
    }

    // -------------------------------------------------------------------------
    // SolidFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void solidFill_name() {
        SolidFill fill = new SolidFill();
        assertEquals("Solid", fill.getName());
    }

    @Test @SmallTest
    void solidFill_returnsBaseColor() {
        SolidFill fill = new SolidFill();
        assertEquals(RED, fill.createPaint(RED, 0, 0, 100, 100));
        assertEquals(BLUE, fill.createPaint(BLUE, 10, 20, 50, 50));
    }

    // -------------------------------------------------------------------------
    // GradientFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void gradientFill_nameAndAngle() {
        GradientFill fill = new GradientFill();
        assertEquals("Gradient", fill.getName());
        assertEquals(0, fill.getAngleDegrees());
        fill.setAngleDegrees(90);
        assertEquals(90, fill.getAngleDegrees());
    }

    @Test @SmallTest
    void gradientFill_returnsGradientPaint() {
        GradientFill fill = new GradientFill();
        Paint paint = fill.createPaint(RED, 0, 0, 100, 100);
        assertInstanceOf(GradientPaint.class, paint);
    }

    @Test @SmallTest
    void gradientFill_zeroWidthFallback() {
        GradientFill fill = new GradientFill();
        assertEquals(RED, fill.createPaint(RED, 0, 0, 0, 100));
    }

    @Test @SmallTest
    void gradientFill_isAngledProvider() {
        GradientFill fill = new GradientFill();
        assertInstanceOf(AngledFillProvider.class, fill);
    }

    // -------------------------------------------------------------------------
    // CustomGradientFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void customGradientFill_nameAndAngle() {
        CustomGradientFill fill = new CustomGradientFill();
        assertEquals("Custom Gradient", fill.getName());
        assertEquals(0, fill.getAngleDegrees());
        assertNotNull(fill.getGradient());

        ColorGradient newGradient = new ColorGradient();
        fill.setGradient(newGradient);
        assertEquals(newGradient, fill.getGradient());

        fill.setAngleDegrees(45);
        assertEquals(45, fill.getAngleDegrees());
    }

    @Test @SmallTest
    void customGradientFill_returnsTexturePaint() {
        CustomGradientFill fill = new CustomGradientFill();
        Paint paint = fill.createPaint(RED, 0, 0, 50, 50);
        assertInstanceOf(TexturePaint.class, paint);
    }

    @Test @SmallTest
    void customGradientFill_zeroWidthFallback() {
        CustomGradientFill fill = new CustomGradientFill();
        assertEquals(RED, fill.createPaint(RED, 0, 0, 0, 50));
    }

    // -------------------------------------------------------------------------
    // CheckerboardFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void checkerboardFill_nameAndPattern() {
        CheckerboardFill fill = new CheckerboardFill();
        assertEquals("Checkerboard", fill.getName());

        Paint paint = fill.createPaint(RED, 0, 0, 100, 100);
        assertInstanceOf(TexturePaint.class, paint);

        // Texture tile must be 16x16 (cellSize*2 = 8*2)
        TexturePaint texture = (TexturePaint) paint;
        BufferedImage img = texture.getImage();
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());

        // Top-left (0,0) is a dark cell; top-right (8,0) is the light cell — they must differ
        int topLeft = img.getRGB(0, 0);
        int topRight = img.getRGB(8, 0);
        assertNotEquals(topLeft, topRight);

        // CheckerboardFill does NOT implement AngledFillProvider
        assertFalse(fill instanceof AngledFillProvider);
    }

    // -------------------------------------------------------------------------
    // DiagonalStripeFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void diagonalStripeFill_nameAndAngle() {
        DiagonalStripeFill fill = new DiagonalStripeFill();
        assertEquals("Diagonal Stripes", fill.getName());
        assertEquals(45, fill.getAngleDegrees());

        fill.setAngleDegrees(60);
        assertEquals(60, fill.getAngleDegrees());

        Paint paint = fill.createPaint(RED, 0, 0, 64, 64);
        assertInstanceOf(TexturePaint.class, paint);

        // Zero-width fallback
        assertEquals(RED, fill.createPaint(RED, 0, 0, 0, 64));

        assertInstanceOf(AngledFillProvider.class, fill);
    }

    // -------------------------------------------------------------------------
    // CrosshatchFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void crosshatchFill_nameAndPattern() {
        CrosshatchFill fill = new CrosshatchFill();
        assertEquals("Crosshatch", fill.getName());
        assertEquals(45, fill.getAngleDegrees());

        fill.setAngleDegrees(30);
        assertEquals(30, fill.getAngleDegrees());

        Paint paint = fill.createPaint(RED, 0, 0, 64, 64);
        assertInstanceOf(TexturePaint.class, paint);

        // Zero-width fallback
        assertEquals(RED, fill.createPaint(RED, 0, 0, 0, 64));

        assertInstanceOf(AngledFillProvider.class, fill);

        // Verify there is drawn content — at least one non-transparent pixel in the tile
        TexturePaint texture = (TexturePaint) paint;
        BufferedImage img = texture.getImage();
        int nonTransparent = 0;
        for (int py = 0; py < img.getHeight(); py++) {
            for (int px = 0; px < img.getWidth(); px++) {
                int alpha = (img.getRGB(px, py) >> 24) & 0xFF;
                if (alpha > 0) nonTransparent++;
            }
        }
        assertTrue(nonTransparent > 0);
    }

    // -------------------------------------------------------------------------
    // DotGridFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void dotGridFill_nameAndDots() {
        DotGridFill fill = new DotGridFill();
        assertEquals("Dot Grid", fill.getName());

        Paint paint = fill.createPaint(RED, 0, 0, 100, 100);
        assertInstanceOf(TexturePaint.class, paint);

        assertFalse(fill instanceof AngledFillProvider);

        // The center of the 12x12 cell (at pixel 6,6) should have non-zero alpha
        TexturePaint texture = (TexturePaint) paint;
        BufferedImage img = texture.getImage();
        int centerAlpha = (img.getRGB(img.getWidth() / 2, img.getHeight() / 2) >> 24) & 0xFF;
        assertTrue(centerAlpha > 0);
    }

    // -------------------------------------------------------------------------
    // HorizontalStripeFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void horizontalStripeFill_nameAndAngle() {
        HorizontalStripeFill fill = new HorizontalStripeFill();
        assertEquals("Horizontal Stripes", fill.getName());
        assertEquals(0, fill.getAngleDegrees());

        fill.setAngleDegrees(90);
        assertEquals(90, fill.getAngleDegrees());

        Paint paint = fill.createPaint(RED, 0, 0, 64, 64);
        assertInstanceOf(TexturePaint.class, paint);

        // Zero-width fallback
        assertEquals(RED, fill.createPaint(RED, 0, 0, 0, 64));

        assertInstanceOf(AngledFillProvider.class, fill);
    }

    // -------------------------------------------------------------------------
    // NoiseFill
    // -------------------------------------------------------------------------

    @Test @SmallTest
    void noiseFill_nameAndPattern() {
        NoiseFill fill = new NoiseFill();
        assertEquals("Noise", fill.getName());

        Paint paint = fill.createPaint(RED, 0, 0, 100, 100);
        assertInstanceOf(TexturePaint.class, paint);

        assertFalse(fill instanceof AngledFillProvider);

        // The noise should exhibit meaningful alpha variation (range > 50)
        TexturePaint texture = (TexturePaint) paint;
        BufferedImage img = texture.getImage();
        int minAlpha = 255;
        int maxAlpha = 0;
        for (int py = 0; py < img.getHeight(); py++) {
            for (int px = 0; px < img.getWidth(); px++) {
                int alpha = (img.getRGB(px, py) >> 24) & 0xFF;
                if (alpha < minAlpha) minAlpha = alpha;
                if (alpha > maxAlpha) maxAlpha = alpha;
            }
        }
        assertTrue(maxAlpha - minAlpha > 50);

        // Determinism: two calls with the same position produce identical tiles
        TexturePaint paint2 = (TexturePaint) fill.createPaint(RED, 0, 0, 100, 100);
        BufferedImage img2 = paint2.getImage();
        for (int py = 0; py < img.getHeight(); py++) {
            for (int px = 0; px < img.getWidth(); px++) {
                assertEquals(img.getRGB(px, py), img2.getRGB(px, py));
            }
        }
    }
}

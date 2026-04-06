package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class MagicWandToolTest {

    @Test @MediumTest
    void magicWandSelectsSameColor() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.BLUE);
        g.fillRect(50, 0, 50, 100);
        g.dispose();

        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        MagicWandTool wand = new MagicWandTool();
        wand.mousePressed(img, 25, 50, canvas);

        assertTrue(wand.hasSelection(), "Magic wand should create a selection");
        Rectangle bounds = wand.getSelectionBounds();
        assertNotNull(bounds);
        // Should select the red half (x: 0-49)
        assertEquals(0, bounds.x);
        assertEquals(50, bounds.width);
    }

    @Test @MediumTest
    void magicWandCopyCut() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        MagicWandTool wand = new MagicWandTool();
        wand.mousePressed(img, 50, 50, null);

        BufferedImage copied = wand.copySelection(img);
        assertNotNull(copied);
        int centerRgb = copied.getRGB(copied.getWidth() / 2, copied.getHeight() / 2) & 0x00FFFFFF;
        assertEquals(0x00FF00, centerRgb, "Copied region should contain green");

        wand.deleteSelection(img);
        int alpha = (img.getRGB(50, 50) >> 24) & 0xFF;
        assertEquals(0, alpha, "Deleted region should be transparent");
    }

    @Test @MediumTest
    void magicWandClearSelection() {
        BufferedImage img = TestHelpers.whiteImage(50, 50);
        MagicWandTool wand = new MagicWandTool();
        wand.mousePressed(img, 25, 25, null);
        assertTrue(wand.hasSelection());

        wand.clearSelection();
        assertFalse(wand.hasSelection());
    }

    @Test @MediumTest
    void magicWandName() {
        MagicWandTool wand = new MagicWandTool();
        assertEquals("Magic Wand", wand.getName());
        assertTrue(wand.needsPersistentPreview());
    }
}

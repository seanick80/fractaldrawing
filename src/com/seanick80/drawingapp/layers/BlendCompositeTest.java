package com.seanick80.drawingapp.layers;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;

class BlendCompositeTest {

    private BufferedImage blendTest(Color base, Color blend, BlendMode mode) {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, 10, 10);
        g.setComposite(new BlendComposite(mode, 1.0f));
        g.setColor(blend);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        return img;
    }

    private int red(int rgb) { return (rgb >> 16) & 0xFF; }
    private int alpha(int rgb) { return (rgb >> 24) & 0xFF; }

    @Test @SmallTest
    void allModes_produceVisibleResult() {
        Color gray = new Color(128, 128, 128);
        for (BlendMode mode : BlendMode.values()) {
            BufferedImage img = blendTest(Color.WHITE, gray, mode);
            int pixel = img.getRGB(5, 5);
            assertNotEquals(0, alpha(pixel),
                "Mode " + mode + " produced fully transparent pixel");
        }
    }

    @Test @SmallTest
    void multiply_darkens() {
        BufferedImage img = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.MULTIPLY);
        int r = red(img.getRGB(5, 5));
        assertTrue(r >= 120 && r <= 140,
            "Multiply white * gray128 expected R in [120,140] but got " + r);
    }

    @Test @SmallTest
    void screen_lightens() {
        BufferedImage img = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.SCREEN);
        int r = red(img.getRGB(5, 5));
        assertTrue(r >= 250,
            "Screen white + gray128 expected R >= 250 but got " + r);
    }

    @Test @SmallTest
    void difference_subtractsMagnitude() {
        BufferedImage img = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.DIFFERENCE);
        int r = red(img.getRGB(5, 5));
        assertTrue(r >= 120 && r <= 135,
            "Difference |white - gray128| expected R in [120,135] but got " + r);
    }

    @Test @SmallTest
    void add_sums() {
        BufferedImage img = blendTest(new Color(100, 100, 100), new Color(100, 100, 100), BlendMode.ADD);
        int r = red(img.getRGB(5, 5));
        assertTrue(r >= 190 && r <= 210,
            "Add 100 + 100 expected R in [190,210] but got " + r);
    }

    @Test @SmallTest
    void normal_replaces() {
        BufferedImage img = blendTest(Color.WHITE, Color.RED, BlendMode.NORMAL);
        int pixel = img.getRGB(5, 5);
        assertEquals(255, red(pixel), "Normal mode on RED should give R=255");
        assertEquals(0, (pixel >> 8) & 0xFF, "Normal mode on RED should give G=0");
    }

    @Test @SmallTest
    void opacity50_blends() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 10, 10);
        g.setComposite(new BlendComposite(BlendMode.NORMAL, 0.5f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        int r = red(img.getRGB(5, 5));
        assertTrue(r >= 120 && r <= 135,
            "50% opacity black over white expected R in [120,135] but got " + r);
    }
}

package com.seanick80.drawingapp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Smoke test: verifies JUnit 5 infra and TestHelpers work. */
class TestHelpersTest {

    @Test @SmallTest
    void whiteImage_isAllWhite() {
        BufferedImage img = TestHelpers.whiteImage(10, 10);
        assertEquals(10, img.getWidth());
        assertEquals(10, img.getHeight());
        assertEquals(Color.WHITE.getRGB(), img.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), img.getRGB(9, 9));
    }

    @Test @SmallTest
    void countUniqueColors_singleColor() {
        BufferedImage img = TestHelpers.whiteImage(5, 5);
        assertEquals(1, TestHelpers.countUniqueColors(img));
    }

    @Test @SmallTest
    void imagesEqual_identical() {
        BufferedImage a = TestHelpers.whiteImage(10, 10);
        BufferedImage b = TestHelpers.whiteImage(10, 10);
        assertTrue(TestHelpers.imagesEqual(a, b));
    }

    @Test @SmallTest
    void imagesEqual_different() {
        BufferedImage a = TestHelpers.whiteImage(10, 10);
        BufferedImage b = TestHelpers.whiteImage(10, 10);
        b.setRGB(0, 0, Color.RED.getRGB());
        assertFalse(TestHelpers.imagesEqual(a, b));
    }

    @Test @SmallTest
    void colorDistance_sameColor() {
        assertEquals(0, TestHelpers.colorDistance(0xFF0000, 0xFF0000));
    }

    @Test @SmallTest
    void colorDistance_blackToWhite() {
        assertEquals(255 * 3, TestHelpers.colorDistance(0x000000, 0xFFFFFF));
    }

    @Test @SmallTest
    void newRenderer_isMandelbrot() {
        var r = TestHelpers.newRenderer();
        assertNotNull(r);
    }

    @Test @SmallTest
    void gradient_notNull() {
        assertNotNull(TestHelpers.gradient());
    }
}

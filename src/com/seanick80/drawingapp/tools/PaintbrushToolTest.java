package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class PaintbrushToolTest {

    @Test @MediumTest
    void paintbrushDrawsOnCanvas() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        PaintbrushTool brush = new PaintbrushTool();
        brush.setStrokeSize(12);
        brush.mousePressed(img, 50, 50, canvas);
        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF),
                "PaintbrushTool should draw on canvas");
    }

    @Test @MediumTest
    void paintbrushStampHasSoftEdge() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        // Fill transparent
        PaintbrushTool brush = new PaintbrushTool();
        brush.setStrokeSize(30);
        brush.paintStamp(img, 50, 50, Color.RED);

        // Center pixel should be fully opaque
        int centerAlpha = (img.getRGB(50, 50) >> 24) & 0xFF;
        assertTrue(centerAlpha > 200, "Center of brush stamp should be mostly opaque, got " + centerAlpha);

        // Edge pixel should be partially transparent (due to hardness falloff)
        // Check a pixel near the edge of the radius (radius = 15)
        int edgeAlpha = (img.getRGB(50 + 13, 50) >> 24) & 0xFF;
        assertTrue(edgeAlpha < centerAlpha,
                "Edge pixel should have lower alpha than center");
    }

    @Test @MediumTest
    void paintbrushInterpolatesStrokes() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        PaintbrushTool brush = new PaintbrushTool();
        brush.setStrokeSize(6);
        brush.mousePressed(img, 10, 50, canvas);
        brush.mouseDragged(img, 90, 50, canvas);

        // Check that intermediate pixels along the path were painted
        boolean midPainted = (img.getRGB(50, 50) & 0x00FFFFFF) != 0x00FFFFFF;
        assertTrue(midPainted, "Paintbrush should interpolate stamps along drag path");
    }

    @Test @MediumTest
    void paintbrushName() {
        PaintbrushTool brush = new PaintbrushTool();
        assertEquals("Brush", brush.getName());
        assertTrue(brush.hasStrokeSize());
        assertEquals(12, brush.getDefaultStrokeSize());
    }
}

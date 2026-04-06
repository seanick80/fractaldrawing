package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class LassoToolTest {

    @Test @MediumTest
    void lassoCreatesSelection() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        LassoTool lasso = new LassoTool();

        // Draw a triangle
        lasso.mousePressed(img, 50, 10, canvas);
        lasso.mouseDragged(img, 90, 90, canvas);
        lasso.mouseDragged(img, 10, 90, canvas);
        lasso.mouseReleased(img, 10, 90, canvas);

        assertTrue(lasso.hasSelection(), "Lasso should create a selection after closing path");
    }

    @Test @MediumTest
    void lassoCopySelection() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        LassoTool lasso = new LassoTool();
        // Select a rectangular-ish area
        lasso.mousePressed(img, 20, 20, null);
        lasso.mouseDragged(img, 80, 20, null);
        lasso.mouseDragged(img, 80, 80, null);
        lasso.mouseDragged(img, 20, 80, null);
        lasso.mouseReleased(img, 20, 80, null);

        BufferedImage copied = lasso.copySelection(img);
        assertNotNull(copied);
        assertTrue(copied.getWidth() > 0 && copied.getHeight() > 0);
    }

    @Test @MediumTest
    void lassoClearSelection() {
        BufferedImage img = TestHelpers.whiteImage(50, 50);
        LassoTool lasso = new LassoTool();
        lasso.mousePressed(img, 10, 10, null);
        lasso.mouseDragged(img, 40, 10, null);
        lasso.mouseDragged(img, 40, 40, null);
        lasso.mouseReleased(img, 40, 40, null);
        assertTrue(lasso.hasSelection());

        lasso.clearSelection();
        assertFalse(lasso.hasSelection());
    }

    @Test @MediumTest
    void lassoName() {
        LassoTool lasso = new LassoTool();
        assertEquals("Lasso", lasso.getName());
        assertTrue(lasso.needsPersistentPreview());
    }
}

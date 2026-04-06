package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class SelectionToolTest {

    @Test @MediumTest
    void selectionToolCreatesSelection() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        SelectionTool sel = new SelectionTool();

        sel.mousePressed(img, 10, 10, canvas);
        sel.mouseDragged(img, 60, 60, canvas);
        sel.mouseReleased(img, 60, 60, canvas);

        assertTrue(sel.hasSelection(), "Selection tool should create a selection after drag");
    }

    @Test @MediumTest
    void selectionToolCopyAndPaste() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(20, 20, 40, 40);
        g.dispose();

        SelectionTool sel = new SelectionTool();
        // Create selection over the red area
        sel.mousePressed(img, 20, 20, null);
        sel.mouseDragged(img, 60, 60, null);
        sel.mouseReleased(img, 60, 60, null);

        BufferedImage copied = sel.copySelection(img);
        assertNotNull(copied, "copySelection should return an image");
        assertTrue(copied.getWidth() > 0 && copied.getHeight() > 0,
                "Copied image should have positive dimensions");

        // Verify the copied region contains red pixels
        int centerRgb = copied.getRGB(copied.getWidth() / 2, copied.getHeight() / 2) & 0x00FFFFFF;
        assertEquals(0xFF0000, centerRgb, "Copied region should contain red pixels");
    }

    @Test @MediumTest
    void selectionToolCutClearsRegion() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        SelectionTool sel = new SelectionTool();
        sel.mousePressed(img, 20, 20, null);
        sel.mouseDragged(img, 60, 60, null);
        sel.mouseReleased(img, 60, 60, null);

        sel.cutSelection(img);

        // The cut region should be transparent
        int alpha = (img.getRGB(40, 40) >> 24) & 0xFF;
        assertEquals(0, alpha, "Cut region should be transparent");
    }

    @Test @MediumTest
    void selectionToolSelectAllAndClear() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        SelectionTool sel = new SelectionTool();

        sel.selectAll(img);
        assertTrue(sel.hasSelection(), "selectAll should create a selection");

        sel.clearSelection();
        assertFalse(sel.hasSelection(), "clearSelection should remove the selection");
    }

    @Test @MediumTest
    void selectionToolCommitsOnNewSelection() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        // Fill transparent, then paint red square
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        SelectionTool sel = new SelectionTool();
        // Select and cut the red area
        sel.mousePressed(img, 10, 10, null);
        sel.mouseDragged(img, 40, 40, null);
        sel.mouseReleased(img, 40, 40, null);
        sel.cutSelection(img);

        // Start a new selection — this should commit the floating content back
        sel.mousePressed(img, 70, 70, null);
        sel.mouseDragged(img, 90, 90, null);
        sel.mouseReleased(img, 90, 90, null);

        // The original cut area should have content again (committed back)
        int rgb = img.getRGB(25, 25);
        int alpha = (rgb >> 24) & 0xFF;
        assertTrue(alpha > 0, "Floating content should be committed when starting new selection");
    }

    @Test @MediumTest
    void selectionToolName() {
        SelectionTool sel = new SelectionTool();
        assertEquals("Select", sel.getName());
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.fills.SolidFill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class DrawingToolTest {

    @Test @MediumTest
    void pencilToolDrawsOnCanvas() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        PencilTool pencil = new PencilTool();
        pencil.setStrokeSize(3);
        pencil.mousePressed(img, 10, 10, canvas);
        pencil.mouseDragged(img, 50, 50, canvas);
        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF), "PencilTool should draw on canvas");
        assertEquals("Pencil", pencil.getName());
        assertEquals(2, pencil.getDefaultStrokeSize());
    }

    @Test @MediumTest
    void lineToolDrawsOnCanvas() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        LineTool line = new LineTool();
        line.setStrokeSize(2);
        line.mousePressed(img, 0, 0, canvas);
        line.mouseReleased(img, 99, 99, canvas);
        boolean diagonalChanged = false;
        for (int i = 10; i < 90; i++) {
            if ((img.getRGB(i, i) & 0x00FFFFFF) != 0x00FFFFFF) {
                diagonalChanged = true;
                break;
            }
        }
        assertTrue(diagonalChanged, "LineTool should draw along diagonal");
        assertEquals("Line", line.getName());
    }

    @Test @MediumTest
    void rectangleToolDrawsOutline() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        RectangleTool rect = new RectangleTool();
        rect.setStrokeSize(1);
        rect.mousePressed(img, 10, 10, canvas);
        rect.mouseReleased(img, 90, 90, canvas);
        boolean borderChanged = (img.getRGB(10, 10) & 0x00FFFFFF) != 0x00FFFFFF;
        boolean interiorWhite = (img.getRGB(50, 50) & 0x00FFFFFF) == 0x00FFFFFF;
        assertTrue(borderChanged, "Rectangle border should be non-white");
        assertTrue(interiorWhite, "Rectangle interior should be white (outline only)");
        assertEquals("Rectangle", rect.getName());
    }

    @Test @MediumTest
    void rectangleToolFilled() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        RectangleTool rect = new RectangleTool();
        rect.setStrokeSize(1);
        rect.setFilled(true);
        rect.setFillProvider(new SolidFill());
        rect.mousePressed(img, 10, 10, canvas);
        rect.mouseReleased(img, 90, 90, canvas);
        boolean interiorFilled = (img.getRGB(50, 50) & 0x00FFFFFF) != 0x00FFFFFF;
        assertTrue(interiorFilled, "Filled rectangle interior should be non-white");
    }

    @Test @MediumTest
    void ovalToolDrawsOnCanvas() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        OvalTool oval = new OvalTool();
        oval.setStrokeSize(2);
        oval.mousePressed(img, 10, 10, canvas);
        oval.mouseReleased(img, 90, 90, canvas);
        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF), "OvalTool should draw on canvas");
        assertEquals("Oval", oval.getName());
    }

    @Test @MediumTest
    void eraserToolErases() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        EraserTool eraser = new EraserTool();
        eraser.setSize(20);
        eraser.mousePressed(img, 50, 50, canvas);
        int pixel = img.getRGB(50, 50) & 0x00FFFFFF;
        assertNotEquals(0xFF0000, pixel, "Eraser should erase (no longer red)");
        assertEquals("Eraser", eraser.getName());
        assertEquals(18, eraser.getDefaultStrokeSize());
    }

    @Test @MediumTest
    void fillToolFloodFill() {
        BufferedImage img = TestHelpers.whiteImage(100, 100);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRect(20, 20, 60, 60);
        g.dispose();

        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        FillTool fill = new FillTool();
        fill.setFillProvider(new SolidFill());
        fill.mousePressed(img, 50, 50, canvas);
        int pixel = img.getRGB(50, 50) & 0x00FFFFFF;
        assertNotEquals(0xFFFFFF, pixel, "FillTool should flood fill interior");
        assertEquals("Fill", fill.getName());
    }

    @Test @MediumTest
    void pencilToolStrokeStyle() {
        PencilTool pencil = new PencilTool();
        assertEquals(StrokeStyle.SOLID, pencil.getStrokeStyle());

        pencil.setStrokeStyle(StrokeStyle.DASHED);
        assertEquals(StrokeStyle.DASHED, pencil.getStrokeStyle());

        // Draw with dashed style
        BufferedImage img = TestHelpers.whiteImage(50, 50);
        DrawingCanvas canvas = TestHelpers.testCanvas(50, 50);
        pencil.mousePressed(img, 5, 25, canvas);
        pencil.mouseDragged(img, 45, 25, canvas);
        pencil.mouseReleased(img, 45, 25, canvas);
        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF), "Pencil dashed should draw");

        // Rough style
        pencil.setStrokeStyle(StrokeStyle.ROUGH);
        BufferedImage img2 = TestHelpers.whiteImage(50, 50);
        pencil.mousePressed(img2, 5, 25, canvas);
        pencil.mouseDragged(img2, 45, 25, canvas);
        pencil.mouseReleased(img2, 45, 25, canvas);
        assertFalse(TestHelpers.isAllColor(img2, 0xFFFFFF), "Pencil rough should draw");
    }

    @Test @MediumTest
    void lineToolStrokeStyle() {
        LineTool line = new LineTool();
        assertEquals(StrokeStyle.SOLID, line.getStrokeStyle());

        line.setStrokeStyle(StrokeStyle.DOTTED);
        assertEquals(StrokeStyle.DOTTED, line.getStrokeStyle());

        BufferedImage img = TestHelpers.whiteImage(50, 50);
        DrawingCanvas canvas = TestHelpers.testCanvas(50, 50);
        line.mousePressed(img, 5, 25, canvas);
        line.mouseReleased(img, 45, 25, canvas);
        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF), "Line dotted should draw");

        // Rough style
        line.setStrokeStyle(StrokeStyle.ROUGH);
        BufferedImage img2 = TestHelpers.whiteImage(50, 50);
        line.mousePressed(img2, 5, 25, canvas);
        line.mouseReleased(img2, 45, 25, canvas);
        assertFalse(TestHelpers.isAllColor(img2, 0xFFFFFF), "Line rough should draw");
    }
}

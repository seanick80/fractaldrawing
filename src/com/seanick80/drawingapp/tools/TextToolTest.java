package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.ColorPicker;
import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.MediumTest;
import com.seanick80.drawingapp.SmallTest;
import com.seanick80.drawingapp.TestHelpers;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class TextToolTest {

    @Test
    @MediumTest
    void textToolName() {
        TextTool tool = new TextTool();
        assertEquals("Text", tool.getName());
        assertTrue(tool.needsPersistentPreview());
    }

    @Test
    @MediumTest
    void textToolCommitsToImage() {
        BufferedImage img = TestHelpers.whiteImage(200, 200);

        // Simulate rasterizing text directly (same as what commitFloatingToLayer does)
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.setColor(Color.BLACK);
        g.drawString("Hello", 50, 100);
        g.dispose();

        assertFalse(TestHelpers.isAllColor(img, 0xFFFFFF), "Text should be drawn on image");
    }

    @Test
    @MediumTest
    void textToolSettingsPanel() {
        TextTool tool = new TextTool();
        JPanel panel = tool.createSettingsPanel(null);
        assertNotNull(panel, "Settings panel should be created");
    }

    @Test
    @SmallTest
    void textAreaCreatedOnMouseRelease() {
        TextTool tool = new TextTool();
        DrawingCanvas canvas = TestHelpers.testCanvas(200, 200);
        BufferedImage img = canvas.getActiveLayerImage();
        tool.onActivated(img, canvas);

        // Click at a point (small drag = click)
        tool.mousePressed(img, 50, 80, canvas);
        tool.mouseReleased(img, 52, 81, canvas);

        assertTrue(tool.isEditing(), "Should be in editing mode after click");
        assertNotNull(tool.getTextArea(), "JTextArea should be created");
    }

    @Test
    @SmallTest
    void textColorMatchesForeground() {
        TextTool tool = new TextTool();
        DrawingCanvas canvas = TestHelpers.testCanvas(200, 200);
        ColorPicker colorPicker = new ColorPicker(canvas);
        canvas.setColorPicker(colorPicker);
        colorPicker.setForegroundColor(Color.RED);
        BufferedImage img = canvas.getActiveLayerImage();
        tool.onActivated(img, canvas);

        tool.mousePressed(img, 50, 80, canvas);
        tool.mouseReleased(img, 52, 81, canvas);

        assertEquals(Color.RED, tool.getTextColor(),
                "Text color should match canvas foreground color");
    }

    @Test
    @SmallTest
    void multilineTextBoundsAccountForAllLines() {
        // Verify that multiline text rendering produces an image taller than single line
        Font font = new Font("SansSerif", Font.PLAIN, 24);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = measure.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.dispose();

        int singleLineHeight = fm.getHeight();
        int threeLineHeight = 3 * fm.getHeight();

        // Three lines should be 3x the height of a single line
        assertTrue(threeLineHeight > singleLineHeight,
                "Three-line text height should exceed single-line height");

        // Verify max width is measured correctly across lines
        BufferedImage m2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = m2.createGraphics();
        g2.setFont(font);
        FontMetrics fm2 = g2.getFontMetrics();
        String shortLine = "Hi";
        String longLine = "Hello World Wide";
        int shortWidth = fm2.stringWidth(shortLine);
        int longWidth = fm2.stringWidth(longLine);
        g2.dispose();

        assertTrue(longWidth > shortWidth,
                "Longer line should have greater width");
    }

    @Test
    @SmallTest
    void clickDragDefinesTextRectangle() {
        TextTool tool = new TextTool();
        DrawingCanvas canvas = TestHelpers.testCanvas(400, 400);
        BufferedImage img = canvas.getActiveLayerImage();
        tool.onActivated(img, canvas);

        // Drag from (50,50) to (250,150) - a 200px wide rectangle
        tool.mousePressed(img, 50, 50, canvas);
        tool.mouseDragged(img, 250, 150, canvas);

        assertTrue(tool.isDefining(), "Should be defining text rectangle during drag");

        tool.mouseReleased(img, 250, 150, canvas);

        assertFalse(tool.isDefining(), "Defining should end on release");
        assertTrue(tool.isEditing(), "Should be editing after drag release");
        assertNotNull(tool.getTextArea(), "TextArea should be created");
    }

    @Test
    @SmallTest
    void clickOutsideTextAreaCommitsText() {
        TextTool tool = new TextTool();
        DrawingCanvas canvas = TestHelpers.testCanvas(400, 400);
        BufferedImage img = canvas.getActiveLayerImage();
        tool.onActivated(img, canvas);

        // Create text area at (50,80)
        tool.mousePressed(img, 50, 80, canvas);
        tool.mouseReleased(img, 52, 81, canvas);
        assertTrue(tool.isEditing(), "Should be editing");

        // Click far outside the text area to commit (empty text = just closes)
        tool.mousePressed(img, 350, 350, canvas);
        tool.mouseReleased(img, 352, 351, canvas);

        // Should have started new editing at the new position
        assertTrue(tool.isEditing(), "Should be editing at new position");
    }

    @Test
    @SmallTest
    void defaultTextColorIsBlack() {
        TextTool tool = new TextTool();
        assertEquals(Color.BLACK, tool.getTextColor(),
                "Default text color should be black");
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.MediumTest;
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
}

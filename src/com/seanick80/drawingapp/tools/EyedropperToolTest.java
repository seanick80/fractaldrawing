package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class EyedropperToolTest {

    @Test @MediumTest
    void eyedropperSamplesForegroundColor() {
        // loadImage calls compositeImage(), so getImage() returns the red composite
        BufferedImage redImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = redImg.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker colorPicker = new ColorPicker(canvas);
        canvas.setColorPicker(colorPicker);
        canvas.loadImage(redImg);

        EyedropperTool eyedropper = new EyedropperTool();
        eyedropper.mousePressed(canvas.getActiveLayerImage(), 50, 50, canvas);

        assertEquals(Color.RED.getRGB(), colorPicker.getForegroundColor().getRGB(),
                "Eyedropper should sample red and set foreground color");
    }

    @Test @MediumTest
    void eyedropperIgnoresOutOfBounds() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker colorPicker = new ColorPicker(canvas);
        canvas.setColorPicker(colorPicker);
        Color originalFg = colorPicker.getForegroundColor();

        EyedropperTool eyedropper = new EyedropperTool();
        eyedropper.mousePressed(canvas.getActiveLayerImage(), -1, -1, canvas);

        assertEquals(originalFg.getRGB(), colorPicker.getForegroundColor().getRGB(),
                "Eyedropper should not change color for out-of-bounds coordinates");
    }

    @Test @MediumTest
    void eyedropperName() {
        EyedropperTool eyedropper = new EyedropperTool();
        assertEquals("Eyedropper", eyedropper.getName());
        assertFalse(eyedropper.hasStrokeSize());
    }
}

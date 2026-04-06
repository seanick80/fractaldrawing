package com.seanick80.drawingapp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;

class ColorPickerTest {

    @Test @MediumTest
    void defaultColors() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker picker = new ColorPicker(canvas);
        assertEquals(Color.BLACK, picker.getForegroundColor());
        assertEquals(Color.WHITE, picker.getBackgroundColor());
    }

    @Test @MediumTest
    void setForegroundFiresEvent() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker picker = new ColorPicker(canvas);
        boolean[] fired = {false};
        picker.addColorPropertyChangeListener(evt -> {
            if (ColorPicker.PROP_FOREGROUND_COLOR.equals(evt.getPropertyName())) {
                fired[0] = true;
            }
        });
        picker.setForegroundColor(Color.RED);
        assertTrue(fired[0], "Setting foreground color should fire property change");
        assertEquals(Color.RED, picker.getForegroundColor());
    }

    @Test @MediumTest
    void setBackgroundColor() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker picker = new ColorPicker(canvas);
        picker.setBackgroundColor(Color.BLUE);
        assertEquals(Color.BLUE, picker.getBackgroundColor());
    }

    @Test @MediumTest
    void removeListenerStopsFiring() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker picker = new ColorPicker(canvas);
        int[] count = {0};
        java.beans.PropertyChangeListener listener = evt -> count[0]++;
        picker.addColorPropertyChangeListener(listener);
        picker.setForegroundColor(Color.GREEN);
        assertEquals(1, count[0]);
        picker.removeColorPropertyChangeListener(listener);
        picker.setForegroundColor(Color.BLUE);
        assertEquals(1, count[0], "Removed listener should not fire");
    }

    @Test @MediumTest
    void swapColorsResetColors() {
        DrawingCanvas canvas = TestHelpers.testCanvas(100, 100);
        ColorPicker picker = new ColorPicker(canvas);
        picker.setForegroundColor(Color.RED);
        picker.setBackgroundColor(Color.BLUE);
        assertEquals(Color.RED, picker.getForegroundColor());
        assertEquals(Color.BLUE, picker.getBackgroundColor());
    }
}

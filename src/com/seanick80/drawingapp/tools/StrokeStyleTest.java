package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Stroke;

import static com.seanick80.drawingapp.tools.StrokeStyle.*;
import static org.junit.jupiter.api.Assertions.*;

class StrokeStyleTest {

    @Test @SmallTest
    void values_hasFiveStyles() {
        assertEquals(5, StrokeStyle.values().length);
    }

    @Test @SmallTest
    void displayNames() {
        assertEquals("Solid",    SOLID.getDisplayName());
        assertEquals("Dashed",   DASHED.getDisplayName());
        assertEquals("Dotted",   DOTTED.getDisplayName());
        assertEquals("Dash-Dot", DASH_DOT.getDisplayName());
        assertEquals("Rough",    ROUGH.getDisplayName());
    }

    @Test @SmallTest
    void createStroke_allNonNull() {
        for (StrokeStyle style : StrokeStyle.values()) {
            assertNotNull(style.createStroke(4), style.name() + " returned null stroke");
        }
    }

    @Test @SmallTest
    void dashed_hasDashArray() {
        Stroke stroke = DASHED.createStroke(2);
        assertInstanceOf(BasicStroke.class, stroke);
        float[] dashArray = ((BasicStroke) stroke).getDashArray();
        assertNotNull(dashArray);
        assertTrue(dashArray.length > 0);
    }

    @Test @SmallTest
    void solid_noDashArray() {
        Stroke stroke = SOLID.createStroke(2);
        assertInstanceOf(BasicStroke.class, stroke);
        assertNull(((BasicStroke) stroke).getDashArray());
    }
}

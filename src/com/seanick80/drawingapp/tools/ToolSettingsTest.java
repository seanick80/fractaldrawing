package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.SmallTest;
import com.seanick80.drawingapp.fills.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import java.awt.*;

class ToolSettingsTest {

    private JComboBox<?> findComboBox(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JComboBox<?> cb) return cb;
            if (comp instanceof Container sub) {
                JComboBox<?> found = findComboBox(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test @SmallTest
    void toolDefaultStrokeSizes() {
        assertEquals(2, new PencilTool().getDefaultStrokeSize());
        assertEquals(2, new LineTool().getDefaultStrokeSize());
        assertEquals(2, new RectangleTool().getDefaultStrokeSize());
        assertEquals(2, new OvalTool().getDefaultStrokeSize());
        assertEquals(18, new EraserTool().getDefaultStrokeSize());
        assertEquals(2, new FillTool().getDefaultStrokeSize());
        assertEquals(1, new FractalTool().getDefaultStrokeSize());
    }

    @Test @SmallTest
    void toolNames() {
        assertEquals("Pencil", new PencilTool().getName());
        assertEquals("Line", new LineTool().getName());
        assertEquals("Rectangle", new RectangleTool().getName());
        assertEquals("Oval", new OvalTool().getName());
        assertEquals("Eraser", new EraserTool().getName());
        assertEquals("Fill", new FillTool().getName());
        assertEquals("Fractal", new FractalTool().getName());
    }

    @Test @SmallTest
    void toolCapabilities_hasStrokeSize() {
        assertTrue(new PencilTool().hasStrokeSize());
        assertTrue(new LineTool().hasStrokeSize());
        assertTrue(new RectangleTool().hasStrokeSize());
        assertTrue(new OvalTool().hasStrokeSize());
        assertTrue(new EraserTool().hasStrokeSize());
        assertFalse(new FillTool().hasStrokeSize());
        assertFalse(new FractalTool().hasStrokeSize());
    }

    @Test @SmallTest
    void toolCapabilities_hasFill() {
        assertTrue(new RectangleTool().hasFill());
        assertTrue(new OvalTool().hasFill());
        assertTrue(new FillTool().hasFill());
        assertFalse(new PencilTool().hasFill());
        assertFalse(new LineTool().hasFill());
        assertFalse(new EraserTool().hasFill());
        assertFalse(new FractalTool().hasFill());
    }

    @Test @SmallTest
    void bug4_fillNoneDropdown() {
        FillRegistry registry = new FillRegistry();
        registry.register(new SolidFill());
        registry.register(new GradientFill());

        JPanel panel = ToolSettingsBuilder.createFillOptionsPanel(
            registry, null, true, null, null, null);

        JComboBox<?> combo = findComboBox(panel);
        assertNotNull(combo, "Expected to find a JComboBox in the fill panel");
        assertEquals("None", combo.getItemAt(0));
        assertEquals(3, combo.getItemCount(), "Expected fills + 1 (None) items");
        assertEquals("None", combo.getSelectedItem());
    }

    @Test @SmallTest
    void bug4_fillComboInitializesToCurrentFill() {
        FillRegistry registry = new FillRegistry();
        SolidFill solid = new SolidFill();
        registry.register(solid);
        registry.register(new GradientFill());

        JPanel panel = ToolSettingsBuilder.createFillOptionsPanel(
            registry, null, true, solid, null, null);

        JComboBox<?> combo = findComboBox(panel);
        assertNotNull(combo);
        assertEquals("Solid", combo.getSelectedItem());
    }

    @Test @SmallTest
    void bug4_fillToolHasNoNone() {
        FillRegistry registry = new FillRegistry();
        registry.register(new SolidFill());
        registry.register(new GradientFill());

        JPanel panel = ToolSettingsBuilder.createFillOptionsPanel(
            registry, null, false, null, null, null);

        JComboBox<?> combo = findComboBox(panel);
        assertNotNull(combo);
        assertNotEquals("None", combo.getItemAt(0));
    }
}

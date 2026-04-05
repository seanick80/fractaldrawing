package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.SmallTest;
import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.gradient.GradientToolbar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import java.awt.*;

class ToolSettingsTest {

    /** Minimal ToolSettingsContext for testing panel creation. */
    private static ToolSettingsContext testContext(FillRegistry registry) {
        return new ToolSettingsContext() {
            @Override public FillRegistry getFillRegistry() { return registry; }
            @Override public GradientToolbar getGradientToolbar() { return null; }
        };
    }

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

    // --- Settings panel creation tests ---

    @Test @SmallTest
    void allDrawingToolsProduceSettingsPanels() {
        FillRegistry registry = new FillRegistry();
        registry.register(new SolidFill());
        ToolSettingsContext ctx = testContext(registry);

        assertNotNull(new PencilTool().createSettingsPanel(ctx), "PencilTool panel");
        assertNotNull(new LineTool().createSettingsPanel(ctx), "LineTool panel");
        assertNotNull(new RectangleTool().createSettingsPanel(ctx), "RectangleTool panel");
        assertNotNull(new OvalTool().createSettingsPanel(ctx), "OvalTool panel");
        assertNotNull(new EraserTool().createSettingsPanel(ctx), "EraserTool panel");
        assertNotNull(new FillTool().createSettingsPanel(ctx), "FillTool panel");
    }

    @Test @SmallTest
    void fractalToolProducesSettingsPanel() {
        ToolSettingsContext ctx = testContext(new FillRegistry());
        assertNotNull(new FractalTool().createSettingsPanel(ctx), "FractalTool panel");
    }

    @Test @SmallTest
    void fractalToolExposesRenderer() {
        FractalTool ft = new FractalTool();
        assertNotNull(ft.getRenderer(), "FractalTool should expose its renderer");
    }

    @Test @SmallTest
    void fractalToolGradientCallback() {
        FractalTool ft = new FractalTool();
        // onGradientChanged should be callable without error (no NPE)
        ft.onGradientChanged();
    }

    @Test @SmallTest
    void gradientChangeCallback() {
        // FractalTool provides a callback; drawing tools do not
        assertNotNull(new FractalTool().getGradientChangeCallback());
        assertNull(new PencilTool().getGradientChangeCallback());
        assertNull(new RectangleTool().getGradientChangeCallback());
        assertNull(new FillTool().getGradientChangeCallback());
    }

    @Test @SmallTest
    void customGradientFillGradientWiring() {
        CustomGradientFill cgf = new CustomGradientFill();
        assertNotNull(cgf.getGradient(), "Should have a default gradient");

        com.seanick80.drawingapp.gradient.ColorGradient g =
            new com.seanick80.drawingapp.gradient.ColorGradient();
        cgf.setGradient(g);
        assertSame(g, cgf.getGradient(), "Gradient should be settable");
    }
}

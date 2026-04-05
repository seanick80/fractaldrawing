package com.seanick80.drawingapp.project;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.layers.BlendMode;
import com.seanick80.drawingapp.layers.Layer;
import com.seanick80.drawingapp.layers.LayerManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;

class FdpSerializerTest {

    @Test @MediumTest @Tag("parser")
    void roundTripSingleLayer(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("single.fdp").toFile();
        LayerManager lm = new LayerManager(50, 50);
        java.awt.Graphics2D g = lm.getActiveLayer().getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 20, 20);
        g.dispose();

        FdpSerializer.save(tmpFile, lm, TestHelpers.newRenderer(), TestHelpers.gradient());
        assertTrue(tmpFile.exists());
        assertTrue(tmpFile.length() > 0);

        AppState state = FdpSerializer.load(tmpFile);
        assertEquals(1, state.layers.size());
        assertEquals(50, state.canvasWidth);
        assertEquals(50, state.canvasHeight);

        int red = (state.layers.get(0).getImage().getRGB(15, 15) >> 16) & 0xFF;
        assertTrue(red > 200, "Layer pixel should be red");
    }

    @Test @MediumTest @Tag("parser")
    void roundTripMultiLayer(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("multi.fdp").toFile();
        LayerManager lm = new LayerManager(40, 40);
        lm.addLayer();
        lm.addLayer();
        lm.setActiveIndex(1);
        lm.getLayer(1).setBlendMode(BlendMode.MULTIPLY);
        lm.getLayer(2).setBlendMode(BlendMode.SCREEN);
        lm.getLayer(1).setOpacity(0.5f);

        FdpSerializer.save(tmpFile, lm, TestHelpers.newRenderer(), TestHelpers.gradient());
        AppState state = FdpSerializer.load(tmpFile);

        assertEquals(3, state.layers.size());
        assertEquals(1, state.activeLayerIndex);
        assertEquals(BlendMode.MULTIPLY, state.layers.get(1).getBlendMode());
        assertEquals(BlendMode.SCREEN, state.layers.get(2).getBlendMode());
        assertEquals(0.5f, state.layers.get(1).getOpacity(), 0.01f);
    }

    @Test @MediumTest @Tag("parser")
    void roundTripFractalState(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("fractal.fdp").toFile();
        LayerManager lm = new LayerManager(40, 40);
        FractalRenderer renderer = TestHelpers.newRenderer();
        renderer.setMaxIterations(512);
        renderer.setColorMode(FractalRenderer.ColorMode.DIVISION);
        renderer.setInteriorPruning(false);

        FdpSerializer.save(tmpFile, lm, renderer, TestHelpers.gradient());
        AppState state = FdpSerializer.load(tmpFile);

        assertNotNull(state.fractalState);
        assertEquals("MANDELBROT", state.fractalState.typeName);
        assertEquals(512, state.fractalState.maxIterations);
        assertEquals("DIVISION", state.fractalState.colorMode);
        assertFalse(state.fractalState.interiorPruning);
    }

    @Test @MediumTest @Tag("parser")
    void roundTripGradient(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("gradient.fdp").toFile();
        LayerManager lm = new LayerManager(40, 40);
        ColorGradient grad = TestHelpers.gradient();

        FdpSerializer.save(tmpFile, lm, TestHelpers.newRenderer(), grad);
        AppState state = FdpSerializer.load(tmpFile);

        assertNotNull(state.gradient);
        assertEquals(grad.getStops().size(), state.gradient.getStops().size());

        ColorGradient.Stop origFirst = grad.getStops().get(0);
        ColorGradient.Stop loadFirst = state.gradient.getStops().get(0);
        assertEquals(origFirst.getPosition(), loadFirst.getPosition(), 0.001f);
        assertEquals(origFirst.getColor(), loadFirst.getColor());
    }

    @Test @MediumTest @Tag("parser")
    void roundTripLayerProperties(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("props.fdp").toFile();
        LayerManager lm = new LayerManager(30, 30);
        Layer layer = lm.getActiveLayer();
        layer.setName("Test Layer");
        layer.setVisible(false);
        layer.setLocked(true);
        layer.setOpacity(0.75f);
        layer.setBlendMode(BlendMode.OVERLAY);

        FdpSerializer.save(tmpFile, lm, TestHelpers.newRenderer(), TestHelpers.gradient());
        AppState state = FdpSerializer.load(tmpFile);

        Layer loaded = state.layers.get(0);
        assertEquals("Test Layer", loaded.getName());
        assertFalse(loaded.isVisible());
        assertTrue(loaded.isLocked());
        assertEquals(0.75f, loaded.getOpacity(), 0.01f);
        assertEquals(BlendMode.OVERLAY, loaded.getBlendMode());
    }

    @Test @MediumTest @Tag("parser")
    void backwardCompat(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("compat.fdp").toFile();
        LayerManager lm = new LayerManager(30, 30);
        FdpSerializer.save(tmpFile, lm, null, null);
        AppState state = FdpSerializer.load(tmpFile);

        assertEquals(1, state.layers.size());
        assertNull(state.fractalState);
        assertNull(state.gradient);
    }

    @Test @MediumTest @Tag("parser")
    void roundTripBigDecimalPrecision(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("precision.fdp").toFile();
        LayerManager lm = new LayerManager(30, 30);
        FractalRenderer renderer = TestHelpers.newDeeperZoomRenderer();

        FdpSerializer.save(tmpFile, lm, renderer, TestHelpers.gradient());
        AppState state = FdpSerializer.load(tmpFile);

        String origMinReal = renderer.getMinRealBig().toPlainString();
        assertEquals(origMinReal, state.fractalState.minReal);
        assertEquals(706, state.fractalState.maxIterations);
    }

    @Test @MediumTest @Tag("parser")
    void bug5SaveFileTracking(@TempDir Path tempDir) throws Exception {
        File tmpFile = tempDir.resolve("tracking.fdp").toFile();
        LayerManager lm = new LayerManager(50, 50);
        lm.getActiveLayer().fill(Color.RED);
        FdpSerializer.save(tmpFile, lm, null, null);
        assertTrue(tmpFile.exists());
        assertTrue(tmpFile.length() > 0);

        lm.getActiveLayer().fill(Color.BLUE);
        FdpSerializer.save(tmpFile, lm, null, null);
        assertTrue(tmpFile.exists());

        AppState state = FdpSerializer.load(tmpFile);
        assertNotNull(state);
        assertEquals(50, state.canvasWidth);
        assertEquals(50, state.canvasHeight);
    }
}

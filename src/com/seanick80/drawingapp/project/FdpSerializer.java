package com.seanick80.drawingapp.project;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.layers.BlendMode;
import com.seanick80.drawingapp.layers.Layer;
import com.seanick80.drawingapp.layers.LayerManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes and deserializes the full application state to/from .fdp files
 * using Protocol Buffers.
 */
public class FdpSerializer {

    private static final int CURRENT_VERSION = 1;

    /**
     * Save the current application state to an .fdp file.
     */
    public static void save(File file, LayerManager layerManager,
                            FractalRenderer renderer, ColorGradient gradient)
            throws IOException {
        FdpProto.FdpFile.Builder fdp = FdpProto.FdpFile.newBuilder();
        fdp.setVersion(CURRENT_VERSION);

        Layer first = layerManager.getLayer(0);
        fdp.setCanvasWidth(first.getWidth());
        fdp.setCanvasHeight(first.getHeight());
        fdp.setActiveLayerIndex(layerManager.getActiveIndex());

        // Serialize layers
        for (Layer layer : layerManager.getLayers()) {
            fdp.addLayers(serializeLayer(layer));
        }

        // Serialize fractal state
        if (renderer != null) {
            fdp.setFractalState(serializeFractalState(renderer));
        }

        // Serialize gradient
        if (gradient != null) {
            fdp.setGradient(serializeGradient(gradient));
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fdp.build().writeTo(fos);
        }
    }

    /**
     * Load application state from an .fdp file.
     * Returns an AppState containing all deserialized data.
     */
    public static AppState load(File file) throws IOException {
        FdpProto.FdpFile fdp;
        try (FileInputStream fis = new FileInputStream(file)) {
            fdp = FdpProto.FdpFile.parseFrom(fis);
        }

        AppState state = new AppState();
        state.canvasWidth = fdp.getCanvasWidth();
        state.canvasHeight = fdp.getCanvasHeight();
        state.activeLayerIndex = fdp.getActiveLayerIndex();

        // Deserialize layers
        state.layers = new ArrayList<>();
        for (FdpProto.LayerData ld : fdp.getLayersList()) {
            state.layers.add(deserializeLayer(ld));
        }

        // Deserialize fractal state
        if (fdp.hasFractalState()) {
            state.fractalState = deserializeFractalState(fdp.getFractalState());
        }

        // Deserialize gradient
        if (fdp.hasGradient()) {
            state.gradient = deserializeGradient(fdp.getGradient());
        }

        return state;
    }

    // --- Layer serialization ---

    private static FdpProto.LayerData serializeLayer(Layer layer) throws IOException {
        FdpProto.LayerData.Builder ld = FdpProto.LayerData.newBuilder();
        ld.setName(layer.getName());
        ld.setOpacity(layer.getOpacity());
        ld.setBlendMode(layer.getBlendMode().name());
        ld.setVisible(layer.isVisible());
        ld.setLocked(layer.isLocked());

        // PNG-encode the layer image
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(layer.getImage(), "PNG", baos);
        ld.setPngData(com.google.protobuf.ByteString.copyFrom(baos.toByteArray()));

        return ld.build();
    }

    private static Layer deserializeLayer(FdpProto.LayerData ld) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(ld.getPngData().toByteArray()));
        Layer layer = new Layer(ld.getName(), img);
        layer.setOpacity(ld.getOpacity());
        layer.setVisible(ld.getVisible());
        layer.setLocked(ld.getLocked());

        try {
            layer.setBlendMode(BlendMode.valueOf(ld.getBlendMode()));
        } catch (IllegalArgumentException e) {
            layer.setBlendMode(BlendMode.NORMAL);
        }

        return layer;
    }

    // --- Fractal state serialization ---

    private static FdpProto.FractalState serializeFractalState(FractalRenderer renderer) {
        FdpProto.FractalState.Builder fs = FdpProto.FractalState.newBuilder();
        fs.setTypeName(renderer.getType().name());
        fs.setMinReal(renderer.getMinRealBig().toPlainString());
        fs.setMaxReal(renderer.getMaxRealBig().toPlainString());
        fs.setMinImag(renderer.getMinImagBig().toPlainString());
        fs.setMaxImag(renderer.getMaxImagBig().toPlainString());
        fs.setMaxIterations(renderer.getMaxIterations());
        fs.setRenderMode(renderer.getRenderMode().name());
        fs.setColorMode(renderer.getColorMode().name());
        fs.setJuliaReal(renderer.getJuliaRealBig().toPlainString());
        fs.setJuliaImag(renderer.getJuliaImagBig().toPlainString());
        fs.setInteriorPruning(renderer.isInteriorPruning());
        fs.setPixelGuessing(renderer.isPixelGuessing());
        return fs.build();
    }

    private static AppState.FractalStateData deserializeFractalState(FdpProto.FractalState fs) {
        AppState.FractalStateData state = new AppState.FractalStateData();
        state.typeName = fs.getTypeName();
        state.minReal = fs.getMinReal();
        state.maxReal = fs.getMaxReal();
        state.minImag = fs.getMinImag();
        state.maxImag = fs.getMaxImag();
        state.maxIterations = fs.getMaxIterations();
        state.renderMode = fs.getRenderMode();
        state.colorMode = fs.getColorMode();
        state.juliaReal = fs.getJuliaReal();
        state.juliaImag = fs.getJuliaImag();
        state.interiorPruning = fs.getInteriorPruning();
        state.pixelGuessing = fs.getPixelGuessing();
        return state;
    }

    // --- Gradient serialization ---

    private static FdpProto.GradientData serializeGradient(ColorGradient gradient) {
        FdpProto.GradientData.Builder gd = FdpProto.GradientData.newBuilder();
        for (ColorGradient.Stop stop : gradient.getStops()) {
            Color c = stop.getColor();
            gd.addStops(FdpProto.GradientStop.newBuilder()
                    .setPosition(stop.getPosition())
                    .setRed(c.getRed())
                    .setGreen(c.getGreen())
                    .setBlue(c.getBlue())
                    .build());
        }
        return gd.build();
    }

    private static ColorGradient deserializeGradient(FdpProto.GradientData gd) {
        ColorGradient gradient = new ColorGradient();
        gradient.getStops().clear();
        for (FdpProto.GradientStop gs : gd.getStopsList()) {
            gradient.addStop(gs.getPosition(), new Color(gs.getRed(), gs.getGreen(), gs.getBlue()));
        }
        return gradient;
    }
}

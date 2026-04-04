package com.seanick80.drawingapp.project;

import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.layers.Layer;

import java.util.List;

/**
 * Value class holding all deserialized project state from an .fdp file.
 */
public class AppState {

    public int canvasWidth;
    public int canvasHeight;
    public List<Layer> layers;
    public int activeLayerIndex;
    public FractalStateData fractalState;
    public ColorGradient gradient;

    /**
     * Fractal renderer state stored as strings to preserve BigDecimal precision.
     */
    public static class FractalStateData {
        public String typeName;
        public String minReal;
        public String maxReal;
        public String minImag;
        public String maxImag;
        public int maxIterations;
        public String renderMode;
        public String colorMode;
        public String juliaReal;
        public String juliaImag;
        public boolean interiorPruning;
        public boolean pixelGuessing;
    }
}

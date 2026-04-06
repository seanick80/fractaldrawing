package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientToolbar;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Fractal explorer tool. Coordinates rendering, mouse interaction,
 * and UI panel assembly by delegating to specialized components.
 */
public class FractalTool implements Tool {

    private final FractalRenderer renderer = new FractalRenderer();
    private final FractalLocationManager locationManager = new FractalLocationManager();
    private final FractalAnimationController animationController = new FractalAnimationController();
    private final FractalInfoPanel infoPanel = new FractalInfoPanel();
    private final FractalRenderController renderController;
    private final FractalSettingsPanel settingsPanel = new FractalSettingsPanel();

    private JMenu fractalMenu;

    // Pan state
    private int dragStartX, dragStartY;
    private boolean dragging;
    private static final int DRAG_THRESHOLD = 5;

    public FractalTool() {
        renderController = new FractalRenderController(renderer, infoPanel);
    }

    public static void setDefaultLocationDirectory(File dir) {
        FractalLocationManager.setDefaultLocationDirectory(dir);
    }

    public static File getDefaultLocationDirectory() {
        return FractalLocationManager.getDefaultLocationDirectory();
    }

    public void setGradientToolbar(GradientToolbar toolbar) {
        renderController.setGradientToolbar(toolbar);
        if (toolbar != null) {
            toolbar.setPaletteCycleRenderer(renderer);
            toolbar.setPaletteCycleFrameCallback(img -> {
                DrawingCanvas canvas = renderController.getLastCanvas();
                if (canvas != null && img != null) {
                    BufferedImage target = canvas.getActiveLayerImage();
                    Graphics2D g = target.createGraphics();
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                    canvas.repaint();
                }
            });
        }
    }

    public FractalRenderer getRenderer() { return renderer; }
    public FractalAnimationController getAnimationController() { return animationController; }
    public FractalLocationManager getLocationManager() { return locationManager; }
    public FractalInfoPanel getInfoPanel() { return infoPanel; }

    public ColorGradient getGradient() {
        return renderController.getGradient();
    }

    /** Called when the gradient is modified externally (e.g. from the gradient toolbar). */
    public void onGradientChanged() {
        renderController.onGradientChanged();
    }

    @Override
    public Runnable getGradientChangeCallback() {
        return this::onGradientChanged;
    }

    @Override
    public String getName() { return "Fractal"; }

    @Override
    public int getDefaultStrokeSize() { return 1; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        return settingsPanel.build(renderer, renderController, locationManager,
                animationController, infoPanel);
    }

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        renderController.setActive(true);
        renderController.setLastImage(image);
        renderController.setLastCanvas(canvas);
        renderController.registerColorListener(canvas);
        renderController.renderAsync(image, canvas);
    }

    @Override
    public void onDeactivated() {
        renderController.setActive(false);
    }

    // --- Mouse interaction ---

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        renderController.setLastImage(image);
        renderController.setLastCanvas(canvas);
        dragStartX = x;
        dragStartY = y;
        dragging = false;
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        int dx = x - dragStartX;
        int dy = y - dragStartY;
        if (!dragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
            dragging = true;
            renderController.cancelWorkerOnly();
        }
        if (!dragging) return;
        double vz = canvas.getViewZoom();
        canvas.setPanOffset((int)(dx * vz), (int)(dy * vz));
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragging) {
            dragging = false;
            int dx = x - dragStartX;
            int dy = y - dragStartY;
            int w = image.getWidth(), h = image.getHeight();

            BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
            BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
            MathContext mc = FractalAnimationController.getZoomMathContext(minR, maxR, minI, maxI);
            BigDecimal rangeR = maxR.subtract(minR, mc);
            BigDecimal rangeI = maxI.subtract(minI, mc);

            BigDecimal deltaR = rangeR.multiply(BigDecimal.valueOf(dx), mc)
                                      .divide(BigDecimal.valueOf(w), mc).negate();
            BigDecimal deltaI = rangeI.multiply(BigDecimal.valueOf(dy), mc)
                                      .divide(BigDecimal.valueOf(h), mc).negate();

            renderer.setBounds(
                minR.add(deltaR, mc), maxR.add(deltaR, mc),
                minI.add(deltaI, mc), maxI.add(deltaI, mc));

            renderController.setLastImage(image);
            renderController.setLastCanvas(canvas);
            renderController.renderAsync(image, canvas);
            return;
        }

        // No drag -- zoom click
        renderController.setLastImage(image);
        renderController.setLastCanvas(canvas);
        int w = image.getWidth(), h = image.getHeight();
        int button = canvas.getLastMouseButton();

        if (button == java.awt.event.MouseEvent.BUTTON1 || button == java.awt.event.MouseEvent.BUTTON3) {
            BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
            BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
            MathContext mc = FractalAnimationController.getZoomMathContext(minR, maxR, minI, maxI);
            BigDecimal rangeR = maxR.subtract(minR, mc);
            BigDecimal rangeI = maxI.subtract(minI, mc);

            BigDecimal xFrac = new BigDecimal(x).divide(new BigDecimal(w), mc);
            BigDecimal yFrac = new BigDecimal(y).divide(new BigDecimal(h), mc);
            BigDecimal clickReal = minR.add(xFrac.multiply(rangeR, mc), mc);
            BigDecimal clickImag = minI.add(yFrac.multiply(rangeI, mc), mc);

            BigDecimal zoomFactor = (button == java.awt.event.MouseEvent.BUTTON1)
                ? new BigDecimal("0.5") : new BigDecimal("2.0");
            BigDecimal newRangeR = rangeR.multiply(zoomFactor, mc);
            BigDecimal newRangeI = rangeI.multiply(zoomFactor, mc);
            BigDecimal two = new BigDecimal(2);

            renderer.setBounds(
                clickReal.subtract(newRangeR.divide(two, mc), mc),
                clickReal.add(newRangeR.divide(two, mc), mc),
                clickImag.subtract(newRangeI.divide(two, mc), mc),
                clickImag.add(newRangeI.divide(two, mc), mc));
        }

        renderController.renderAsync(image, canvas);
    }

    @Override
    public void mouseWheelMoved(BufferedImage image, int x, int y, int wheelRotation, DrawingCanvas canvas) {
        // Ctrl+scroll = fractal zoom centered on cursor position
        renderController.setLastImage(image);
        renderController.setLastCanvas(canvas);
        int w = image.getWidth(), h = image.getHeight();

        BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
        BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
        MathContext mc = FractalAnimationController.getZoomMathContext(minR, maxR, minI, maxI);
        BigDecimal rangeR = maxR.subtract(minR, mc);
        BigDecimal rangeI = maxI.subtract(minI, mc);

        BigDecimal xFrac = new BigDecimal(x).divide(new BigDecimal(w), mc);
        BigDecimal yFrac = new BigDecimal(y).divide(new BigDecimal(h), mc);
        BigDecimal centerReal = minR.add(xFrac.multiply(rangeR, mc), mc);
        BigDecimal centerImag = minI.add(yFrac.multiply(rangeI, mc), mc);

        // Scroll up = zoom in, scroll down = zoom out
        BigDecimal zoomFactor = (wheelRotation < 0) ? new BigDecimal("0.8") : new BigDecimal("1.25");
        BigDecimal newRangeR = rangeR.multiply(zoomFactor, mc);
        BigDecimal newRangeI = rangeI.multiply(zoomFactor, mc);
        BigDecimal two = new BigDecimal(2);

        renderer.setBounds(
            centerReal.subtract(newRangeR.divide(two, mc), mc),
            centerReal.add(newRangeR.divide(two, mc), mc),
            centerImag.subtract(newRangeI.divide(two, mc), mc),
            centerImag.add(newRangeI.divide(two, mc), mc));

        renderController.renderAsync(image, canvas);
    }

    @Override
    public void drawPreview(Graphics2D g) {}

    // --- Location loading (used by DrawingApp menu) ---

    public void loadLocationFile(File file) {
        locationManager.loadLocationFile(renderer, file,
                settingsPanel.getTypeCombo(), settingsPanel.getIterSpinner(),
                () -> { infoPanel.updateInfoLabels(renderer); renderController.triggerRender(); });
    }

    @Override
    public JMenu getMenu() {
        if (fractalMenu == null) {
            fractalMenu = FractalMenuBuilder.build(renderer, renderController, this);
        }
        return fractalMenu;
    }
}

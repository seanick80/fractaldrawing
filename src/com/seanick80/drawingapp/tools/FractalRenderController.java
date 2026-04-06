package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.ColorPicker;
import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientToolbar;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;

/**
 * Manages asynchronous fractal rendering, gradient state, and color listener
 * registration. Extracted from FractalTool to separate render orchestration
 * from mouse/UI concerns.
 */
public class FractalRenderController {

    private final FractalRenderer renderer;
    private final FractalInfoPanel infoPanel;

    private SwingWorker<BufferedImage, Integer> currentWorker;
    private BufferedImage lastImage;
    private DrawingCanvas lastCanvas;
    private boolean active;

    private JButton renderBtn;
    private GradientToolbar gradientToolbar;
    private JPanel gradientPreview;
    private ColorGradient gradient;

    private PropertyChangeListener colorListener;
    private ColorPicker registeredColorPicker;

    public FractalRenderController(FractalRenderer renderer, FractalInfoPanel infoPanel) {
        this.renderer = renderer;
        this.infoPanel = infoPanel;
        this.gradient = ColorGradient.fractalDefault();
    }

    // --- Gradient access ---

    public ColorGradient getGradient() {
        if (gradientToolbar != null) return gradientToolbar.getGradient();
        return gradient;
    }

    public void setGradient(ColorGradient gradient) {
        this.gradient = gradient;
    }

    public ColorGradient getOwnedGradient() {
        return gradient;
    }

    public void setGradientToolbar(GradientToolbar toolbar) {
        this.gradientToolbar = toolbar;
        if (toolbar != null) {
            gradient = toolbar.getGradient();
        }
    }

    public GradientToolbar getGradientToolbar() {
        return gradientToolbar;
    }

    public void setRenderButton(JButton btn) { this.renderBtn = btn; }
    public void setGradientPreview(JPanel preview) { this.gradientPreview = preview; }

    // --- State ---

    public BufferedImage getLastImage() { return lastImage; }
    public DrawingCanvas getLastCanvas() { return lastCanvas; }
    public void setLastImage(BufferedImage img) { this.lastImage = img; }
    public void setLastCanvas(DrawingCanvas canvas) { this.lastCanvas = canvas; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // --- Rendering ---

    public boolean isRendering() {
        return currentWorker != null && !currentWorker.isDone();
    }

    public void triggerRender() {
        if (lastCanvas != null) {
            renderAsync(lastCanvas.getActiveLayerImage(), lastCanvas);
        }
    }

    public void renderAsync(BufferedImage image, DrawingCanvas canvas) {
        lastImage = image;
        lastCanvas = canvas;

        if (currentWorker != null && !currentWorker.isDone()) {
            renderer.cancelRender();
            currentWorker.cancel(true);
        }

        int w = image.getWidth(), h = image.getHeight();
        infoPanel.setRenderStartTime(System.currentTimeMillis());
        setRenderButtonCancel();
        infoPanel.startProgressTimer(renderer, w, h, image, canvas);

        ColorGradient renderGradient = getGradient();

        currentWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return renderer.render(w, h, renderGradient);
            }

            @Override
            protected void done() {
                infoPanel.stopProgressTimer();
                setRenderButtonIdle();
                if (isCancelled()) {
                    JLabel pl = infoPanel.getProgressLabel();
                    if (pl != null) pl.setText("Cancelled");
                    return;
                }
                try {
                    BufferedImage fractalImage = get();
                    if (fractalImage != null) {
                        canvas.setPanOffset(0, 0);
                        canvas.resetViewZoom();
                        BufferedImage target = canvas.getActiveLayerImage();
                        Graphics2D g = target.createGraphics();
                        g.drawImage(fractalImage, 0, 0, null);
                        g.dispose();
                        canvas.repaint();
                        canvas.getLayerManager().fireChange();
                    }
                    infoPanel.updateInfoLabels(renderer);
                    JLabel pl = infoPanel.getProgressLabel();
                    if (pl != null) {
                        long elapsed = System.currentTimeMillis() - infoPanel.getRenderStartTime();
                        pl.setText("Done " + FractalInfoPanel.formatElapsedShort(elapsed));
                    }
                } catch (Exception ignored) {}
            }
        };
        currentWorker.execute();
    }

    public void cancelRender() {
        renderer.cancelRender();
        if (currentWorker != null) currentWorker.cancel(true);
        infoPanel.stopProgressTimer();
        setRenderButtonIdle();
        JLabel pl = infoPanel.getProgressLabel();
        if (pl != null) pl.setText("Cancelled");
    }

    public void cancelWorkerOnly() {
        if (currentWorker != null && !currentWorker.isDone()) {
            renderer.cancelRender();
            currentWorker.cancel(true);
        }
    }

    // --- Render button ---

    private void setRenderButtonCancel() {
        if (renderBtn != null) {
            renderBtn.setText("Cancel");
            renderBtn.setForeground(Color.RED);
        }
    }

    private void setRenderButtonIdle() {
        if (renderBtn != null) {
            renderBtn.setText("Render");
            renderBtn.setForeground(UIManager.getColor("Button.foreground"));
        }
    }

    // --- Color listener ---

    public void registerColorListener(DrawingCanvas canvas) {
        if (registeredColorPicker != null && colorListener != null) {
            registeredColorPicker.removeColorPropertyChangeListener(colorListener);
        }

        ColorPicker picker = canvas.getColorPicker();
        if (picker == null) return;

        colorListener = evt -> {
            if (!active) return;
            if (!ColorPicker.PROP_FOREGROUND_COLOR.equals(evt.getPropertyName())) return;
            Color newColor = (Color) evt.getNewValue();
            ColorGradient newGrad = ColorGradient.fromBaseColor(newColor);
            getGradient().copyFrom(newGrad);
            if (gradientPreview != null) gradientPreview.repaint();
            if (gradientToolbar != null) gradientToolbar.repaint();
            triggerRender();
        };
        picker.addColorPropertyChangeListener(colorListener);
        registeredColorPicker = picker;
    }

    /** Called when the gradient is modified externally (e.g. from the gradient toolbar). */
    public void onGradientChanged() {
        if (gradientPreview != null) gradientPreview.repaint();
        triggerRender();
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.ColorPicker;
import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.FractalTypeRegistry;
import com.seanick80.drawingapp.fractal.TerrainViewer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientEditorDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Fractal explorer tool. Coordinates rendering, mouse interaction,
 * and UI panel assembly by delegating to specialized components.
 */
public class FractalTool implements Tool {

    private static final double INITIAL_RANGE = 4.0;

    private final FractalRenderer renderer = new FractalRenderer();
    private final FractalLocationManager locationManager = new FractalLocationManager();
    private final FractalAnimationController animationController = new FractalAnimationController();
    private final FractalInfoPanel infoPanel = new FractalInfoPanel();

    private ColorGradient gradient;
    private SwingWorker<BufferedImage, Integer> currentWorker;
    private JComboBox<String> typeCombo;
    private JSpinner iterSpinner;
    private JButton renderBtn;
    private BufferedImage lastImage;
    private DrawingCanvas lastCanvas;
    private boolean active;

    private JPanel gradientPreview;
    private PropertyChangeListener colorListener;
    private ColorPicker registeredColorPicker;

    // Pan state
    private int dragStartX, dragStartY;
    private boolean dragging;
    private static final int DRAG_THRESHOLD = 5;

    public FractalTool() {
        gradient = ColorGradient.fractalDefault();
    }

    public static void setDefaultLocationDirectory(File dir) {
        FractalLocationManager.setDefaultLocationDirectory(dir);
    }

    public static File getDefaultLocationDirectory() {
        return FractalLocationManager.getDefaultLocationDirectory();
    }

    public FractalRenderer getRenderer() { return renderer; }
    public ColorGradient getGradient() { return gradient; }

    @Override
    public String getName() { return "Fractal"; }

    @Override
    public int getDefaultStrokeSize() { return 1; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Fractal type selector
        JLabel typeLabel = new JLabel("Type:");
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 11f));
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(typeLabel);

        var registry = FractalTypeRegistry.getDefault();
        String[] typeNames = registry.getAll().stream()
            .map(FractalType::name).toArray(String[]::new);
        typeCombo = new JComboBox<>(typeNames);
        typeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        typeCombo.setMaximumSize(new Dimension(120, 28));
        typeCombo.setFont(typeCombo.getFont().deriveFont(10f));
        typeCombo.addActionListener(e -> {
            String name = (String) typeCombo.getSelectedItem();
            FractalType ft = FractalType.valueOf(name);
            if (ft != null) {
                renderer.setType(ft);
                renderer.setBounds(-2, 2, -2, 2);
                renderer.setMaxIterations(256);
                if (iterSpinner != null) iterSpinner.setValue(256);
                if (lastImage != null && lastCanvas != null) renderAsync(lastImage, lastCanvas);
            }
        });
        panel.add(typeCombo);
        panel.add(Box.createVerticalStrut(4));

        // Iterations
        JLabel iterLabel = new JLabel("Iterations:");
        iterLabel.setFont(iterLabel.getFont().deriveFont(Font.BOLD, 11f));
        iterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(iterLabel);
        iterSpinner = new JSpinner(new SpinnerNumberModel(256, 1, 100000, 50));
        iterSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        iterSpinner.setMaximumSize(new Dimension(120, 28));
        iterSpinner.setFont(iterSpinner.getFont().deriveFont(10f));
        iterSpinner.addChangeListener(e ->
            renderer.setMaxIterations((int) iterSpinner.getValue()));
        panel.add(iterSpinner);
        panel.add(Box.createVerticalStrut(8));

        // Gradient preview
        JLabel gradLabel = new JLabel("Gradient:");
        gradLabel.setFont(gradLabel.getFont().deriveFont(Font.BOLD, 11f));
        gradLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(gradLabel);

        gradientPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth(), h = getHeight();
                Color[] colors = gradient.toColors(w);
                for (int x = 0; x < w; x++) {
                    g.setColor(colors[x]);
                    g.drawLine(x, 0, x, h);
                }
            }
        };
        gradientPreview.setPreferredSize(new Dimension(120, 20));
        gradientPreview.setMaximumSize(new Dimension(120, 20));
        gradientPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(gradientPreview);
        panel.add(Box.createVerticalStrut(4));

        JButton editGradBtn = new JButton("Edit Gradient...");
        editGradBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        editGradBtn.setMaximumSize(new Dimension(120, 28));
        editGradBtn.setFont(editGradBtn.getFont().deriveFont(10f));
        editGradBtn.addActionListener(e -> {
            ColorGradient result = GradientEditorDialog.showDialog(
                SwingUtilities.getWindowAncestor(panel), gradient);
            if (result != null) {
                gradient = result;
                gradientPreview.repaint();
            }
        });
        panel.add(editGradBtn);
        panel.add(Box.createVerticalStrut(12));

        // Save / Load location
        JLabel locLabel = new JLabel("Location:");
        locLabel.setFont(locLabel.getFont().deriveFont(Font.BOLD, 11f));
        locLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(locLabel);

        JButton saveBtn = new JButton("Save Location...");
        saveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveBtn.setMaximumSize(new Dimension(120, 28));
        saveBtn.setFont(saveBtn.getFont().deriveFont(10f));
        saveBtn.addActionListener(e -> locationManager.saveLocation(renderer, panel));
        panel.add(saveBtn);
        panel.add(Box.createVerticalStrut(2));

        JButton loadBtn = new JButton("Load Location...");
        loadBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadBtn.setMaximumSize(new Dimension(120, 28));
        loadBtn.setFont(loadBtn.getFont().deriveFont(10f));
        loadBtn.addActionListener(e -> locationManager.loadLocation(
                renderer, panel, typeCombo, iterSpinner,
                () -> { infoPanel.updateInfoLabels(renderer); triggerRender(); }));
        panel.add(loadBtn);
        panel.add(Box.createVerticalStrut(8));

        // Action buttons
        renderBtn = new JButton("Render");
        renderBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderBtn.setMaximumSize(new Dimension(120, 28));
        renderBtn.setFont(renderBtn.getFont().deriveFont(10f));
        renderBtn.addActionListener(e -> {
            if (isRendering()) {
                renderer.cancelRender();
                currentWorker.cancel(true);
                infoPanel.stopProgressTimer();
                setRenderButtonIdle();
                JLabel pl = infoPanel.getProgressLabel();
                if (pl != null) pl.setText("Cancelled");
            } else {
                triggerRender();
            }
        });
        panel.add(renderBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton luckyBtn = new JButton("I Feel Lucky");
        luckyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        luckyBtn.setMaximumSize(new Dimension(120, 28));
        luckyBtn.setFont(luckyBtn.getFont().deriveFont(10f));
        luckyBtn.addActionListener(e -> feelLucky());
        panel.add(luckyBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton zoomAnimBtn = new JButton("Zoom Movie...");
        zoomAnimBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        zoomAnimBtn.setMaximumSize(new Dimension(120, 28));
        zoomAnimBtn.setFont(zoomAnimBtn.getFont().deriveFont(10f));
        zoomAnimBtn.addActionListener(e ->
                animationController.startZoomAnimation(renderer, gradient,
                        lastImage, locationManager.getLastDirectory(),
                        infoPanel.getProgressLabel(), panel));
        panel.add(zoomAnimBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton flyoverBtn = new JButton("3D Flyover");
        flyoverBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        flyoverBtn.setMaximumSize(new Dimension(120, 28));
        flyoverBtn.setFont(flyoverBtn.getFont().deriveFont(10f));
        flyoverBtn.addActionListener(e ->
                TerrainViewer.openFromRenderer(renderer, gradient));
        panel.add(flyoverBtn);
        panel.add(Box.createVerticalStrut(12));

        // Info labels
        infoPanel.addLabelsTo(panel);

        return panel;
    }

    private void triggerRender() {
        if (lastImage != null && lastCanvas != null) {
            renderAsync(lastImage, lastCanvas);
        }
    }

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        active = true;
        lastImage = image;
        lastCanvas = canvas;
        registerColorListener(canvas);
        renderAsync(image, canvas);
    }

    public void onDeactivated() {
        active = false;
    }

    private void registerColorListener(DrawingCanvas canvas) {
        if (registeredColorPicker != null && colorListener != null) {
            registeredColorPicker.removeColorPropertyChangeListener(colorListener);
        }

        ColorPicker picker = canvas.getColorPicker();
        if (picker == null) return;

        colorListener = evt -> {
            if (!active) return;
            if (!ColorPicker.PROP_FOREGROUND_COLOR.equals(evt.getPropertyName())) return;
            Color newColor = (Color) evt.getNewValue();
            gradient = ColorGradient.fromBaseColor(newColor);
            if (gradientPreview != null) gradientPreview.repaint();
            triggerRender();
        };
        picker.addColorPropertyChangeListener(colorListener);
        registeredColorPicker = picker;
    }

    // --- Rendering ---

    private boolean isRendering() {
        return currentWorker != null && !currentWorker.isDone();
    }

    private void renderAsync(BufferedImage image, DrawingCanvas canvas) {
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

        currentWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return renderer.render(w, h, gradient);
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
                        Graphics2D g = image.createGraphics();
                        g.drawImage(fractalImage, 0, 0, null);
                        g.dispose();
                        canvas.repaint();
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

    // --- I Feel Lucky ---

    private void feelLucky() {
        new SwingWorker<double[], Void>() {
            @Override
            protected double[] doInBackground() {
                return FractalAnimationController.findInterestingLocation();
            }

            @Override
            protected void done() {
                try {
                    double[] loc = get();
                    if (loc == null) return;
                    double centerR = loc[0], centerI = loc[1], halfSpan = loc[2];
                    if (typeCombo != null) typeCombo.setSelectedItem("MANDELBROT");
                    if (iterSpinner != null) iterSpinner.setValue(256);
                    renderer.setBounds(
                        centerR - halfSpan, centerR + halfSpan,
                        centerI - halfSpan, centerI + halfSpan
                    );
                    infoPanel.updateInfoLabels(renderer);
                    triggerRender();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // --- Mouse interaction ---

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastImage = image;
        lastCanvas = canvas;
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
            if (currentWorker != null && !currentWorker.isDone()) {
                renderer.cancelRender();
                currentWorker.cancel(true);
            }
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

            lastImage = image;
            lastCanvas = canvas;
            renderAsync(image, canvas);
            return;
        }

        // No drag — zoom click
        lastImage = image;
        lastCanvas = canvas;
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

        renderAsync(image, canvas);
    }

    @Override
    public void mouseWheelMoved(BufferedImage image, int x, int y, int wheelRotation, DrawingCanvas canvas) {
        // Ctrl+scroll = fractal zoom, plain scroll = image zoom (handled by canvas)
    }

    @Override
    public void drawPreview(Graphics2D g) {}

    // --- Location loading (used by DrawingApp menu) ---

    public void loadLocationFile(File file) {
        locationManager.loadLocationFile(renderer, file, typeCombo, iterSpinner,
                () -> { infoPanel.updateInfoLabels(renderer); triggerRender(); });
    }
}

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.ColorPicker;
import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.JuliaType;
import com.seanick80.drawingapp.fractal.FractalTypeRegistry;
import com.seanick80.drawingapp.fractal.ZoomAnimator;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientEditorDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class FractalTool implements Tool {

    private static final double INITIAL_RANGE = 4.0; // default bounds: -2 to 2

    private final FractalRenderer renderer = new FractalRenderer();
    private ColorGradient gradient;
    private SwingWorker<BufferedImage, Integer> currentWorker;
    private javax.swing.Timer progressTimer;
    private JLabel zoomLabel;
    private JLabel timeLabel;
    private JLabel centerLabel;
    private JLabel rangeLabel;
    private JLabel cacheLabel;
    private JLabel progressLabel;
    private long renderStartTime;
    private JComboBox<String> typeCombo;
    private JSpinner iterSpinner;
    private JButton renderBtn;
    private File lastDirectory;
    private BufferedImage lastImage;
    private DrawingCanvas lastCanvas;

    private JPanel gradientPreview;
    private PropertyChangeListener colorListener;
    private ColorPicker registeredColorPicker;

    // Pan state
    private int dragStartX, dragStartY;
    private boolean dragging;
    private static final int DRAG_THRESHOLD = 5; // pixels before drag starts

    public FractalTool() {
        gradient = ColorGradient.fractalDefault();
    }

    @Override public String getName() { return "Fractal"; }

    public FractalRenderer getRenderer() { return renderer; }
    public ColorGradient getGradient() { return gradient; }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Fractal type selector
        JLabel typeLabel = new JLabel("Fractal:");
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 11f));
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(typeLabel);

        String[] typeNames = FractalTypeRegistry.getDefault().getNames().toArray(new String[0]);
        typeCombo = new JComboBox<>(typeNames);
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                setText(displayName((String) value));
                return this;
            }
        });
        typeCombo.setMaximumSize(new Dimension(140, 28));
        typeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        typeCombo.addActionListener(e -> {
            FractalType selected = FractalType.valueOf((String) typeCombo.getSelectedItem());
            if (selected != null) renderer.setType(selected);
            renderer.setBounds(-2, 2, -2, 2);
            updateInfoLabels();
        });
        panel.add(typeCombo);
        panel.add(Box.createVerticalStrut(8));

        // Max iterations
        JLabel iterLabel = new JLabel("Iterations:");
        iterLabel.setFont(iterLabel.getFont().deriveFont(Font.BOLD, 11f));
        iterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(iterLabel);

        iterSpinner = new JSpinner(new SpinnerNumberModel(256, 10, 10000, 50));
        iterSpinner.setMaximumSize(new Dimension(120, 28));
        iterSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        iterSpinner.addChangeListener(e -> renderer.setMaxIterations((int) iterSpinner.getValue()));
        panel.add(iterSpinner);
        panel.add(Box.createVerticalStrut(8));

        // Gradient preview
        JLabel gradLabel = new JLabel("Colors:");
        gradLabel.setFont(gradLabel.getFont().deriveFont(Font.BOLD, 11f));
        gradLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(gradLabel);

        gradientPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth();
                for (int px = 0; px < w; px++) {
                    float t = (float) px / Math.max(1, w - 1);
                    g.setColor(gradient.getColorAt(t));
                    g.drawLine(px, 0, px, getHeight());
                }
            }
        };
        gradientPreview.setPreferredSize(new Dimension(120, 20));
        gradientPreview.setMinimumSize(new Dimension(120, 20));
        gradientPreview.setMaximumSize(new Dimension(120, 20));
        gradientPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        gradientPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.add(gradientPreview);
        panel.add(Box.createVerticalStrut(2));

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
        saveBtn.addActionListener(e -> saveLocation(panel));
        panel.add(saveBtn);
        panel.add(Box.createVerticalStrut(2));

        JButton loadBtn = new JButton("Load Location...");
        loadBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadBtn.setMaximumSize(new Dimension(120, 28));
        loadBtn.setFont(loadBtn.getFont().deriveFont(10f));
        loadBtn.addActionListener(e -> loadLocation(panel));
        panel.add(loadBtn);
        panel.add(Box.createVerticalStrut(8));

        renderBtn = new JButton("Render");
        renderBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderBtn.setMaximumSize(new Dimension(120, 28));
        renderBtn.setFont(renderBtn.getFont().deriveFont(10f));
        renderBtn.addActionListener(e -> {
            if (isRendering()) {
                renderer.cancelRender();
                currentWorker.cancel(true);
                stopProgressTimer();
                setRenderButtonIdle();
                if (progressLabel != null) progressLabel.setText("Cancelled");
            } else if (lastImage != null && lastCanvas != null) {
                renderAsync(lastImage, lastCanvas);
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
        zoomAnimBtn.addActionListener(e -> startZoomAnimation(panel));
        panel.add(zoomAnimBtn);
        panel.add(Box.createVerticalStrut(12));

        // Zoom / position info
        JLabel infoLabel = new JLabel("View:");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.BOLD, 11f));
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(infoLabel);

        Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 10);

        zoomLabel = new JLabel();
        zoomLabel.setFont(monoFont);
        zoomLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(zoomLabel);

        timeLabel = new JLabel(" ");
        timeLabel.setFont(monoFont);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(timeLabel);

        centerLabel = new JLabel();
        centerLabel.setFont(monoFont);
        centerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(centerLabel);

        rangeLabel = new JLabel();
        rangeLabel.setFont(monoFont);
        rangeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(rangeLabel);

        cacheLabel = new JLabel();
        cacheLabel.setFont(monoFont);
        cacheLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(cacheLabel);

        progressLabel = new JLabel(" ");
        progressLabel.setFont(monoFont);
        progressLabel.setForeground(new Color(0, 100, 200));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(progressLabel);

        updateInfoLabels();

        return panel;
    }

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        lastImage = image;
        lastCanvas = canvas;
        registerColorListener(canvas);
        renderAsync(image, canvas);
    }

    private void registerColorListener(DrawingCanvas canvas) {
        // Remove previous listener if any
        if (registeredColorPicker != null && colorListener != null) {
            registeredColorPicker.removeColorPropertyChangeListener(colorListener);
        }

        ColorPicker picker = canvas.getColorPicker();
        if (picker == null) return;

        colorListener = evt -> {
            if (!ColorPicker.PROP_FOREGROUND_COLOR.equals(evt.getPropertyName())) return;
            Color newColor = (Color) evt.getNewValue();
            gradient = ColorGradient.fromBaseColor(newColor);
            if (gradientPreview != null) {
                gradientPreview.repaint();
            }
            if (lastImage != null && lastCanvas != null) {
                renderAsync(lastImage, lastCanvas);
            }
        };
        picker.addColorPropertyChangeListener(colorListener);
        registeredColorPicker = picker;
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastImage = image;
        lastCanvas = canvas;
        dragStartX = x;
        dragStartY = y;
        dragging = false;
    }

    @Override
    public void mouseWheelMoved(BufferedImage image, int x, int y, int wheelRotation, DrawingCanvas canvas) {

        int w = image.getWidth();
        int h = image.getHeight();

        BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
        BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
        MathContext mc = getZoomMathContext(minR, maxR, minI, maxI);
        BigDecimal rangeR = maxR.subtract(minR, mc);
        BigDecimal rangeI = maxI.subtract(minI, mc);

        // Map mouse position to complex coordinate
        BigDecimal xFrac = new BigDecimal(x).divide(new BigDecimal(w), mc);
        BigDecimal yFrac = new BigDecimal(y).divide(new BigDecimal(h), mc);
        BigDecimal clickReal = minR.add(xFrac.multiply(rangeR, mc), mc);
        BigDecimal clickImag = minI.add(yFrac.multiply(rangeI, mc), mc);

        // Scroll up (negative) = zoom in, scroll down (positive) = zoom out
        BigDecimal zoomFactor = (wheelRotation < 0)
            ? new BigDecimal("0.8") : new BigDecimal("1.25");
        BigDecimal newRangeR = rangeR.multiply(zoomFactor, mc);
        BigDecimal newRangeI = rangeI.multiply(zoomFactor, mc);
        BigDecimal two = new BigDecimal(2);

        renderer.setBounds(
            clickReal.subtract(newRangeR.divide(two, mc), mc),
            clickReal.add(newRangeR.divide(two, mc), mc),
            clickImag.subtract(newRangeI.divide(two, mc), mc),
            clickImag.add(newRangeI.divide(two, mc), mc)
        );

        renderAsync(image, canvas);
    }

    private boolean isRendering() {
        return currentWorker != null && !currentWorker.isDone();
    }

    private void setRenderButtonActive() {
        if (renderBtn != null) {
            renderBtn.setText("Cancel Render");
            renderBtn.setForeground(new Color(200, 40, 40));
        }
    }

    private void setRenderButtonIdle() {
        if (renderBtn != null) {
            renderBtn.setText("Render");
            renderBtn.setForeground(null);
        }
    }

    private void renderAsync(BufferedImage image, DrawingCanvas canvas) {
        // Always cancel any in-progress render before starting a new one
        if (currentWorker != null && !currentWorker.isDone()) {
            renderer.cancelRender();
            currentWorker.cancel(true);
        }
        stopProgressTimer();

        renderStartTime = System.currentTimeMillis();
        int w = image.getWidth();
        int h = image.getHeight();
        if (progressLabel != null) progressLabel.setText("Rendering...");
        setRenderButtonActive();

        // Start progress polling timer (200ms interval)
        startProgressTimer(w, h);

        currentWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return renderer.render(w, h, gradient);
            }

            @Override
            protected void done() {
                stopProgressTimer();
                setRenderButtonIdle();
                if (isCancelled()) {
                    if (progressLabel != null) progressLabel.setText("Cancelled");
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
                    updateInfoLabels();
                    long elapsed = System.currentTimeMillis() - renderStartTime;
                    if (progressLabel != null) {
                        progressLabel.setText("Done " + formatElapsedShort(elapsed));
                    }
                } catch (Exception ignored) {}
            }
        };
        currentWorker.execute();
    }

    private void startProgressTimer(int width, int height) {
        progressTimer = new javax.swing.Timer(200, e -> {
            if (progressLabel == null) return;
            long elapsed = System.currentTimeMillis() - renderStartTime;

            // Blit progressive RGB data to canvas for real-time feedback
            int[] progRgb = renderer.getProgressiveRgb();
            if (progRgb != null && lastImage != null && lastCanvas != null) {
                try {
                    lastImage.setRGB(0, 0, width, height, progRgb, 0, width);
                    lastCanvas.repaint();
                } catch (Exception ignored) {}
            }

            double progress = renderer.getBigDecimalProgress();
            if (progress > 0 && progress < 1.0) {
                int pct = (int) (progress * 100);
                int completedRows = (int) (progress * height);
                String eta = "";
                if (progress > 0.05) {
                    long remaining = (long) (elapsed / progress * (1.0 - progress));
                    eta = " ETA " + formatElapsedShort(remaining);
                }
                progressLabel.setText(String.format("Rendering: %d%% (%d/%d)%s",
                        pct, completedRows, height, eta));
            } else if (progress <= 0) {
                progressLabel.setText("Rendering... " + formatElapsedShort(elapsed));
            }
        });
        progressTimer.start();
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
    }

    private static String formatElapsedShort(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("%.1fs", sec);
        int min = (int) (sec / 60);
        double remSec = sec - min * 60;
        return String.format("%dm%.0fs", min, remSec);
    }

    private static String formatElapsed(long ms) {
        if (ms < 1000) return String.format("Time: %dms", ms);
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("Time: %.1fs", sec);
        int min = (int) (sec / 60);
        double remSec = sec - min * 60;
        return String.format("Time: %dm %.1fs", min, remSec);
    }

    private void updateInfoLabels() {
        if (zoomLabel == null) return;
        long elapsed = System.currentTimeMillis() - renderStartTime;
        if (timeLabel != null) {
            timeLabel.setText(formatElapsed(elapsed));
        }
        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double rangeRd = rangeR.doubleValue();
        double rangeId = rangeI.doubleValue();
        double zoom = INITIAL_RANGE / Math.min(rangeRd, rangeId);

        boolean bigDecimalMode = renderer.isLastRenderBigDecimal();

        String modeTag = bigDecimalMode ? " [BigDecimal]" : " [double]";
        zoomLabel.setText(String.format("Zoom: %.2e%s", zoom, modeTag));

        // Show center with appropriate precision
        MathContext mc = getZoomMathContext(renderer.getMinRealBig(), renderer.getMaxRealBig(),
                                            renderer.getMinImagBig(), renderer.getMaxImagBig());
        BigDecimal two = new BigDecimal(2);
        BigDecimal centerR = renderer.getMinRealBig().add(renderer.getMaxRealBig(), mc).divide(two, mc);
        BigDecimal centerI = renderer.getMinImagBig().add(renderer.getMaxImagBig(), mc).divide(two, mc);

        centerLabel.setText("Re: " + centerR.toPlainString());
        rangeLabel.setText("Im: " + centerI.toPlainString());

        var cache = renderer.getCache();
        if (bigDecimalMode) {
            int prevHits = renderer.getPrevRenderCacheHits();
            if (prevHits > 0) {
                cacheLabel.setText(String.format("Cache: %dk reused", prevHits / 1000));
            } else {
                cacheLabel.setText("Cache: off (BigDecimal)");
            }
        } else {
            int lookups = cache.getLookups();
            if (lookups > 0) {
                double hitRate = 100.0 * cache.getHits() / lookups;
                cacheLabel.setText(String.format("Cache: %dk %.0f%%", cache.size() / 1000, hitRate));
            } else {
                cacheLabel.setText(String.format("Cache: %dk", cache.size() / 1000));
            }
        }

        // In BigDecimal mode, no warning needed — precision is sufficient
        if (bigDecimalMode) {
            zoomLabel.setForeground(new Color(0, 128, 0));
            zoomLabel.setToolTipText("BigDecimal mode: arbitrary precision active");
        } else if (rangeRd < 1e-13 || rangeId < 1e-13) {
            zoomLabel.setForeground(Color.RED);
            zoomLabel.setToolTipText("Near double precision limit!");
        } else {
            zoomLabel.setForeground(UIManager.getColor("Label.foreground"));
            zoomLabel.setToolTipText(null);
        }
    }

    private static MathContext getZoomMathContext(BigDecimal minR, BigDecimal maxR,
                                                  BigDecimal minI, BigDecimal maxI) {
        double rangeR = maxR.subtract(minR).abs().doubleValue();
        double rangeI = maxI.subtract(minI).abs().doubleValue();
        double minRange = Math.min(rangeR, rangeI);
        double zoom = (minRange > 0) ? 4.0 / minRange : 1;
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        return new MathContext(precision, RoundingMode.HALF_UP);
    }

    // --- I Feel Lucky ---

    private void feelLucky() {
        new SwingWorker<double[], Void>() {
            @Override
            protected double[] doInBackground() {
                return findInterestingLocation();
            }

            @Override
            protected void done() {
                try {
                    double[] loc = get();
                    if (loc == null) return;
                    double centerR = loc[0], centerI = loc[1], halfSpan = loc[2];

                    // Update UI first (triggers listeners that reset bounds)
                    if (typeCombo != null) typeCombo.setSelectedItem("MANDELBROT");
                    if (iterSpinner != null) iterSpinner.setValue(256);

                    // Set bounds AFTER combo/spinner listeners have fired
                    renderer.setBounds(
                        centerR - halfSpan, centerR + halfSpan,
                        centerI - halfSpan, centerI + halfSpan
                    );
                    updateInfoLabels();

                    if (lastImage != null && lastCanvas != null) {
                        renderAsync(lastImage, lastCanvas);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    /**
     * Searches for a random interesting Mandelbrot location by sampling points
     * and checking for varied iteration counts (a sign of boundary detail).
     * Returns {centerReal, centerImag, halfSpan} or null if nothing found.
     */
    private double[] findInterestingLocation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        FractalType mandelbrot = FractalType.MANDELBROT;
        int maxIter = 256;

        // Sample offsets: corners + edge midpoints of a unit square [-1,1]
        double[][] offsets = {
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1},  // corners
            {0, -1}, {0, 1}, {-1, 0}, {1, 0}       // midpoints
        };

        for (int attempt = 0; attempt < 1000; attempt++) {
            double centerR = rng.nextDouble(-2.0, 0.5);
            double centerI = rng.nextDouble(-1.2, 1.2);
            double exponent = rng.nextDouble(2.0, 8.0);
            double halfSpan = Math.pow(10, -exponent) / 2.0;

            int[] iters = new int[8];
            int distinctCount = 0;
            boolean hasInterior = false;
            boolean hasEscape = false;
            boolean allTrivial = true;

            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            for (int s = 0; s < 8; s++) {
                double px = centerR + offsets[s][0] * halfSpan;
                double py = centerI + offsets[s][1] * halfSpan;
                iters[s] = mandelbrot.iterate(px, py, maxIter);

                seen.add(iters[s]);
                if (iters[s] >= maxIter) hasInterior = true;
                if (iters[s] < maxIter) hasEscape = true;
                if (iters[s] >= 5) allTrivial = false;
            }

            distinctCount = seen.size();

            if (distinctCount >= 4 && hasInterior && hasEscape && !allTrivial) {
                return new double[] { centerR, centerI, halfSpan };
            }
        }
        return null; // unlikely
    }

    // --- Zoom Animation ---

    private void startZoomAnimation(Component parent) {
        Window window = SwingUtilities.getWindowAncestor(parent);

        // Current viewport info for "current location" option
        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double currentRange = Math.min(rangeR.doubleValue(), rangeI.doubleValue());
        double currentZoom = INITIAL_RANGE / currentRange;
        MathContext mc = getZoomMathContext(renderer.getMinRealBig(), renderer.getMaxRealBig(),
                                            renderer.getMinImagBig(), renderer.getMaxImagBig());
        BigDecimal two = new BigDecimal(2);
        BigDecimal curCenterR = renderer.getMinRealBig().add(renderer.getMaxRealBig(), mc).divide(two, mc);
        BigDecimal curCenterI = renderer.getMinImagBig().add(renderer.getMaxImagBig(), mc).divide(two, mc);

        // Step 1: Build candidate list — current viewport + auto-discovered points
        if (progressLabel != null) progressLabel.setText("Scanning for interesting points...");
        List<ZoomAnimator.ZoomTarget> autoTargets = ZoomAnimator.findInterestingPoints(
                renderer.getType(), 512, 3);

        // Create current-viewport preview
        BufferedImage curPreview = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        if (lastImage != null) {
            java.awt.Graphics2D g = curPreview.createGraphics();
            g.drawImage(lastImage, 0, 0, 120, 90, null);
            g.dispose();
        }
        ZoomAnimator.ZoomTarget currentTarget = new ZoomAnimator.ZoomTarget(
                curCenterR, curCenterI, Double.MAX_VALUE, curPreview);

        List<ZoomAnimator.ZoomTarget> allTargets = new ArrayList<>();
        allTargets.add(currentTarget);
        allTargets.addAll(autoTargets);

        // Step 2: Show target picker dialog with previews
        JPanel pickerPanel = new JPanel(new java.awt.BorderLayout(8, 8));
        pickerPanel.add(new JLabel("Select a zoom target:"), java.awt.BorderLayout.NORTH);

        JPanel thumbPanel = new JPanel(new java.awt.GridLayout(1, allTargets.size(), 8, 8));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        JRadioButton[] buttons = new JRadioButton[allTargets.size()];
        for (int i = 0; i < allTargets.size(); i++) {
            ZoomAnimator.ZoomTarget t = allTargets.get(i);
            JPanel card = new JPanel(new java.awt.BorderLayout(4, 4));
            card.add(new JLabel(new ImageIcon(t.preview)), java.awt.BorderLayout.CENTER);
            if (i == 0) {
                buttons[i] = new JRadioButton("Current view");
            } else {
                buttons[i] = new JRadioButton(String.format("Auto #%d", i));
            }
            if (i == 0) buttons[i].setSelected(true);
            group.add(buttons[i]);
            card.add(buttons[i], java.awt.BorderLayout.SOUTH);
            thumbPanel.add(card);
        }
        pickerPanel.add(thumbPanel, java.awt.BorderLayout.CENTER);

        // Settings panel below thumbnails
        JTextField framesField = new JTextField("120", 5);
        JTextField fpsField = new JTextField("30", 5);
        JTextField zoomField = new JTextField(
            String.valueOf((long) Math.max(currentZoom * 10, 10000)), 8);
        JTextField widthField = new JTextField(
            lastImage != null ? String.valueOf(lastImage.getWidth()) : "640", 5);
        JTextField heightField = new JTextField(
            lastImage != null ? String.valueOf(lastImage.getHeight()) : "480", 5);

        JPanel settingsPanel = new JPanel(new java.awt.GridLayout(5, 2, 4, 4));
        settingsPanel.add(new JLabel("Total frames:"));
        settingsPanel.add(framesField);
        settingsPanel.add(new JLabel("FPS:"));
        settingsPanel.add(fpsField);
        settingsPanel.add(new JLabel("Final zoom:"));
        settingsPanel.add(zoomField);
        settingsPanel.add(new JLabel("Frame width:"));
        settingsPanel.add(widthField);
        settingsPanel.add(new JLabel("Frame height:"));
        settingsPanel.add(heightField);
        pickerPanel.add(settingsPanel, java.awt.BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(window, pickerPanel,
                "Zoom Movie — Pick Target", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        // Find which target was selected
        int selectedIdx = 0;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].isSelected()) { selectedIdx = i; break; }
        }
        ZoomAnimator.ZoomTarget target = allTargets.get(selectedIdx);

        int totalFrames;
        int fpsVal;
        double finalZoom;
        int frameW, frameH;
        try {
            totalFrames = Integer.parseInt(framesField.getText().trim());
            fpsVal = Integer.parseInt(fpsField.getText().trim());
            finalZoom = Double.parseDouble(zoomField.getText().trim());
            frameW = Integer.parseInt(widthField.getText().trim());
            frameH = Integer.parseInt(heightField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(window, "Invalid number format",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 3: Choose output directory
        JFileChooser fc = new JFileChooser(lastDirectory != null ? lastDirectory : new File("."));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select output directory for zoom movie");
        if (fc.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return;
        File outputDir = fc.getSelectedFile();
        lastDirectory = outputDir;

        // Step 4: Build keyframes — fixed center at target, zoom 1 → finalZoom
        // Fixed center is the standard technique: at zoom=1 the full set is
        // visible (slightly offset), and every frame shows boundary detail.
        // Panning across the set while zooming always hits the black interior.
        int movieIter = ZoomAnimator.iterationsForZoom(finalZoom, 256);

        FractalRenderer animRenderer = new FractalRenderer();
        animRenderer.setType(renderer.getType());
        animRenderer.setRenderMode(FractalRenderer.RenderMode.AUTO);

        ZoomAnimator animator = new ZoomAnimator(animRenderer, gradient);
        animator.setSize(frameW, frameH);
        animator.setFramesPerSegment(totalFrames - 1);
        animator.setFps(fpsVal);
        // Both keyframes use the TARGET center
        animator.addKeyframe(new ZoomAnimator.Keyframe(
                target.centerReal, target.centerImag,
                BigDecimal.ONE, movieIter));
        animator.addKeyframe(new ZoomAnimator.Keyframe(
                target.centerReal, target.centerImag,
                new BigDecimal(Double.toString(finalZoom)), movieIter));

        // Step 5: Render in background
        if (progressLabel != null) progressLabel.setText("Rendering zoom movie...");

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return animator.renderToFiles(outputDir, (frame, total, ms) -> {
                    publish(String.format("Frame %d/%d (%dms)", frame + 1, total, ms));
                });
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (progressLabel != null && !chunks.isEmpty()) {
                    progressLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    if (progressLabel != null) {
                        progressLabel.setText("Zoom movie: " + count + " frames saved");
                    }
                    JOptionPane.showMessageDialog(window,
                            count + " frames + zoom.avi saved to:\n" + outputDir.getAbsolutePath(),
                            "Zoom Movie Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    if (progressLabel != null) progressLabel.setText("Zoom movie failed");
                    JOptionPane.showMessageDialog(window,
                            "Error: " + e.getMessage(), "Zoom Movie Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // --- Save / Load location ---

    private void saveLocation(Component parent) {
        JFileChooser fc = createFileChooser();
        if (fc.showSaveDialog(SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        lastDirectory = file.getParentFile();
        if (!file.getName().contains(".")) file = new File(file.getPath() + ".json");

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", renderer.getType().name());
        data.put("minReal", renderer.getMinRealBig().toPlainString());
        data.put("maxReal", renderer.getMaxRealBig().toPlainString());
        data.put("minImag", renderer.getMinImagBig().toPlainString());
        data.put("maxImag", renderer.getMaxImagBig().toPlainString());
        data.put("maxIterations", String.valueOf(renderer.getMaxIterations()));
        data.put("juliaReal", renderer.getJuliaReal() + "");
        data.put("juliaImag", renderer.getJuliaImag() + "");

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("{\n");
            int i = 0;
            for (var entry : data.entrySet()) {
                String value = entry.getValue();
                boolean isNumber = entry.getKey().equals("maxIterations");
                w.write("  \"" + entry.getKey() + "\": ");
                w.write(isNumber ? value : "\"" + value + "\"");
                if (++i < data.size()) w.write(",");
                w.write("\n");
            }
            w.write("}\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parent),
                "Error saving: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadLocation(Component parent) {
        JFileChooser fc = createFileChooser();
        if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        lastDirectory = file.getParentFile();

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Map<String, String> data = parseJson(json);

            String typeName = data.getOrDefault("type", "MANDELBROT");
            FractalType type = FractalType.valueOf(typeName);
            if (type == null) type = FractalType.MANDELBROT;
            renderer.setType(type);
            if (typeCombo != null) {
                typeCombo.setSelectedItem(typeName);
            }

            BigDecimal minR = new BigDecimal(data.get("minReal"));
            BigDecimal maxR = new BigDecimal(data.get("maxReal"));
            BigDecimal minI = new BigDecimal(data.get("minImag"));
            BigDecimal maxI = new BigDecimal(data.get("maxImag"));
            renderer.setBounds(minR, maxR, minI, maxI);

            if (data.containsKey("maxIterations")) {
                int iters = Integer.parseInt(data.get("maxIterations"));
                renderer.setMaxIterations(iters);
                if (iterSpinner != null) iterSpinner.setValue(iters);
            }

            if (type instanceof JuliaType
                    && data.containsKey("juliaReal") && data.containsKey("juliaImag")) {
                renderer.setJuliaConstant(
                    new BigDecimal(data.get("juliaReal")),
                    new BigDecimal(data.get("juliaImag")));
            }

            updateInfoLabels();

            // Trigger a render immediately if we have a canvas
            if (lastImage != null && lastCanvas != null) {
                renderAsync(lastImage, lastCanvas);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parent),
                "Error loading: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser createFileChooser() {
        JFileChooser fc = new JFileChooser(lastDirectory != null ? lastDirectory : new File("."));
        fc.setFileFilter(new FileNameExtensionFilter("Fractal Location (*.json)", "json"));
        return fc;
    }

    /** Convert registry name like "BURNING_SHIP" to display name "Burning Ship". */
    private static String displayName(String registryName) {
        if (registryName == null) return "";
        String[] parts = registryName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static Map<String, String> parseJson(String json) {
        return com.seanick80.drawingapp.fractal.FractalJsonUtil.parseJson(json);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        int dx = x - dragStartX;
        int dy = y - dragStartY;
        if (!dragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
            dragging = true;
            // Cancel any in-progress render when pan starts
            if (currentWorker != null && !currentWorker.isDone()) {
                renderer.cancelRender();
                currentWorker.cancel(true);
            }
        }
        if (!dragging) return;

        // Shift the raster image visually (scale to screen pixels) — no re-render until mouse up
        double vz = canvas.getViewZoom();
        canvas.setPanOffset((int)(dx * vz), (int)(dy * vz));
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (dragging) {
            dragging = false;
            // Don't clear pan offset here — keep image pinned until re-render completes

            // Apply the final pan delta to the viewport
            int dx = x - dragStartX;
            int dy = y - dragStartY;
            int w = image.getWidth();
            int h = image.getHeight();

            BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
            BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
            MathContext mc = getZoomMathContext(minR, maxR, minI, maxI);
            BigDecimal rangeR = maxR.subtract(minR, mc);
            BigDecimal rangeI = maxI.subtract(minI, mc);

            BigDecimal deltaR = rangeR.multiply(BigDecimal.valueOf(dx), mc)
                                      .divide(BigDecimal.valueOf(w), mc).negate();
            BigDecimal deltaI = rangeI.multiply(BigDecimal.valueOf(dy), mc)
                                      .divide(BigDecimal.valueOf(h), mc).negate();

            renderer.setBounds(
                minR.add(deltaR, mc), maxR.add(deltaR, mc),
                minI.add(deltaI, mc), maxI.add(deltaI, mc)
            );

            lastImage = image;
            lastCanvas = canvas;
            renderAsync(image, canvas);
            return;
        }

        // No drag occurred — treat as zoom click
        lastImage = image;
        lastCanvas = canvas;
        int w = image.getWidth();
        int h = image.getHeight();
        int button = canvas.getLastMouseButton();

        if (button == java.awt.event.MouseEvent.BUTTON1 || button == java.awt.event.MouseEvent.BUTTON3) {
            BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
            BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
            MathContext mc = getZoomMathContext(minR, maxR, minI, maxI);
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
                clickImag.add(newRangeI.divide(two, mc), mc)
            );
        }

        renderAsync(image, canvas);
    }

    @Override
    public void drawPreview(Graphics2D g) {}
}

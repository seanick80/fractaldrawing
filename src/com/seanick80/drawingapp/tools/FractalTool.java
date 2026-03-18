package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.FractalTypeRegistry;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientEditorDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class FractalTool implements Tool {

    private static final double INITIAL_RANGE = 4.0; // default bounds: -2 to 2

    private final FractalRenderer renderer = new FractalRenderer();
    private ColorGradient gradient;
    private SwingWorker<BufferedImage, Integer> currentWorker;
    private JLabel zoomLabel;
    private JLabel timeLabel;
    private JLabel centerLabel;
    private JLabel rangeLabel;
    private JLabel cacheLabel;
    private JLabel progressLabel;
    private long renderStartTime;
    private JComboBox<String> typeCombo;
    private JSpinner iterSpinner;
    private File lastDirectory;
    private BufferedImage lastImage;
    private DrawingCanvas lastCanvas;

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

        JPanel gradientPreview = new JPanel() {
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

        JButton renderBtn = new JButton("Render");
        renderBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderBtn.setMaximumSize(new Dimension(120, 28));
        renderBtn.setFont(renderBtn.getFont().deriveFont(10f));
        renderBtn.addActionListener(e -> {
            if (lastImage != null && lastCanvas != null) {
                renderAsync(lastImage, lastCanvas);
            }
        });
        panel.add(renderBtn);
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
        renderAsync(image, canvas);
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastImage = image;
        lastCanvas = canvas;

        int w = image.getWidth();
        int h = image.getHeight();
        int button = canvas.getLastMouseButton();

        // Zoom: compute new bounds centered on click position using BigDecimal
        if (button == java.awt.event.MouseEvent.BUTTON1 || button == java.awt.event.MouseEvent.BUTTON3) {
            BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
            BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
            MathContext mc = getZoomMathContext(minR, maxR, minI, maxI);
            BigDecimal rangeR = maxR.subtract(minR, mc);
            BigDecimal rangeI = maxI.subtract(minI, mc);

            // Map click pixel to complex coordinate
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

    private void renderAsync(BufferedImage image, DrawingCanvas canvas) {
        // Always cancel any in-progress render before starting a new one
        if (currentWorker != null && !currentWorker.isDone()) {
            renderer.cancelRender();
            currentWorker.cancel(true);
        }

        renderStartTime = System.currentTimeMillis();
        int w = image.getWidth();
        int h = image.getHeight();
        if (progressLabel != null) progressLabel.setText(" ");

        currentWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return renderer.render(w, h, gradient);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    BufferedImage fractalImage = get();
                    if (fractalImage != null) {
                        Graphics2D g = image.createGraphics();
                        g.drawImage(fractalImage, 0, 0, null);
                        g.dispose();
                        canvas.repaint();
                    }
                    updateInfoLabels();
                } catch (Exception ignored) {}
            }
        };
        currentWorker.execute();
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
            cacheLabel.setText("Cache: off (BigDecimal)");
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

            if (data.containsKey("juliaReal") && data.containsKey("juliaImag")) {
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
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}
}

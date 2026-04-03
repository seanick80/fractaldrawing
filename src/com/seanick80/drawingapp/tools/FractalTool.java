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
import java.util.Arrays;

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
    private JMenu fractalMenu;
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

    @Override
    public JMenu getMenu() {
        if (fractalMenu == null) {
            fractalMenu = buildFractalMenu();
        }
        return fractalMenu;
    }

    private JMenu buildFractalMenu() {
        JMenu menu = new JMenu("Fractal");
        menu.setMnemonic('R');

        JMenu typeMenu = new JMenu("Type");
        ButtonGroup typeGroup = new ButtonGroup();
        boolean first = true;
        for (FractalType ft : FractalTypeRegistry.getDefault().getAll()) {
            String displayName = formatTypeName(ft.name());
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(displayName, first);
            first = false;
            typeGroup.add(item);
            item.addActionListener(e -> {
                renderer.setType(ft);
                renderer.setBounds(-2, 2, -2, 2);
                if (lastImage != null && lastCanvas != null) {
                    onActivated(lastImage, lastCanvas);
                }
            });
            typeMenu.add(item);
        }
        menu.add(typeMenu);

        JMenu colorMenu = new JMenu("Coloring");
        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButtonMenuItem modItem = new JRadioButtonMenuItem("Mod (cyclic)", true);
        JRadioButtonMenuItem divItem = new JRadioButtonMenuItem("Division (linear)");
        colorGroup.add(modItem);
        colorGroup.add(divItem);

        modItem.addActionListener(e -> {
            renderer.setColorMode(FractalRenderer.ColorMode.MOD);
            triggerRender();
        });
        divItem.addActionListener(e -> {
            renderer.setColorMode(FractalRenderer.ColorMode.DIVISION);
            triggerRender();
        });

        colorMenu.add(modItem);
        colorMenu.add(divItem);
        menu.add(colorMenu);

        JCheckBoxMenuItem pruningItem = new JCheckBoxMenuItem("Interior Pruning", true);
        pruningItem.addActionListener(e -> {
            renderer.setInteriorPruning(pruningItem.isSelected());
            triggerRender();
        });
        menu.add(pruningItem);

        menu.addSeparator();

        JMenuItem flyoverItem = new JMenuItem("3D Flyover");
        flyoverItem.addActionListener(e ->
                TerrainViewer.openFromRenderer(renderer, gradient));
        menu.add(flyoverItem);

        menu.addSeparator();

        JMenu presetsMenu = new JMenu("Locations");

        addPreset(presetsMenu, "Full Mandelbrot", FractalType.MANDELBROT,
            "-2", "2", "-2", "2", 256);
        addPreset(presetsMenu, "Full Julia", FractalType.JULIA,
            "-2", "2", "-2", "2", 256);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Seahorse Valley", FractalType.MANDELBROT,
            "-0.7516", "-0.7346", "0.0534", "0.0661", 256);
        addPreset(presetsMenu, "Mini Mandelbrot", FractalType.MANDELBROT,
            "-1.7692", "-1.7642", "-0.0025", "0.0025", 512);
        addPreset(presetsMenu, "Elephant Valley", FractalType.MANDELBROT,
            "0.2501", "0.2601", "-0.0050", "0.0050", 512);
        addPreset(presetsMenu, "Lightning", FractalType.MANDELBROT,
            "-1.9855", "-1.9775", "-0.0010", "0.0010", 1024);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Deep Zoom (1e13)", FractalType.MANDELBROT,
            "-0.6596578041282916240699130664224003",
            "-0.6596578041281954277657226502133863",
            "-0.4505474984324947231692017068002755",
            "-0.4505474984323985268650112905912615", 456);
        addPreset(presetsMenu, "Deeper Zoom (1e17)", FractalType.MANDELBROT,
            "-0.65965780412826339954936433105672396103",
            "-0.65965780412826338780665141718755782163",
            "-0.45054749843244648813621629936085526501",
            "-0.45054749843244647639350338549168912561", 706);
        addPreset(presetsMenu, "Deepest Zoom (1e18)", FractalType.MANDELBROT,
            "-0.659657804128263429441678542563321007371",
            "-0.659657804128263427973839428329675239951",
            "-0.450547498432446486027726571727316188813",
            "-0.450547498432446484559887457493670421393", 506);

        menu.add(presetsMenu);

        File locDir = FractalLocationManager.getDefaultLocationDirectory();
        if (locDir != null && locDir.isDirectory()) {
            File[] jsonFiles = locDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".json"));
            if (jsonFiles != null && jsonFiles.length > 0) {
                JMenu savedMenu = new JMenu("Saved Locations");
                Arrays.sort(jsonFiles, (a, b) ->
                        a.getName().compareToIgnoreCase(b.getName()));
                for (File jsonFile : jsonFiles) {
                    String label = jsonFile.getName().replaceFirst("\\.json$", "")
                            .replace('_', ' ');
                    JMenuItem item = new JMenuItem(label);
                    item.addActionListener(e -> {
                        loadLocationFile(jsonFile);
                        if (lastImage != null && lastCanvas != null) {
                            onActivated(lastImage, lastCanvas);
                        }
                    });
                    savedMenu.add(item);
                }
                menu.add(savedMenu);
            }
        }

        return menu;
    }

    private void addPreset(JMenu menu, String name, FractalType type,
                           String minR, String maxR, String minI, String maxI, int iterations) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            renderer.setType(type);
            renderer.setBounds(
                new BigDecimal(minR), new BigDecimal(maxR),
                new BigDecimal(minI), new BigDecimal(maxI));
            renderer.setMaxIterations(iterations);
            triggerRender();
        });
        menu.add(item);
    }

    private static String formatTypeName(String registryName) {
        String[] parts = registryName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}

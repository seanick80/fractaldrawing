package com.seanick80.drawingapp.gradient;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.PaletteCycleAnimator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;

import java.io.File;

/**
 * A JPanel containing the gradient editor plus load/save/reset buttons.
 * Designed to be wrapped in a DockablePanel for floating/docking.
 * Supports switching the active gradient (e.g. when tools change).
 */
public class GradientToolbar extends JPanel {

    private final GradientEditorPanel editorPanel;
    private Runnable changeCallback;
    private static File lastDirectory;

    // Palette cycle live preview
    private FractalRenderer paletteCycleRenderer;
    private java.util.function.Consumer<java.awt.image.BufferedImage> paletteCycleFrameCallback;
    private volatile boolean paletteCycling;
    private Thread paletteCycleThread;

    public static void setDefaultDirectory(File dir) {
        if (lastDirectory == null && dir != null && dir.isDirectory()) {
            lastDirectory = dir;
        }
    }

    public GradientToolbar() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        editorPanel = new GradientEditorPanel(ColorGradient.fractalDefault());
        editorPanel.setPreferredSize(new Dimension(400, 260));

        // Help text
        JLabel helpLabel = new JLabel(
            "<html><b>Shift+click</b> add &nbsp; " +
            "<b>Right-click</b> delete &nbsp; " +
            "<b>Double-click</b> color &nbsp; " +
            "<b>Drag</b> move</html>"
        );
        helpLabel.setFont(helpLabel.getFont().deriveFont(10f));
        helpLabel.setForeground(Color.GRAY);

        // File buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton loadButton = new JButton("Load...");
        JButton saveButton = new JButton("Save...");
        JButton resetButton = new JButton("Reset");
        loadButton.setFont(loadButton.getFont().deriveFont(10f));
        saveButton.setFont(saveButton.getFont().deriveFont(10f));
        resetButton.setFont(resetButton.getFont().deriveFont(10f));
        loadButton.addActionListener(e -> loadGradient());
        saveButton.addActionListener(e -> saveGradient());
        resetButton.addActionListener(e -> {
            ColorGradient current = editorPanel.getGradient();
            current.getStops().clear();
            current.addStop(0f, Color.BLACK);
            current.addStop(1f, Color.WHITE);
            editorPanel.setSelectedStop(null);
            editorPanel.repaint();
            fireChange();
        });
        JCheckBox cycleCheck = new JCheckBox("Cycle");
        cycleCheck.setFont(cycleCheck.getFont().deriveFont(10f));
        cycleCheck.setToolTipText("Live palette cycle animation on current fractal");
        cycleCheck.addActionListener(e -> {
            if (cycleCheck.isSelected()) {
                startPaletteCycle();
            } else {
                stopPaletteCycle();
            }
        });

        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(cycleCheck);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(helpLabel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(editorPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /** Set the gradient to edit. Changes are applied directly to this object. */
    public void setGradient(ColorGradient gradient) {
        editorPanel.setGradient(gradient);
    }

    public ColorGradient getGradient() {
        return editorPanel.getGradient();
    }

    /** Callback fired when the gradient is modified (edit, load, or reset). */
    public void setChangeCallback(Runnable callback) {
        this.changeCallback = callback;
        editorPanel.setEditCallback(this::fireChange);
    }

    private void fireChange() {
        if (changeCallback != null) changeCallback.run();
    }

    /** Set the renderer used for live palette cycling. Call from FractalTool setup. */
    public void setPaletteCycleRenderer(FractalRenderer renderer) {
        this.paletteCycleRenderer = renderer;
    }

    /** Set callback to display each cycle animation frame on the canvas. */
    public void setPaletteCycleFrameCallback(java.util.function.Consumer<java.awt.image.BufferedImage> callback) {
        this.paletteCycleFrameCallback = callback;
    }

    private void startPaletteCycle() {
        if (paletteCycleRenderer == null) return;

        stopPaletteCycle();
        paletteCycling = true;

        ColorGradient baseGrad = getGradient();

        paletteCycleThread = new Thread(() -> {
            // Wait for render iters to become available (initial render may still be running)
            int[] itersCopy = null;
            int w = 0, h = 0;
            for (int attempt = 0; attempt < 300 && paletteCycling; attempt++) {
                int[] iters = paletteCycleRenderer.getLastRenderIters();
                int[] sz = paletteCycleRenderer.getLastRenderSize();
                if (iters != null && sz[0] > 0) {
                    itersCopy = new int[iters.length];
                    System.arraycopy(iters, 0, itersCopy, 0, iters.length);
                    w = sz[0]; h = sz[1];
                    break;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
            if (itersCopy == null) return;

            int frame = 0;
            int totalFrames = 120;
            float cycleSpeed = 1.0f;
            while (paletteCycling) {
                float offset = (frame * cycleSpeed) / totalFrames;
                frame = (frame + 1) % totalFrames;
                java.awt.image.BufferedImage recolored =
                        paletteCycleRenderer.recolorFromIters(itersCopy, w, h, baseGrad, offset);

                editorPanel.setPreviewOffset(offset);
                SwingUtilities.invokeLater(() -> {
                    editorPanel.repaint();
                    if (paletteCycleFrameCallback != null) {
                        paletteCycleFrameCallback.accept(recolored);
                    }
                });

                try { Thread.sleep(33); } catch (InterruptedException e) { break; }
            }
        }, "PaletteCycle");
        paletteCycleThread.setDaemon(true);
        paletteCycleThread.start();
    }

    private void stopPaletteCycle() {
        paletteCycling = false;
        if (paletteCycleThread != null) {
            paletteCycleThread.interrupt();
            paletteCycleThread = null;
        }
        editorPanel.setPreviewOffset(0f);
        editorPanel.repaint();
    }

    private JFileChooser createChooser() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("Gradient files (*.grd)", "grd"));
        return chooser;
    }

    private void loadGradient() {
        JFileChooser chooser = createChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            try {
                ColorGradient loaded = ColorGradient.load(chooser.getSelectedFile());
                ColorGradient current = editorPanel.getGradient();
                current.getStops().clear();
                for (ColorGradient.Stop s : loaded.getStops()) {
                    current.addStop(s.getPosition(), s.getColor());
                }
                editorPanel.setSelectedStop(null);
                editorPanel.repaint();
                fireChange();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load gradient: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveGradient() {
        JFileChooser chooser = createChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            try {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".grd")) {
                    file = new File(file.getPath() + ".grd");
                }
                if (file.exists()) {
                    int result = JOptionPane.showConfirmDialog(this,
                        file.getName() + " already exists.\nDo you want to replace it?",
                        "Confirm Overwrite", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (result != JOptionPane.YES_OPTION) return;
                }
                editorPanel.getGradient().save(file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save gradient: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

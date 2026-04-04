package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.FractalTypeRegistry;
import com.seanick80.drawingapp.fractal.TerrainViewer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientEditorDialog;
import com.seanick80.drawingapp.gradient.GradientToolbar;

import javax.swing.*;
import java.awt.*;

/**
 * Builds the settings panel for the fractal tool. Holds references to
 * the type combo and iteration spinner so callers can read/update them.
 */
public class FractalSettingsPanel {

    private JComboBox<String> typeCombo;
    private JSpinner iterSpinner;

    public JComboBox<String> getTypeCombo() { return typeCombo; }
    public JSpinner getIterSpinner() { return iterSpinner; }

    public JPanel build(FractalRenderer renderer, FractalRenderController renderController,
                        FractalLocationManager locationManager, FractalAnimationController animationController,
                        FractalInfoPanel infoPanel) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        GradientToolbar gradientToolbar = renderController.getGradientToolbar();
        ColorGradient gradient = renderController.getOwnedGradient();

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
                renderController.triggerRender();
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

        // Gradient preview and edit button (only shown if gradient toolbar is not available)
        JPanel gradientPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth(), h = getHeight();
                Color[] colors = renderController.getGradient().toColors(w);
                for (int x = 0; x < w; x++) {
                    g.setColor(colors[x]);
                    g.drawLine(x, 0, x, h);
                }
            }
        };
        renderController.setGradientPreview(gradientPreview);

        if (gradientToolbar == null) {
            JLabel gradLabel = new JLabel("Gradient:");
            gradLabel.setFont(gradLabel.getFont().deriveFont(Font.BOLD, 11f));
            gradLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(gradLabel);

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
                    renderController.setGradient(result);
                    gradientPreview.repaint();
                }
            });
            panel.add(editGradBtn);
        }
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
                () -> { infoPanel.updateInfoLabels(renderer); renderController.triggerRender(); }));
        panel.add(loadBtn);
        panel.add(Box.createVerticalStrut(8));

        // Action buttons
        JButton renderBtn = new JButton("Render");
        renderController.setRenderButton(renderBtn);
        renderBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        renderBtn.setMaximumSize(new Dimension(120, 28));
        renderBtn.setFont(renderBtn.getFont().deriveFont(10f));
        renderBtn.addActionListener(e -> {
            if (renderController.isRendering()) {
                renderController.cancelRender();
            } else {
                renderController.triggerRender();
            }
        });
        panel.add(renderBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton luckyBtn = new JButton("I Feel Lucky");
        luckyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        luckyBtn.setMaximumSize(new Dimension(120, 28));
        luckyBtn.setFont(luckyBtn.getFont().deriveFont(10f));
        luckyBtn.addActionListener(e -> feelLucky(renderer, renderController, infoPanel));
        panel.add(luckyBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton zoomAnimBtn = new JButton("Zoom Movie...");
        zoomAnimBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        zoomAnimBtn.setMaximumSize(new Dimension(120, 28));
        zoomAnimBtn.setFont(zoomAnimBtn.getFont().deriveFont(10f));
        zoomAnimBtn.addActionListener(e ->
                animationController.startZoomAnimation(renderer, renderController.getGradient(),
                        renderController.getLastImage(), locationManager.getLastDirectory(),
                        infoPanel.getProgressLabel(), panel));
        panel.add(zoomAnimBtn);
        panel.add(Box.createVerticalStrut(4));

        JButton flyoverBtn = new JButton("3D Flyover");
        flyoverBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        flyoverBtn.setMaximumSize(new Dimension(120, 28));
        flyoverBtn.setFont(flyoverBtn.getFont().deriveFont(10f));
        flyoverBtn.addActionListener(e ->
                TerrainViewer.openFromRenderer(renderer, renderController.getGradient()));
        panel.add(flyoverBtn);
        panel.add(Box.createVerticalStrut(12));

        // Info labels
        infoPanel.addLabelsTo(panel);

        return panel;
    }

    private void feelLucky(FractalRenderer renderer, FractalRenderController renderController,
                           FractalInfoPanel infoPanel) {
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
                    renderController.triggerRender();
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}

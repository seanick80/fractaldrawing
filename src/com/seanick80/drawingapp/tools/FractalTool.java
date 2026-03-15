package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientEditorDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class FractalTool implements Tool {

    private static final double INITIAL_RANGE = 4.0; // default bounds: -2 to 2

    private final FractalRenderer renderer = new FractalRenderer();
    private ColorGradient gradient;
    private SwingWorker<BufferedImage, Void> currentWorker;
    private JLabel zoomLabel;
    private JLabel centerLabel;
    private JLabel rangeLabel;
    private JLabel cacheLabel;

    public FractalTool() {
        // Default fractal-friendly gradient
        gradient = new ColorGradient();
        gradient.getStops().clear();
        gradient.addStop(0.0f, new Color(0, 7, 100));
        gradient.addStop(0.16f, new Color(32, 107, 203));
        gradient.addStop(0.42f, new Color(237, 255, 255));
        gradient.addStop(0.6425f, new Color(255, 170, 0));
        gradient.addStop(0.8575f, new Color(200, 82, 0));
        gradient.addStop(1.0f, new Color(0, 2, 0));
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

        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Mandelbrot", "Julia"});
        typeCombo.setMaximumSize(new Dimension(120, 28));
        typeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        typeCombo.addActionListener(e -> {
            renderer.setType(typeCombo.getSelectedIndex() == 0
                ? FractalType.MANDELBROT : FractalType.JULIA);
            // Reset bounds for the selected type
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

        JSpinner iterSpinner = new JSpinner(new SpinnerNumberModel(256, 10, 10000, 50));
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

        updateInfoLabels();

        return panel;
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        // Cancel any in-progress render
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        int w = image.getWidth();
        int h = image.getHeight();
        int button = canvas.getLastMouseButton();

        // Zoom: compute new bounds centered on click position
        if (button == java.awt.event.MouseEvent.BUTTON1 || button == java.awt.event.MouseEvent.BUTTON3) {
            double minR = renderer.getMinReal(), maxR = renderer.getMaxReal();
            double minI = renderer.getMinImag(), maxI = renderer.getMaxImag();
            double rangeR = maxR - minR;
            double rangeI = maxI - minI;

            // Map click pixel to complex coordinate
            double clickReal = minR + (double) x / w * rangeR;
            double clickImag = minI + (double) y / h * rangeI;

            double zoomFactor = (button == java.awt.event.MouseEvent.BUTTON1) ? 0.5 : 2.0;
            double newRangeR = rangeR * zoomFactor;
            double newRangeI = rangeI * zoomFactor;

            renderer.setBounds(
                clickReal - newRangeR / 2, clickReal + newRangeR / 2,
                clickImag - newRangeI / 2, clickImag + newRangeI / 2
            );
        }

        renderAsync(image, canvas);
    }

    @Override
    public void mouseWheelMoved(BufferedImage image, int x, int y, int wheelRotation, DrawingCanvas canvas) {
        // Cancel any in-progress render
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        int w = image.getWidth();
        int h = image.getHeight();

        double minR = renderer.getMinReal(), maxR = renderer.getMaxReal();
        double minI = renderer.getMinImag(), maxI = renderer.getMaxImag();
        double rangeR = maxR - minR;
        double rangeI = maxI - minI;

        // Map mouse position to complex coordinate
        double clickReal = minR + (double) x / w * rangeR;
        double clickImag = minI + (double) y / h * rangeI;

        // Scroll up (negative) = zoom in, scroll down (positive) = zoom out
        double zoomFactor = (wheelRotation < 0) ? 0.8 : 1.25;
        double newRangeR = rangeR * zoomFactor;
        double newRangeI = rangeI * zoomFactor;

        renderer.setBounds(
            clickReal - newRangeR / 2, clickReal + newRangeR / 2,
            clickImag - newRangeI / 2, clickImag + newRangeI / 2
        );

        renderAsync(image, canvas);
    }

    private void renderAsync(BufferedImage image, DrawingCanvas canvas) {
        int w = image.getWidth();
        int h = image.getHeight();

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
                    Graphics2D g = image.createGraphics();
                    g.drawImage(fractalImage, 0, 0, null);
                    g.dispose();
                    canvas.repaint();
                    updateInfoLabels();
                } catch (Exception ignored) {}
            }
        };
        currentWorker.execute();
    }

    private void updateInfoLabels() {
        if (zoomLabel == null) return;
        double rangeR = renderer.getMaxReal() - renderer.getMinReal();
        double rangeI = renderer.getMaxImag() - renderer.getMinImag();
        double zoom = INITIAL_RANGE / Math.min(rangeR, rangeI);
        double centerR = (renderer.getMinReal() + renderer.getMaxReal()) / 2;
        double centerI = (renderer.getMinImag() + renderer.getMaxImag()) / 2;

        zoomLabel.setText(String.format("Zoom: %.2e", zoom));
        centerLabel.setText(String.format("Re: %.15g", centerR));
        rangeLabel.setText(String.format("Im: %.15g", centerI));

        var cache = renderer.getCache();
        int lookups = cache.getLookups();
        if (lookups > 0) {
            double hitRate = 100.0 * cache.getHits() / lookups;
            cacheLabel.setText(String.format("Cache: %dk %.0f%%", cache.size() / 1000, hitRate));
        } else {
            cacheLabel.setText(String.format("Cache: %dk", cache.size() / 1000));
        }

        // Warn when approaching double precision limits (~1e-15 relative)
        String tooltip = null;
        if (rangeR < 1e-13 || rangeI < 1e-13) {
            tooltip = "Near double precision limit!";
            zoomLabel.setForeground(Color.RED);
        } else {
            zoomLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
        zoomLabel.setToolTipText(tooltip);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {}
}

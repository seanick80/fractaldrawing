package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.ZoomAnimator;
import com.seanick80.drawingapp.gradient.ColorGradient;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles zoom movie creation and "I Feel Lucky" random location finding.
 */
public class FractalAnimationController {

    private static final double INITIAL_RANGE = 4.0;

    /**
     * Searches for a random interesting Mandelbrot location by sampling points
     * and checking for varied iteration counts (a sign of boundary detail).
     * Returns {centerReal, centerImag, halfSpan} or null if nothing found.
     */
    public static double[] findInterestingLocation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        FractalType mandelbrot = FractalType.MANDELBROT;
        int maxIter = 256;

        double[][] offsets = {
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1},
            {0, -1}, {0, 1}, {-1, 0}, {1, 0}
        };

        for (int attempt = 0; attempt < 1000; attempt++) {
            double centerR = rng.nextDouble(-2.0, 0.5);
            double centerI = rng.nextDouble(-1.2, 1.2);
            double exponent = rng.nextDouble(2.0, 8.0);
            double halfSpan = Math.pow(10, -exponent) / 2.0;

            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            boolean hasInterior = false;
            boolean hasEscape = false;
            boolean allTrivial = true;

            for (int s = 0; s < 8; s++) {
                double px = centerR + offsets[s][0] * halfSpan;
                double py = centerI + offsets[s][1] * halfSpan;
                int iter = mandelbrot.iterate(px, py, maxIter);
                seen.add(iter);
                if (iter >= maxIter) hasInterior = true;
                if (iter < maxIter) hasEscape = true;
                if (iter >= 5) allTrivial = false;
            }

            if (seen.size() >= 4 && hasInterior && hasEscape && !allTrivial) {
                return new double[] { centerR, centerI, halfSpan };
            }
        }
        return null;
    }

    /**
     * Show zoom movie dialog and start rendering in background.
     */
    public void startZoomAnimation(FractalRenderer renderer, ColorGradient gradient,
                                    BufferedImage lastImage, File lastDirectory,
                                    JLabel progressLabel, Component parent) {
        Window window = SwingUtilities.getWindowAncestor(parent);

        // Current viewport info
        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double currentRange = Math.min(rangeR.doubleValue(), rangeI.doubleValue());
        double currentZoom = INITIAL_RANGE / currentRange;
        MathContext mc = getZoomMathContext(renderer.getMinRealBig(), renderer.getMaxRealBig(),
                                            renderer.getMinImagBig(), renderer.getMaxImagBig());
        BigDecimal two = new BigDecimal(2);
        BigDecimal curCenterR = renderer.getMinRealBig().add(renderer.getMaxRealBig(), mc).divide(two, mc);
        BigDecimal curCenterI = renderer.getMinImagBig().add(renderer.getMaxImagBig(), mc).divide(two, mc);

        // Build candidate list
        if (progressLabel != null) progressLabel.setText("Scanning for interesting points...");
        List<ZoomAnimator.ZoomTarget> autoTargets = ZoomAnimator.findInterestingPoints(
                renderer.getType(), 512, 3);

        BufferedImage curPreview = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        if (lastImage != null) {
            Graphics2D g = curPreview.createGraphics();
            g.drawImage(lastImage, 0, 0, 120, 90, null);
            g.dispose();
        }
        ZoomAnimator.ZoomTarget currentTarget = new ZoomAnimator.ZoomTarget(
                curCenterR, curCenterI, Double.MAX_VALUE, curPreview);

        List<ZoomAnimator.ZoomTarget> allTargets = new ArrayList<>();
        allTargets.add(currentTarget);
        allTargets.addAll(autoTargets);

        // Target picker dialog
        JPanel pickerPanel = new JPanel(new BorderLayout(8, 8));
        pickerPanel.add(new JLabel("Select a zoom target:"), BorderLayout.NORTH);

        JPanel thumbPanel = new JPanel(new GridLayout(1, allTargets.size(), 8, 8));
        ButtonGroup group = new ButtonGroup();
        JRadioButton[] buttons = new JRadioButton[allTargets.size()];
        for (int i = 0; i < allTargets.size(); i++) {
            ZoomAnimator.ZoomTarget t = allTargets.get(i);
            JPanel card = new JPanel(new BorderLayout(4, 4));
            card.add(new JLabel(new ImageIcon(t.preview)), BorderLayout.CENTER);
            buttons[i] = new JRadioButton(i == 0 ? "Current view" : String.format("Auto #%d", i));
            if (i == 0) buttons[i].setSelected(true);
            group.add(buttons[i]);
            card.add(buttons[i], BorderLayout.SOUTH);
            thumbPanel.add(card);
        }
        pickerPanel.add(thumbPanel, BorderLayout.CENTER);

        // Settings
        JTextField framesField = new JTextField("120", 5);
        JTextField fpsField = new JTextField("30", 5);
        JTextField zoomField = new JTextField(
            String.valueOf((long) Math.max(currentZoom * 10, 10000)), 8);
        JTextField widthField = new JTextField(
            lastImage != null ? String.valueOf(lastImage.getWidth()) : "640", 5);
        JTextField heightField = new JTextField(
            lastImage != null ? String.valueOf(lastImage.getHeight()) : "480", 5);

        JPanel settingsPanel = new JPanel(new GridLayout(5, 2, 4, 4));
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
        pickerPanel.add(settingsPanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(window, pickerPanel,
                "Zoom Movie — Pick Target", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        int selectedIdx = 0;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].isSelected()) { selectedIdx = i; break; }
        }
        ZoomAnimator.ZoomTarget target = allTargets.get(selectedIdx);

        int totalFrames, fpsVal, frameW, frameH;
        double finalZoom;
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

        // Choose output directory
        JFileChooser fc = new JFileChooser(lastDirectory != null ? lastDirectory : new File("."));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select output directory for zoom movie");
        if (fc.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return;
        File outputDir = fc.getSelectedFile();

        // Build keyframes
        int movieIter = ZoomAnimator.iterationsForZoom(finalZoom, 256);
        FractalRenderer animRenderer = new FractalRenderer();
        animRenderer.setType(renderer.getType());
        animRenderer.setRenderMode(FractalRenderer.RenderMode.AUTO);

        ZoomAnimator animator = new ZoomAnimator(animRenderer, gradient);
        animator.setSize(frameW, frameH);
        animator.setFramesPerSegment(totalFrames - 1);
        animator.setFps(fpsVal);
        animator.addKeyframe(new ZoomAnimator.Keyframe(
                target.centerReal, target.centerImag,
                BigDecimal.ONE, movieIter));
        animator.addKeyframe(new ZoomAnimator.Keyframe(
                target.centerReal, target.centerImag,
                new BigDecimal(Double.toString(finalZoom)), movieIter));

        // Render in background
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

    static MathContext getZoomMathContext(BigDecimal minR, BigDecimal maxR,
                                          BigDecimal minI, BigDecimal maxI) {
        double rangeR = maxR.subtract(minR).abs().doubleValue();
        double rangeI = maxI.subtract(minI).abs().doubleValue();
        double minRange = Math.min(rangeR, rangeI);
        double zoom = (minRange > 0) ? 4.0 / minRange : 1;
        int precision = 20 + (int) Math.ceil(Math.log10(Math.max(zoom, 1)));
        return new MathContext(precision, RoundingMode.HALF_UP);
    }
}

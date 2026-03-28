package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.FractalRenderer;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Manages fractal info labels (zoom, center, range, cache, time, progress)
 * and the render progress timer with live progressive rendering feedback.
 */
public class FractalInfoPanel {

    private static final double INITIAL_RANGE = 4.0;

    private JLabel zoomLabel;
    private JLabel timeLabel;
    private JLabel centerLabel;
    private JLabel rangeLabel;
    private JLabel cacheLabel;
    private JLabel progressLabel;

    private long renderStartTime;
    private javax.swing.Timer progressTimer;

    public JLabel getProgressLabel() { return progressLabel; }
    public long getRenderStartTime() { return renderStartTime; }
    public void setRenderStartTime(long t) { this.renderStartTime = t; }

    /**
     * Create and add info labels to the given panel.
     * Returns the progress label for external use.
     */
    public void addLabelsTo(JPanel panel) {
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

        timeLabel = new JLabel();
        timeLabel.setFont(monoFont);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(timeLabel);

        cacheLabel = new JLabel();
        cacheLabel.setFont(monoFont);
        cacheLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(cacheLabel);

        panel.add(Box.createVerticalStrut(8));

        progressLabel = new JLabel(" ");
        progressLabel.setFont(monoFont);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(progressLabel);
    }

    public void updateInfoLabels(FractalRenderer renderer) {
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

        MathContext mc = FractalAnimationController.getZoomMathContext(
                renderer.getMinRealBig(), renderer.getMaxRealBig(),
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

    public void startProgressTimer(FractalRenderer renderer, int width, int height,
                                    java.awt.image.BufferedImage image,
                                    com.seanick80.drawingapp.DrawingCanvas canvas) {
        progressTimer = new javax.swing.Timer(200, e -> {
            if (progressLabel == null) return;
            long elapsed = System.currentTimeMillis() - renderStartTime;

            int[] progRgb = renderer.getProgressiveRgb();
            if (progRgb != null && image != null && canvas != null) {
                try {
                    image.setRGB(0, 0, width, height, progRgb, 0, width);
                    canvas.repaint();
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

    public void stopProgressTimer() {
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
    }

    static String formatElapsedShort(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("%.1fs", sec);
        int min = (int) (sec / 60);
        return String.format("%dm%.0fs", min, sec - min * 60);
    }

    private static String formatElapsed(long ms) {
        if (ms < 1000) return String.format("Time: %dms", ms);
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("Time: %.1fs", sec);
        int min = (int) (sec / 60);
        double remSec = sec - min * 60;
        return String.format("Time: %dm %.1fs", min, remSec);
    }
}

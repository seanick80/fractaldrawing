package com.seanick80.drawingapp.fractal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/**
 * A standalone window that plays back animation frames in real time.
 * Used by both palette cycle and iteration animation preview modes.
 */
public class AnimationPreviewWindow extends JFrame {

    private final ImagePanel imagePanel;
    private volatile boolean running;
    private volatile boolean stopped;
    private final JLabel statusLabel;
    private final JButton stopBtn;

    public AnimationPreviewWindow(String title, int width, int height) {
        super(title);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        imagePanel = new ImagePanel();
        imagePanel.setPreferredSize(new Dimension(width, height));

        statusLabel = new JLabel("Starting...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        stopBtn = new JButton("Stop");
        stopBtn.addActionListener(e -> stop());

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(stopBtn, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(imagePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { stop(); }
        });
    }

    public void stop() {
        running = false;
        stopped = true;
    }

    public boolean isStopped() { return stopped; }

    /**
     * Play frames from the supplier in a loop at the given FPS.
     * The supplier produces the next frame each call, or null to end.
     * Runs in a background thread; call setVisible(true) before this.
     */
    public void playLoop(int fps, boolean loop, Supplier<BufferedImage> frameSupplier) {
        running = true;
        stopped = false;
        long frameTimeMs = 1000 / Math.max(1, fps);

        Thread playThread = new Thread(() -> {
            int frameCount = 0;
            while (running) {
                long start = System.currentTimeMillis();
                BufferedImage frame = frameSupplier.get();
                if (frame == null) {
                    if (loop) {
                        frameCount = 0;
                        continue;
                    }
                    break;
                }
                frameCount++;
                int fc = frameCount;
                SwingUtilities.invokeLater(() -> {
                    imagePanel.setImage(frame);
                    statusLabel.setText("Frame " + fc);
                });

                long elapsed = System.currentTimeMillis() - start;
                long sleepMs = frameTimeMs - elapsed;
                if (sleepMs > 0) {
                    try { Thread.sleep(sleepMs); } catch (InterruptedException e) { break; }
                }
            }
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Finished");
                stopBtn.setText("Close");
                stopBtn.removeActionListener(stopBtn.getActionListeners()[0]);
                stopBtn.addActionListener(e -> dispose());
            });
        }, "AnimPreview-" + getTitle());
        playThread.setDaemon(true);
        playThread.start();
    }

    private static class ImagePanel extends JPanel {
        private BufferedImage image;

        void setImage(BufferedImage img) {
            this.image = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }
}

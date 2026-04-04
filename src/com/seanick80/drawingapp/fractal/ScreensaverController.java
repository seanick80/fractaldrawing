package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.tools.FractalAnimationController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Full-screen screensaver that cycles through random interesting
 * Mandelbrot locations with cross-fade transitions.
 */
public class ScreensaverController {

    private final int displayDurationMs;
    private final int transitionDurationMs;
    private final FractalRenderer.ColorMode colorMode;
    private volatile boolean running;
    private JFrame fullScreenFrame;

    public ScreensaverController(int displayDurationSeconds) {
        this(displayDurationSeconds, FractalRenderer.ColorMode.MOD);
    }

    public ScreensaverController(int displayDurationSeconds, FractalRenderer.ColorMode colorMode) {
        this.displayDurationMs = displayDurationSeconds * 1000;
        this.transitionDurationMs = 1000;
        this.colorMode = colorMode;
    }

    public boolean isRunning() { return running; }

    public void start() {
        if (running) return;
        running = true;

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        fullScreenFrame = new JFrame();
        fullScreenFrame.setUndecorated(true);
        fullScreenFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        ScreensaverPanel panel = new ScreensaverPanel(
                screenSize.width, screenSize.height, this::stop);
        fullScreenFrame.setContentPane(panel);

        fullScreenFrame.setSize(screenSize);
        if (gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(fullScreenFrame);
        } else {
            fullScreenFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            fullScreenFrame.setVisible(true);
        }

        // Hide cursor
        fullScreenFrame.setCursor(fullScreenFrame.getToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                new Point(0, 0), "blank"));

        // Start render loop in background
        new Thread(() -> renderLoop(panel, screenSize.width, screenSize.height),
                "Screensaver-Render").start();
    }

    public void stop() {
        running = false;
        SwingUtilities.invokeLater(() -> {
            if (fullScreenFrame != null) {
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice();
                if (gd.getFullScreenWindow() == fullScreenFrame) {
                    gd.setFullScreenWindow(null);
                }
                fullScreenFrame.dispose();
                fullScreenFrame = null;
            }
        });
    }

    private void renderLoop(ScreensaverPanel panel, int width, int height) {
        FractalRenderer renderer = new FractalRenderer();
        renderer.setType(FractalType.MANDELBROT);
        renderer.setRenderMode(FractalRenderer.RenderMode.AUTO);
        renderer.setColorMode(colorMode);

        while (running) {
            double[] loc = FractalAnimationController.findInterestingLocation();
            if (loc == null) {
                // Fallback: full Mandelbrot
                loc = new double[]{-0.5, 0.0, 2.0};
            }

            renderer.setBounds(
                    loc[0] - loc[2], loc[0] + loc[2],
                    loc[1] - loc[2], loc[1] + loc[2]);
            renderer.setMaxIterations(256);

            // Random gradient for variety
            float hue = (float) Math.random();
            ColorGradient gradient = ColorGradient.fromBaseColor(
                    Color.getHSBColor(hue, 0.8f, 0.9f));

            BufferedImage img = renderer.render(width, height, gradient);
            if (img == null || !running) break;

            panel.transitionTo(img, transitionDurationMs);

            // Wait for display duration
            long deadline = System.currentTimeMillis() + displayDurationMs;
            while (running && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Panel that displays fractal images with cross-fade transitions.
     */
    static class ScreensaverPanel extends JPanel {
        private BufferedImage currentImage;
        private BufferedImage nextImage;
        private float alpha = 1.0f;
        private Timer fadeTimer;
        private final Runnable exitCallback;

        ScreensaverPanel(int width, int height, Runnable exitCallback) {
            this.exitCallback = exitCallback;
            setBackground(Color.BLACK);
            setPreferredSize(new Dimension(width, height));

            // Exit on any key or mouse movement
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) { exitCallback.run(); }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { exitCallback.run(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                private int lastX = -1, lastY = -1;
                @Override
                public void mouseMoved(MouseEvent e) {
                    // Ignore the first move event (initial cursor position)
                    if (lastX == -1) {
                        lastX = e.getX();
                        lastY = e.getY();
                        return;
                    }
                    if (Math.abs(e.getX() - lastX) > 5 || Math.abs(e.getY() - lastY) > 5) {
                        exitCallback.run();
                    }
                }
            });

            // Request focus when shown
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                        && isShowing()) {
                    requestFocusInWindow();
                }
            });
        }

        void transitionTo(BufferedImage image, int durationMs) {
            if (currentImage == null) {
                currentImage = image;
                alpha = 1.0f;
                repaint();
                return;
            }

            nextImage = image;
            alpha = 0.0f;

            int steps = durationMs / 33; // ~30fps fade
            float alphaStep = 1.0f / Math.max(1, steps);

            if (fadeTimer != null) fadeTimer.stop();
            fadeTimer = new Timer(33, null);
            fadeTimer.addActionListener(e -> {
                alpha += alphaStep;
                if (alpha >= 1.0f) {
                    alpha = 1.0f;
                    currentImage = nextImage;
                    nextImage = null;
                    fadeTimer.stop();
                }
                repaint();
            });
            fadeTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (currentImage != null) {
                g2.drawImage(currentImage, 0, 0, null);
            }

            if (nextImage != null && alpha > 0 && alpha < 1.0f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.drawImage(nextImage, 0, 0, null);
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }
    }
}

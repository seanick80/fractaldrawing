package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.tools.FractalAnimationController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen screensaver that cycles through random interesting
 * Mandelbrot locations with cross-fade transitions.
 * Spans all connected displays.
 */
public class ScreensaverController {

    private final int displayDurationMs;
    private final int transitionDurationMs;
    private final FractalRenderer.ColorMode colorMode;
    private volatile boolean running;
    private final List<JFrame> fullScreenFrames = new ArrayList<>();

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

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();

        List<ScreenInfo> screenInfos = new ArrayList<>();
        for (GraphicsDevice gd : screens) {
            Rectangle bounds = gd.getDefaultConfiguration().getBounds();

            JFrame frame = new JFrame(gd.getDefaultConfiguration());
            frame.setUndecorated(true);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            ScreensaverPanel panel = new ScreensaverPanel(
                    bounds.width, bounds.height, this::stop);
            frame.setContentPane(panel);
            frame.setBounds(bounds);

            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(frame);
            } else {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setVisible(true);
            }

            // Hide cursor
            frame.setCursor(frame.getToolkit().createCustomCursor(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    new Point(0, 0), "blank"));

            fullScreenFrames.add(frame);
            screenInfos.add(new ScreenInfo(gd, panel, bounds.width, bounds.height));
        }

        // Start render loop in background
        new Thread(() -> renderLoop(screenInfos), "Screensaver-Render").start();
    }

    public void stop() {
        running = false;
        SwingUtilities.invokeLater(() -> {
            for (JFrame frame : fullScreenFrames) {
                GraphicsDevice gd = frame.getGraphicsConfiguration().getDevice();
                if (gd.getFullScreenWindow() == frame) {
                    gd.setFullScreenWindow(null);
                }
                frame.dispose();
            }
            fullScreenFrames.clear();
        });
    }

    private void renderLoop(List<ScreenInfo> screens) {
        // One renderer per screen for parallel rendering
        List<FractalRenderer> renderers = new ArrayList<>();
        for (int i = 0; i < screens.size(); i++) {
            FractalRenderer renderer = new FractalRenderer();
            renderer.setType(FractalType.MANDELBROT);
            renderer.setRenderMode(FractalRenderer.RenderMode.AUTO);
            renderer.setColorMode(colorMode);
            renderers.add(renderer);
        }

        while (running) {
            // Find a location and render on all screens
            double[] loc = FractalAnimationController.findInterestingLocation();
            if (loc == null) {
                loc = new double[]{-0.5, 0.0, 2.0};
            }

            float hue = (float) Math.random();
            ColorGradient gradient = ColorGradient.fromBaseColor(
                    Color.getHSBColor(hue, 0.8f, 0.9f));

            for (int i = 0; i < screens.size(); i++) {
                if (!running) return;
                ScreenInfo si = screens.get(i);
                FractalRenderer renderer = renderers.get(i);

                renderer.setBounds(
                        loc[0] - loc[2], loc[0] + loc[2],
                        loc[1] - loc[2], loc[1] + loc[2]);
                renderer.setMaxIterations(256);

                BufferedImage img = renderer.render(si.width, si.height, gradient);
                if (img == null || !running) return;
                si.panel.transitionTo(img, transitionDurationMs);
            }

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

    private static class ScreenInfo {
        final GraphicsDevice device;
        final ScreensaverPanel panel;
        final int width, height;

        ScreenInfo(GraphicsDevice device, ScreensaverPanel panel, int width, int height) {
            this.device = device;
            this.panel = panel;
            this.width = width;
            this.height = height;
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
        ScreensaverPanel(int width, int height, Runnable exitCallback) {
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

            int steps = durationMs / 33;
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

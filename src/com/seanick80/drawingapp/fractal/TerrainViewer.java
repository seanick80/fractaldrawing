package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Interactive 3D terrain flyover viewer. Generates fractal terrain using
 * diamond-square algorithm, colored with the app's gradient system.
 * Optionally blends fractal iteration colors as a terrain texture.
 *
 * Controls:
 *   W/S         — move forward/backward
 *   A/D         — strafe left/right
 *   Left/Right  — turn
 *   Up/Down     — pitch up/down
 *   Q/E         — altitude up/down
 *   +/-         — adjust terrain height scale
 *   R           — toggle recording
 *   F           — toggle auto-fly
 *   Escape      — close
 */
public class TerrainViewer extends JFrame {

    private static final int VIEW_W = 640;
    private static final int VIEW_H = 480;
    private static final int TARGET_FPS = 30;

    private final TerrainRenderer terrain;
    private final RenderPanel panel;

    // Camera state
    private float camX, camY;
    private float camAlt;
    private float heading = 0;
    private float pitch = 0;
    private float moveSpeed = 3.0f;
    private float turnSpeed = 0.04f;

    // Key tracking
    private final Set<Integer> keysDown = new HashSet<>();

    // Recording
    private AviWriter aviWriter;
    private File recordDir;
    private int recordedFrames;
    private boolean recording = false;

    // Auto-fly
    private boolean autoFly = true;
    private float autoTurnRate = 0.005f;

    // Render loop
    private final Timer renderTimer;
    private final JLabel statusLabel;

    public TerrainViewer(TerrainRenderer terrain) {
        super("3D Fractal Terrain");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.terrain = terrain;

        // Find a good starting position
        float[] startPos = terrain.findStartPosition();
        camX = startPos[0];
        camY = startPos[1];
        camAlt = startPos[2];

        panel = new RenderPanel();
        panel.setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        panel.setFocusable(true);

        statusLabel = new JLabel(buildStatusText());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        panel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                keysDown.add(e.getKeyCode());
                handleKeyAction(e.getKeyCode());
            }
            @Override public void keyReleased(KeyEvent e) {
                keysDown.remove(e.getKeyCode());
            }
        });

        renderTimer = new Timer(1000 / TARGET_FPS, e -> tick());
        renderTimer.setCoalesce(true);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                stopRecording();
                renderTimer.stop();
            }
            @Override public void windowOpened(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
    }

    public void start() {
        setVisible(true);
        renderTimer.start();
    }

    private void handleKeyAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_R:
                if (recording) stopRecording();
                else startRecording();
                break;
            case KeyEvent.VK_F:
                autoFly = !autoFly;
                updateStatus();
                break;
            case KeyEvent.VK_ESCAPE:
                dispose();
                break;
        }
    }

    private void tick() {
        updateCamera();
        BufferedImage frame = terrain.render(VIEW_W, VIEW_H,
                camX, camY, camAlt, heading, pitch);
        panel.setFrame(frame);

        if (recording && aviWriter != null) {
            try {
                aviWriter.addFrame(frame);
                recordedFrames++;
            } catch (Exception ex) {
                stopRecording();
            }
        }
        updateStatus();
    }

    private void updateCamera() {
        float sin = (float) Math.sin(heading);
        float cos = (float) Math.cos(heading);

        if (autoFly) {
            camX += cos * moveSpeed;
            camY += sin * moveSpeed;
            heading += autoTurnRate;
        }

        if (keysDown.contains(KeyEvent.VK_W)) {
            camX += cos * moveSpeed;
            camY += sin * moveSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_S)) {
            camX -= cos * moveSpeed;
            camY -= sin * moveSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_A)) {
            camX += sin * moveSpeed;
            camY -= cos * moveSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_D)) {
            camX -= sin * moveSpeed;
            camY += cos * moveSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_LEFT)) {
            heading -= turnSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_RIGHT)) {
            heading += turnSpeed;
        }
        if (keysDown.contains(KeyEvent.VK_UP)) {
            pitch += 3;
        }
        if (keysDown.contains(KeyEvent.VK_DOWN)) {
            pitch -= 3;
        }
        if (keysDown.contains(KeyEvent.VK_Q)) {
            camAlt += 2;
        }
        if (keysDown.contains(KeyEvent.VK_E)) {
            camAlt -= 2;
            if (camAlt < 5) camAlt = 5;
        }
        if (keysDown.contains(KeyEvent.VK_EQUALS) || keysDown.contains(KeyEvent.VK_PLUS)) {
            terrain.setHeightScale(terrain.getHeightScale() + 2);
        }
        if (keysDown.contains(KeyEvent.VK_MINUS)) {
            terrain.setHeightScale(Math.max(10, terrain.getHeightScale() - 2));
        }
    }

    private void startRecording() {
        try {
            recordDir = new File(System.getProperty("java.io.tmpdir"), "terrain_recording");
            recordDir.mkdirs();
            File aviFile = new File(recordDir, "terrain.avi");
            aviWriter = new AviWriter(aviFile, VIEW_W, VIEW_H, TARGET_FPS);
            recordedFrames = 0;
            recording = true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to start recording: " + ex.getMessage(),
                    "Recording Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        if (aviWriter != null) {
            try {
                aviWriter.close();
                JOptionPane.showMessageDialog(this,
                        recordedFrames + " frames saved to:\n" + recordDir.getAbsolutePath(),
                        "Recording Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving: " + ex.getMessage(),
                        "Recording Error", JOptionPane.ERROR_MESSAGE);
            }
            aviWriter = null;
        }
    }

    private String buildStatusText() {
        StringBuilder sb = new StringBuilder();
        if (recording) sb.append("[REC ").append(recordedFrames).append("] ");
        if (autoFly) sb.append("[AUTO-FLY] ");
        sb.append("WASD:move Arrows:look Q/E:alt +/-:scale R:rec F:fly Esc:close");
        return sb.toString();
    }

    private void updateStatus() {
        statusLabel.setText(buildStatusText());
    }

    /** Render panel that displays the current frame. */
    private static class RenderPanel extends JPanel {
        private volatile BufferedImage frame;

        void setFrame(BufferedImage img) {
            this.frame = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage f = frame;
            if (f != null) {
                g.drawImage(f, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Convenience launchers
    // -----------------------------------------------------------------------

    /**
     * Open a terrain viewer with diamond-square generated terrain,
     * colored with the given gradient. Optionally blends fractal
     * iteration colors if a renderer with iteration data is provided.
     */
    public static void openFromRenderer(FractalRenderer renderer, ColorGradient gradient) {
        int power = 9; // 513x513 map
        long seed = System.currentTimeMillis();
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.55f, seed);
        int mapSize = (1 << power) + 1;

        int[] colormap = TerrainRenderer.buildColorMap(heightmap, gradient);

        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);
        TerrainViewer viewer = new TerrainViewer(tr);
        viewer.start();
    }
}

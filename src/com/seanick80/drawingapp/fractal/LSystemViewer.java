package com.seanick80.drawingapp.fractal;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Window that displays an L-System tree rendered from fractal iteration data.
 * Shows the tree image with parameter info in a status bar.
 */
public class LSystemViewer extends JFrame {

    private static final int VIEW_W = 800;
    private static final int VIEW_H = 700;

    private LSystemViewer(LSystemRenderer renderer) {
        super("L-System Tree (from Fractal Iterations)");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        BufferedImage img = renderer.render(VIEW_W, VIEW_H);

        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            }
        };
        imagePanel.setPreferredSize(new Dimension(VIEW_W, VIEW_H));

        JLabel statusLabel = new JLabel("  " + renderer.getParams().toString());
        statusLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        setLayout(new BorderLayout());
        add(imagePanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
    }

    public static void open(LSystemRenderer renderer) {
        LSystemViewer viewer = new LSystemViewer(renderer);
        viewer.setVisible(true);
    }
}

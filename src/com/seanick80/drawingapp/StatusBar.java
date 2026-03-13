package com.seanick80.drawingapp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class StatusBar extends JPanel {

    private final JLabel coordLabel;
    private final JLabel sizeLabel;

    public StatusBar() {
        setLayout(new BorderLayout());
        setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));

        coordLabel = new JLabel(" ");
        coordLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        sizeLabel = new JLabel(" ");
        sizeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        add(coordLabel, BorderLayout.WEST);
        add(sizeLabel, BorderLayout.EAST);
    }

    public void setCoordinates(int x, int y) {
        coordLabel.setText(String.format("(%d, %d)", x, y));
    }

    public void clearCoordinates() {
        coordLabel.setText(" ");
    }

    public void setCanvasSize(int w, int h) {
        sizeLabel.setText(String.format("%d x %d", w, h));
    }
}

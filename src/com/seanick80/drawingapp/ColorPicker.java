package com.seanick80.drawingapp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ColorPicker extends JPanel {

    public static final String PROP_FOREGROUND_COLOR = "foregroundColor";

    private Color foregroundColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;
    private final JPanel fgSwatch;
    private final JPanel bgSwatch;
    private final DrawingCanvas canvas;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private static final Color[] PALETTE = {
        Color.BLACK, Color.WHITE, Color.DARK_GRAY, Color.GRAY,
        Color.LIGHT_GRAY, new Color(128, 0, 0), Color.RED, new Color(255, 128, 0),
        Color.YELLOW, new Color(0, 128, 0), Color.GREEN, new Color(0, 128, 128),
        Color.CYAN, new Color(0, 0, 128), Color.BLUE, new Color(128, 0, 128),
        Color.MAGENTA, Color.PINK, new Color(128, 128, 0), new Color(0, 64, 0),
    };

    public ColorPicker(DrawingCanvas canvas) {
        this.canvas = canvas;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Active colors display
        JPanel activePanel = new JPanel(null);
        activePanel.setPreferredSize(new Dimension(60, 50));

        bgSwatch = createSwatch(backgroundColor);
        bgSwatch.setBounds(15, 15, 30, 30);
        bgSwatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Color c = JColorChooser.showDialog(ColorPicker.this, "Background Color", backgroundColor);
                if (c != null) setBackgroundColor(c);
            }
        });

        fgSwatch = createSwatch(foregroundColor);
        fgSwatch.setBounds(5, 5, 30, 30);
        fgSwatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Color c = JColorChooser.showDialog(ColorPicker.this, "Foreground Color", foregroundColor);
                if (c != null) setForegroundColor(c);
            }
        });

        activePanel.add(fgSwatch);
        activePanel.add(bgSwatch);

        // Palette grid
        JPanel palettePanel = new JPanel(new GridLayout(4, 5, 2, 2));
        for (Color color : PALETTE) {
            JPanel cell = createSwatch(color);
            cell.setPreferredSize(new Dimension(16, 16));
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        setForegroundColor(color);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        setBackgroundColor(color);
                    }
                }
            });
            palettePanel.add(cell);
        }

        JLabel label = new JLabel("Colors", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));

        add(label, BorderLayout.NORTH);
        add(activePanel, BorderLayout.WEST);
        add(palettePanel, BorderLayout.CENTER);
    }

    private JPanel createSwatch(Color color) {
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.DARK_GRAY),
            BorderFactory.createLineBorder(Color.WHITE)
        ));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return swatch;
    }

    public Color getForegroundColor() { return foregroundColor; }
    public Color getBackgroundColor() { return backgroundColor; }

    public void setForegroundColor(Color c) {
        Color old = foregroundColor;
        foregroundColor = c;
        fgSwatch.setBackground(c);
        pcs.firePropertyChange(PROP_FOREGROUND_COLOR, old, c);
    }

    public void addColorPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removeColorPropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void setBackgroundColor(Color c) {
        backgroundColor = c;
        bgSwatch.setBackground(c);
    }
}

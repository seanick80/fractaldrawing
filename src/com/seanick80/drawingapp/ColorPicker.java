package com.seanick80.drawingapp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

public class ColorPicker extends JPanel {

    public static final String PROP_FOREGROUND_COLOR = "foregroundColor";

    private Color foregroundColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;
    private final JPanel fgSwatch;
    private final JPanel bgSwatch;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final HSBPanel hsbPanel;
    private final JSlider brightnessSlider;
    private final JTextField hexField;
    private final List<Color> recentColors = new ArrayList<>();
    private final JPanel recentPanel;
    private boolean updatingFromCode = false;

    private static final int MAX_RECENT = 10;

    private static final Color[] PALETTE = {
        Color.BLACK, Color.WHITE, Color.DARK_GRAY, Color.GRAY,
        Color.LIGHT_GRAY, new Color(128, 0, 0), Color.RED, new Color(255, 128, 0),
        Color.YELLOW, new Color(0, 128, 0), Color.GREEN, new Color(0, 128, 128),
        Color.CYAN, new Color(0, 0, 128), Color.BLUE, new Color(128, 0, 128),
        Color.MAGENTA, Color.PINK, new Color(128, 128, 0), new Color(0, 64, 0),
    };

    public ColorPicker(DrawingCanvas canvas) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 1. Title
        JLabel label = new JLabel("Colors");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(CENTER_ALIGNMENT);
        add(label);
        add(Box.createVerticalStrut(6));

        // 2. Fg/Bg swatches with Swap and Reset
        JPanel swatchRow = new JPanel();
        swatchRow.setLayout(new BoxLayout(swatchRow, BoxLayout.X_AXIS));
        swatchRow.setAlignmentX(CENTER_ALIGNMENT);

        JPanel swatchContainer = new JPanel(null);
        swatchContainer.setPreferredSize(new Dimension(50, 42));
        swatchContainer.setMinimumSize(new Dimension(50, 42));
        swatchContainer.setMaximumSize(new Dimension(50, 42));

        bgSwatch = createSwatch(backgroundColor);
        bgSwatch.setBounds(14, 12, 26, 26);
        bgSwatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Color c = JColorChooser.showDialog(ColorPicker.this, "Background Color", backgroundColor);
                    if (c != null) setBackgroundColor(c);
                }
            }
        });

        fgSwatch = createSwatch(foregroundColor);
        fgSwatch.setBounds(4, 4, 26, 26);
        fgSwatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Color c = JColorChooser.showDialog(ColorPicker.this, "Foreground Color", foregroundColor);
                    if (c != null) setForegroundColor(c);
                }
            }
        });

        swatchContainer.add(fgSwatch);
        swatchContainer.add(bgSwatch);

        JButton swapButton = new JButton("\u21C4");
        swapButton.setMargin(new Insets(1, 3, 1, 3));
        swapButton.setFont(swapButton.getFont().deriveFont(10f));
        swapButton.setToolTipText("Swap foreground and background");
        swapButton.setFocusable(false);
        swapButton.addActionListener(e -> {
            Color tmp = foregroundColor;
            setForegroundColor(backgroundColor);
            setBackgroundColor(tmp);
        });

        JButton resetButton = new JButton("D");
        resetButton.setMargin(new Insets(1, 3, 1, 3));
        resetButton.setFont(resetButton.getFont().deriveFont(10f));
        resetButton.setToolTipText("Reset to default (black/white)");
        resetButton.setFocusable(false);
        resetButton.addActionListener(e -> {
            setForegroundColor(Color.BLACK);
            setBackgroundColor(Color.WHITE);
        });

        swatchRow.add(swatchContainer);
        swatchRow.add(Box.createHorizontalStrut(4));
        swatchRow.add(swapButton);
        swatchRow.add(Box.createHorizontalStrut(2));
        swatchRow.add(resetButton);
        swatchRow.add(Box.createHorizontalGlue());

        add(swatchRow);
        add(Box.createVerticalStrut(6));

        // 3. HSB Color Panel
        hsbPanel = new HSBPanel();
        hsbPanel.setAlignmentX(CENTER_ALIGNMENT);
        hsbPanel.addPropertyChangeListener("color", evt -> {
            if (!updatingFromCode) {
                updatingFromCode = true;
                setForegroundColor(hsbPanel.getColor());
                updatingFromCode = false;
            }
        });
        add(hsbPanel);
        add(Box.createVerticalStrut(4));

        // 4. Brightness Slider
        JPanel brightnessRow = new JPanel();
        brightnessRow.setLayout(new BoxLayout(brightnessRow, BoxLayout.X_AXIS));
        brightnessRow.setAlignmentX(CENTER_ALIGNMENT);
        JLabel briLabel = new JLabel("B:");
        briLabel.setFont(briLabel.getFont().deriveFont(10f));
        brightnessSlider = new JSlider(0, 100, 100);
        brightnessSlider.setPreferredSize(new Dimension(140, 20));
        brightnessSlider.setMaximumSize(new Dimension(160, 20));
        brightnessSlider.addChangeListener(e -> {
            if (!updatingFromCode) {
                updatingFromCode = true;
                float bri = brightnessSlider.getValue() / 100f;
                hsbPanel.setBrightness(bri);
                setForegroundColor(hsbPanel.getColor());
                updatingFromCode = false;
            }
        });
        brightnessRow.add(briLabel);
        brightnessRow.add(Box.createHorizontalStrut(2));
        brightnessRow.add(brightnessSlider);
        add(brightnessRow);
        add(Box.createVerticalStrut(4));

        // 5. Hex Input
        hexField = new JTextField(formatHex(foregroundColor), 7);
        hexField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        hexField.setMaximumSize(new Dimension(160, 24));
        hexField.setAlignmentX(CENTER_ALIGNMENT);
        hexField.setHorizontalAlignment(JTextField.CENTER);
        hexField.addActionListener(e -> {
            String text = hexField.getText().trim();
            if (!text.startsWith("#")) text = "#" + text;
            try {
                Color c = Color.decode(text);
                setForegroundColor(c);
            } catch (NumberFormatException ex) {
                hexField.setText(formatHex(foregroundColor));
            }
        });
        add(hexField);
        add(Box.createVerticalStrut(6));

        // 6. Swatch Palette
        JPanel palettePanel = new JPanel(new GridLayout(4, 5, 1, 1));
        palettePanel.setAlignmentX(CENTER_ALIGNMENT);
        palettePanel.setMaximumSize(new Dimension(160, 56));
        for (Color color : PALETTE) {
            JPanel cell = createSwatch(color);
            cell.setPreferredSize(new Dimension(14, 14));
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
        add(palettePanel);
        add(Box.createVerticalStrut(4));

        // 7. Recent Colors Row
        JLabel recentLabel = new JLabel("Recent:");
        recentLabel.setFont(recentLabel.getFont().deriveFont(9f));
        recentLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(recentLabel);
        add(Box.createVerticalStrut(2));

        recentPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        recentPanel.setAlignmentX(CENTER_ALIGNMENT);
        recentPanel.setMaximumSize(new Dimension(160, 16));
        add(recentPanel);

        // Constrain overall size for dockable panel
        Dimension pref = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height + 50));
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

    private String formatHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void updateHSBPanelFromColor(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsbPanel.setFromColor(c);
        brightnessSlider.setValue(Math.round(hsb[2] * 100));
    }

    private void addRecentColor(Color c) {
        // Don't add black or white as recent (they're always available)
        if (c.equals(Color.BLACK) || c.equals(Color.WHITE)) return;
        recentColors.remove(c);
        recentColors.add(0, c);
        if (recentColors.size() > MAX_RECENT) {
            recentColors.remove(recentColors.size() - 1);
        }
        rebuildRecentPanel();
    }

    private void rebuildRecentPanel() {
        recentPanel.removeAll();
        for (Color c : recentColors) {
            JPanel cell = new JPanel();
            cell.setBackground(c);
            cell.setPreferredSize(new Dimension(12, 12));
            cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        setForegroundColor(c);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        setBackgroundColor(c);
                    }
                }
            });
            recentPanel.add(cell);
        }
        recentPanel.revalidate();
        recentPanel.repaint();
    }

    // --- Public API (unchanged) ---

    public Color getForegroundColor() { return foregroundColor; }
    public Color getBackgroundColor() { return backgroundColor; }

    public void setForegroundColor(Color c) {
        Color old = foregroundColor;
        foregroundColor = c;
        fgSwatch.setBackground(c);
        hexField.setText(formatHex(c));
        if (!updatingFromCode) {
            updatingFromCode = true;
            updateHSBPanelFromColor(c);
            updatingFromCode = false;
        }
        addRecentColor(c);
        pcs.firePropertyChange(PROP_FOREGROUND_COLOR, old, c);
    }

    public void setBackgroundColor(Color c) {
        backgroundColor = c;
        bgSwatch.setBackground(c);
    }

    public void addColorPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removeColorPropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // --- HSB Panel (inner class) ---

    static class HSBPanel extends JPanel {
        private float hue = 0f;
        private float sat = 1f;
        private float bri = 1f;
        private BufferedImage gradientImage;

        HSBPanel() {
            setPreferredSize(new Dimension(160, 100));
            setMinimumSize(new Dimension(160, 100));
            setMaximumSize(new Dimension(160, 100));
            setBorder(BorderFactory.createLineBorder(Color.GRAY));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            rebuildGradient();

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { pickColor(e); }
                @Override
                public void mouseDragged(MouseEvent e) { pickColor(e); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void pickColor(MouseEvent e) {
            hue = (float) e.getX() / getWidth();
            sat = 1f - (float) e.getY() / getHeight();
            hue = Math.max(0, Math.min(1, hue));
            sat = Math.max(0, Math.min(1, sat));
            repaint();
            firePropertyChange("color", null, getColor());
        }

        Color getColor() { return Color.getHSBColor(hue, sat, bri); }

        void setBrightness(float b) {
            this.bri = b;
            rebuildGradient();
            repaint();
        }

        void setFromColor(Color c) {
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            hue = hsb[0];
            sat = hsb[1];
            bri = hsb[2];
            rebuildGradient();
            repaint();
        }

        private void rebuildGradient() {
            int w = 160, h = 100;
            gradientImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    float h2 = (float) x / w;
                    float s2 = 1f - (float) y / h;
                    gradientImage.setRGB(x, y, Color.HSBtoRGB(h2, s2, bri));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (gradientImage != null) {
                g.drawImage(gradientImage, 0, 0, getWidth(), getHeight(), null);
            }
            int cx = (int) (hue * getWidth());
            int cy = (int) ((1 - sat) * getHeight());
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.WHITE);
            g2.drawOval(cx - 4, cy - 4, 8, 8);
            g2.setColor(Color.BLACK);
            g2.drawOval(cx - 5, cy - 5, 10, 10);
        }
    }
}

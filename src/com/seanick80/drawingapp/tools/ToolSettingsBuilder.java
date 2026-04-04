package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.gradient.GradientToolbar;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reusable UI component builders for tool settings panels. */
public class ToolSettingsBuilder {

    public static JSpinner createStrokeSpinner(int defaultSize, Consumer<Integer> onChange) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultSize, 1, 50, 1));
        spinner.setMaximumSize(new Dimension(120, 28));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (onChange != null) {
            spinner.addChangeListener(e -> onChange.accept((int) spinner.getValue()));
        }
        return spinner;
    }

    public static JPanel createStrokePreview(Supplier<Integer> sizeSupplier) {
        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = sizeSupplier.get();
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.setColor(Color.BLACK);
                g2.fillOval(cx - size / 2, cy - size / 2, size, size);
            }
        };
        preview.setPreferredSize(new Dimension(54, 54));
        preview.setMinimumSize(new Dimension(54, 54));
        preview.setMaximumSize(new Dimension(54, 54));
        preview.setBackground(Color.WHITE);
        preview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        return preview;
    }

    public static JPanel createStrokeSizePanel(int defaultSize, Consumer<Integer> onChange) {
        JSpinner spinner = createStrokeSpinner(defaultSize, null);
        JPanel preview = createStrokePreview(() -> (int) spinner.getValue());

        if (onChange != null) {
            spinner.addChangeListener(e -> {
                onChange.accept((int) spinner.getValue());
                preview.repaint();
            });
        } else {
            spinner.addChangeListener(e -> preview.repaint());
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 11f));
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sizeLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(spinner);
        panel.add(Box.createVerticalStrut(4));
        panel.add(preview);
        return panel;
    }

    /** Returns the spinner component from a stroke size panel (first JSpinner child). */
    public static JSpinner getSpinnerFrom(JPanel strokeSizePanel) {
        for (Component c : strokeSizePanel.getComponents()) {
            if (c instanceof JSpinner) return (JSpinner) c;
        }
        return null;
    }

    /**
     * Creates a stroke style panel with a combo box for selecting stroke styles
     * and a preview of the current stroke.
     */
    public static JPanel createStrokeStylePanel(StrokeStyle defaultStyle, Consumer<StrokeStyle> onChange) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel styleLabel = new JLabel("Stroke:");
        styleLabel.setFont(styleLabel.getFont().deriveFont(Font.BOLD, 11f));
        styleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(styleLabel);
        panel.add(Box.createVerticalStrut(2));

        JComboBox<StrokeStyle> styleCombo = new JComboBox<>(StrokeStyle.values());
        styleCombo.setSelectedItem(defaultStyle);
        styleCombo.setMaximumSize(new Dimension(120, 28));
        styleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                StrokeStyle style = (StrokeStyle) styleCombo.getSelectedItem();
                g2.setStroke(style.createStroke(2));
                g2.setColor(Color.BLACK);
                int cy = getHeight() / 2;
                g2.drawLine(4, cy, getWidth() - 4, cy);
            }
        };
        preview.setPreferredSize(new Dimension(120, 20));
        preview.setMinimumSize(new Dimension(120, 20));
        preview.setMaximumSize(new Dimension(120, 20));
        preview.setBackground(Color.WHITE);
        preview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleCombo.addActionListener(e -> {
            if (onChange != null) onChange.accept((StrokeStyle) styleCombo.getSelectedItem());
            preview.repaint();
        });

        panel.add(styleCombo);
        panel.add(Box.createVerticalStrut(4));
        panel.add(preview);
        return panel;
    }

    /**
     * Creates a fill options panel with checkbox, fill type combo, angle dial,
     * and gradient preview.
     *
     * @param fillRegistry      registry of available fill providers
     * @param gradientToolbar   the gradient toolbar (may be null)
     * @param showFilledCheck   whether to show the "Filled" checkbox (false for FillTool)
     * @param onFilledChanged   callback when filled checkbox changes (may be null)
     * @param onFillChanged     callback when fill provider changes
     */
    public static JPanel createFillOptionsPanel(
            FillRegistry fillRegistry,
            GradientToolbar gradientToolbar,
            boolean showFilledCheck,
            Consumer<Boolean> onFilledChanged,
            Consumer<FillProvider> onFillChanged) {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fillLabel = new JLabel("Fill:");
        fillLabel.setFont(fillLabel.getFont().deriveFont(Font.BOLD, 11f));
        fillLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fillLabel);
        panel.add(Box.createVerticalStrut(2));

        JCheckBox filledCheck = new JCheckBox("Filled");
        filledCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (showFilledCheck) {
            panel.add(filledCheck);
        }

        JComboBox<String> fillCombo = new JComboBox<>();
        for (FillProvider fp : fillRegistry.getAll()) {
            fillCombo.addItem(fp.getName());
        }
        fillCombo.setMaximumSize(new Dimension(120, 28));
        fillCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fillCombo);

        JPanel gradientPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                FillProvider fp = fillRegistry.getByName((String) fillCombo.getSelectedItem());
                if (fp instanceof CustomGradientFill cgf) {
                    ColorGradient grad = cgf.getGradient();
                    int w = getWidth();
                    for (int px = 0; px < w; px++) {
                        float t = (float) px / Math.max(1, w - 1);
                        g.setColor(grad.getColorAt(t));
                        g.drawLine(px, 0, px, getHeight());
                    }
                }
            }
        };
        gradientPreview.setPreferredSize(new Dimension(120, 20));
        gradientPreview.setMinimumSize(new Dimension(120, 20));
        gradientPreview.setMaximumSize(new Dimension(120, 20));
        gradientPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        gradientPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JButton editGradientBtn = new JButton("Edit Gradient...");
        editGradientBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        editGradientBtn.setMaximumSize(new Dimension(120, 28));
        editGradientBtn.setFont(editGradientBtn.getFont().deriveFont(10f));
        editGradientBtn.addActionListener(e -> {
            FillProvider fp = fillRegistry.getByName((String) fillCombo.getSelectedItem());
            if (fp instanceof CustomGradientFill cgf && gradientToolbar != null) {
                gradientToolbar.setGradient(cgf.getGradient());
                gradientToolbar.setChangeCallback(gradientPreview::repaint);
            }
        });

        JLabel angleLabel = new JLabel("Angle:");
        angleLabel.setFont(angleLabel.getFont().deriveFont(Font.BOLD, 11f));
        angleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int dialSize = 54;
        int[] dialAngle = {0};
        JPanel angleDial = new JPanel() {
            {
                setPreferredSize(new Dimension(dialSize, dialSize));
                setMinimumSize(new Dimension(dialSize, dialSize));
                setMaximumSize(new Dimension(dialSize, dialSize));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                MouseAdapter mouse = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) { updateAngle(e); }
                    @Override
                    public void mouseDragged(MouseEvent e) { updateAngle(e); }
                };
                addMouseListener(mouse);
                addMouseMotionListener(mouse);
            }

            private void updateAngle(MouseEvent e) {
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int dx = e.getX() - cx;
                int dy = e.getY() - cy;
                if (dx == 0 && dy == 0) return;
                dialAngle[0] = (int) Math.round(Math.toDegrees(Math.atan2(dy, dx)));
                if (dialAngle[0] < 0) dialAngle[0] += 360;
                FillProvider fp = fillRegistry.getByName((String) fillCombo.getSelectedItem());
                if (fp instanceof AngledFillProvider afp) {
                    afp.setAngleDegrees(dialAngle[0]);
                }
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int radius = Math.min(cx, cy) - 3;

                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

                g2.setColor(Color.WHITE);
                g2.fillOval(cx - radius + 1, cy - radius + 1, radius * 2 - 2, radius * 2 - 2);

                double rad = Math.toRadians(dialAngle[0]);
                int lx = cx + (int) (radius * Math.cos(rad));
                int ly = cy + (int) (radius * Math.sin(rad));
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2));
                g2.drawLine(cx, cy, lx, ly);

                g2.fillOval(cx - 3, cy - 3, 6, 6);

                g2.setColor(Color.RED);
                g2.fillOval(lx - 3, ly - 3, 6, 6);

                g2.setColor(Color.DARK_GRAY);
                g2.setFont(g2.getFont().deriveFont(9f));
                String text = dialAngle[0] + "\u00B0";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, cx - fm.stringWidth(text) / 2, getHeight() - 1);
            }
        };
        angleDial.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Initially hidden
        boolean hasGradToolbar = gradientToolbar != null;
        gradientPreview.setVisible(false);
        editGradientBtn.setVisible(false);
        angleLabel.setVisible(false);
        angleDial.setVisible(false);

        // Notify callback helper
        Runnable notifyChanges = () -> {
            String name = (String) fillCombo.getSelectedItem();
            FillProvider fp = fillRegistry.getByName(name);
            if (onFillChanged != null) onFillChanged.accept(fp);
            if (onFilledChanged != null) onFilledChanged.accept(filledCheck.isSelected());
        };

        fillCombo.addActionListener(e -> {
            String name = (String) fillCombo.getSelectedItem();
            FillProvider fp = fillRegistry.getByName(name);
            boolean isCustom = fp instanceof CustomGradientFill;
            boolean hasAngle = fp instanceof AngledFillProvider;
            gradientPreview.setVisible(isCustom && !hasGradToolbar);
            editGradientBtn.setVisible(isCustom && !hasGradToolbar);
            angleLabel.setVisible(hasAngle);
            angleDial.setVisible(hasAngle);
            if (fp instanceof AngledFillProvider afp) {
                dialAngle[0] = afp.getAngleDegrees();
                angleDial.repaint();
            }
            if (isCustom && gradientToolbar != null) {
                FillProvider cfp = fillRegistry.getByName(name);
                if (cfp instanceof CustomGradientFill cgf) {
                    gradientToolbar.setGradient(cgf.getGradient());
                    gradientToolbar.setChangeCallback(gradientPreview::repaint);
                }
            }
            notifyChanges.run();
        });

        filledCheck.addActionListener(e -> notifyChanges.run());

        panel.add(Box.createVerticalStrut(4));
        panel.add(gradientPreview);
        panel.add(Box.createVerticalStrut(2));
        panel.add(editGradientBtn);
        panel.add(Box.createVerticalStrut(4));
        panel.add(angleLabel);
        panel.add(angleDial);
        return panel;
    }
}

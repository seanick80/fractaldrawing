package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.gradient.*;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

public class ToolBar extends JPanel implements ToolSettingsContext {

    private final DrawingCanvas canvas;
    private final Map<String, Tool> tools = new HashMap<>();
    private final Map<String, Integer> toolSizes = new HashMap<>();
    private Tool activeTool;
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final FillRegistry fillRegistry;
    private java.util.function.BiConsumer<Tool, Tool> toolChangeListener;

    // Reusable settings components
    private final JSpinner strokeSpinner;
    private final JPanel strokePreview;
    private final JPanel strokeSizePanel;
    private final JCheckBox filledCheck;
    private final JComboBox<String> fillCombo;
    private final JPanel fillOptionsPanel;

    // Container that swaps content per tool
    private final JPanel toolSettingsContainer;

    public ToolBar(DrawingCanvas canvas, FillRegistry fillRegistry) {
        this.canvas = canvas;
        this.fillRegistry = fillRegistry;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel toolLabel = new JLabel("Tools");
        toolLabel.setFont(toolLabel.getFont().deriveFont(Font.BOLD, 11f));
        toolLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(toolLabel);
        add(Box.createVerticalStrut(4));

        JPanel buttonPanel = new JPanel(new GridLayout(4, 2, 2, 2));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(120, 130));

        // Build reusable components before adding tools (tools reference them via context)
        strokeSpinner = buildStrokeSpinner();
        strokePreview = buildStrokePreview();
        strokeSizePanel = buildStrokeSizePanel();
        filledCheck = new JCheckBox("Filled");
        fillCombo = buildFillCombo();
        fillOptionsPanel = buildFillOptionsPanel();

        addTool(buttonPanel, new PencilTool(), true);
        addTool(buttonPanel, new LineTool(), false);
        addTool(buttonPanel, new RectangleTool(), false);
        addTool(buttonPanel, new OvalTool(), false);
        addTool(buttonPanel, new EraserTool(), false);
        addTool(buttonPanel, new FillTool(), false);
        addTool(buttonPanel, new FractalTool(), false);

        add(buttonPanel);
        add(Box.createVerticalStrut(12));

        // Tool Settings container
        JLabel settingsLabel = new JLabel("Tool Settings");
        settingsLabel.setFont(settingsLabel.getFont().deriveFont(Font.BOLD, 11f));
        settingsLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(settingsLabel);
        add(Box.createVerticalStrut(4));

        toolSettingsContainer = new JPanel();
        toolSettingsContainer.setLayout(new BorderLayout());
        toolSettingsContainer.setAlignmentX(LEFT_ALIGNMENT);
        add(toolSettingsContainer);

        // Show initial tool's settings
        refreshSettingsPanel();
    }

    // --- ToolSettingsContext implementation ---

    @Override
    public JPanel getStrokeSizePanel() {
        return strokeSizePanel;
    }

    @Override
    public JPanel getFillOptionsPanel() {
        return fillOptionsPanel;
    }

    // --- Build reusable components ---

    private JSpinner buildStrokeSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));
        spinner.setMaximumSize(new Dimension(120, 28));
        spinner.setAlignmentX(LEFT_ALIGNMENT);
        spinner.addChangeListener(e -> {
            applyStrokeSize();
            strokePreview.repaint();
        });
        return spinner;
    }

    private JPanel buildStrokePreview() {
        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = (int) strokeSpinner.getValue();
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
        preview.setAlignmentX(LEFT_ALIGNMENT);
        return preview;
    }

    private JPanel buildStrokeSizePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 11f));
        sizeLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(sizeLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(strokeSpinner);
        panel.add(Box.createVerticalStrut(4));
        panel.add(strokePreview);
        return panel;
    }

    private JComboBox<String> buildFillCombo() {
        JComboBox<String> combo = new JComboBox<>();
        for (FillProvider fp : fillRegistry.getAll()) {
            combo.addItem(fp.getName());
        }
        combo.setMaximumSize(new Dimension(120, 28));
        combo.setAlignmentX(LEFT_ALIGNMENT);
        combo.addActionListener(e -> applyFillSettings());
        return combo;
    }

    private JPanel buildFillOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel fillLabel = new JLabel("Fill:");
        fillLabel.setFont(fillLabel.getFont().deriveFont(Font.BOLD, 11f));
        fillLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(fillLabel);
        panel.add(Box.createVerticalStrut(2));

        filledCheck.setAlignmentX(LEFT_ALIGNMENT);
        filledCheck.addActionListener(e -> applyFillSettings());
        panel.add(filledCheck);
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
        gradientPreview.setAlignmentX(LEFT_ALIGNMENT);
        gradientPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JButton editGradientBtn = new JButton("Edit Gradient...");
        editGradientBtn.setAlignmentX(LEFT_ALIGNMENT);
        editGradientBtn.setMaximumSize(new Dimension(120, 28));
        editGradientBtn.setFont(editGradientBtn.getFont().deriveFont(10f));
        editGradientBtn.addActionListener(e -> {
            FillProvider fp = fillRegistry.getByName((String) fillCombo.getSelectedItem());
            if (fp instanceof CustomGradientFill cgf) {
                ColorGradient result = GradientEditorDialog.showDialog(
                    SwingUtilities.getWindowAncestor(this), cgf.getGradient());
                if (result != null) {
                    cgf.setGradient(result);
                    gradientPreview.repaint();
                }
            }
        });

        JLabel angleLabel = new JLabel("Angle:");
        angleLabel.setFont(angleLabel.getFont().deriveFont(Font.BOLD, 11f));
        angleLabel.setAlignmentX(LEFT_ALIGNMENT);

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
        angleDial.setAlignmentX(LEFT_ALIGNMENT);

        // Show/hide controls based on fill selection
        gradientPreview.setVisible(false);
        editGradientBtn.setVisible(false);
        angleLabel.setVisible(false);
        angleDial.setVisible(false);
        fillCombo.addActionListener(e -> {
            String name = (String) fillCombo.getSelectedItem();
            FillProvider fp = fillRegistry.getByName(name);
            boolean isCustom = fp instanceof CustomGradientFill;
            boolean hasAngle = fp instanceof AngledFillProvider;
            gradientPreview.setVisible(isCustom);
            editGradientBtn.setVisible(isCustom);
            angleLabel.setVisible(hasAngle);
            angleDial.setVisible(hasAngle);
            // Sync dial to the fill's current angle
            if (fp instanceof AngledFillProvider afp) {
                dialAngle[0] = afp.getAngleDegrees();
                angleDial.repaint();
            }
        });

        panel.add(Box.createVerticalStrut(4));
        panel.add(gradientPreview);
        panel.add(Box.createVerticalStrut(2));
        panel.add(editGradientBtn);
        panel.add(Box.createVerticalStrut(4));
        panel.add(angleLabel);
        panel.add(angleDial);
        return panel;
    }

    // --- Tool management ---

    public void setToolChangeListener(java.util.function.BiConsumer<Tool, Tool> listener) {
        this.toolChangeListener = listener;
    }

    private void addTool(JPanel panel, Tool tool, boolean selected) {
        tools.put(tool.getName(), tool);
        JToggleButton btn = new JToggleButton(tool.getName());
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setMargin(new Insets(4, 2, 4, 2));
        toolGroup.add(btn);
        btn.addActionListener(e -> {
            Tool oldTool = activeTool;
            if (activeTool != null) {
                toolSizes.put(activeTool.getName(), (int) strokeSpinner.getValue());
                activeTool.onDeactivated();
            }
            activeTool = tool;
            canvas.setActiveTool(tool);
            int size = toolSizes.getOrDefault(tool.getName(), tool.getDefaultStrokeSize());
            strokeSpinner.setValue(size);
            refreshSettingsPanel();
            applyStrokeSize();
            applyFillSettings();
            tool.onActivated(canvas.getActiveLayerImage(), canvas);
            if (toolChangeListener != null) {
                toolChangeListener.accept(oldTool, tool);
            }
        });
        panel.add(btn);
        if (selected) {
            btn.setSelected(true);
            activeTool = tool;
        }
    }

    private void refreshSettingsPanel() {
        toolSettingsContainer.removeAll();
        JPanel custom = activeTool.createSettingsPanel(this);
        if (custom != null) {
            toolSettingsContainer.add(custom, BorderLayout.CENTER);
        } else {
            // Default: just the stroke size panel
            toolSettingsContainer.add(strokeSizePanel, BorderLayout.CENTER);
        }
        toolSettingsContainer.revalidate();
        toolSettingsContainer.repaint();
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    private void applyStrokeSize() {
        int size = (int) strokeSpinner.getValue();
        if (activeTool instanceof PencilTool pt) pt.setStrokeSize(size);
        if (activeTool instanceof LineTool lt) lt.setStrokeSize(size);
        if (activeTool instanceof RectangleTool rt) rt.setStrokeSize(size);
        if (activeTool instanceof OvalTool ot) ot.setStrokeSize(size);
        if (activeTool instanceof EraserTool et) et.setSize(size);
    }

    private void applyFillSettings() {
        boolean filled = filledCheck.isSelected();
        String fillName = (String) fillCombo.getSelectedItem();
        FillProvider fp = fillRegistry.getByName(fillName);

        if (activeTool instanceof RectangleTool rt) {
            rt.setFilled(filled);
            rt.setFillProvider(fp);
        }
        if (activeTool instanceof OvalTool ot) {
            ot.setFilled(filled);
            ot.setFillProvider(fp);
        }
        if (activeTool instanceof FillTool ft) {
            ft.setFillProvider(fp);
        }
    }
}

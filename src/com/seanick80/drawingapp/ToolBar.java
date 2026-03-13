package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ToolBar extends JPanel {

    private final DrawingCanvas canvas;
    private final Map<String, Tool> tools = new HashMap<>();
    private Tool activeTool;
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final JCheckBox filledCheck;
    private final JComboBox<String> fillCombo;
    private final JSpinner strokeSpinner;
    private final FillRegistry fillRegistry;

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

        JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 2, 2));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(120, 100));

        addTool(buttonPanel, new PencilTool(), true);
        addTool(buttonPanel, new LineTool(), false);
        addTool(buttonPanel, new RectangleTool(), false);
        addTool(buttonPanel, new OvalTool(), false);
        addTool(buttonPanel, new EraserTool(), false);
        addTool(buttonPanel, new FillTool(), false);

        add(buttonPanel);
        add(Box.createVerticalStrut(12));

        // Stroke size
        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 11f));
        sizeLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(sizeLabel);
        add(Box.createVerticalStrut(2));

        strokeSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));
        strokeSpinner.setMaximumSize(new Dimension(120, 28));
        strokeSpinner.setAlignmentX(LEFT_ALIGNMENT);
        strokeSpinner.addChangeListener(e -> updateStrokeSize());
        add(strokeSpinner);
        add(Box.createVerticalStrut(12));

        // Shape fill options
        JLabel fillLabel = new JLabel("Shape Fill");
        fillLabel.setFont(fillLabel.getFont().deriveFont(Font.BOLD, 11f));
        fillLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(fillLabel);
        add(Box.createVerticalStrut(2));

        filledCheck = new JCheckBox("Filled");
        filledCheck.setAlignmentX(LEFT_ALIGNMENT);
        filledCheck.addActionListener(e -> updateFillSettings());
        add(filledCheck);

        fillCombo = new JComboBox<>();
        for (FillProvider fp : fillRegistry.getAll()) {
            fillCombo.addItem(fp.getName());
        }
        fillCombo.setMaximumSize(new Dimension(120, 28));
        fillCombo.setAlignmentX(LEFT_ALIGNMENT);
        fillCombo.addActionListener(e -> updateFillSettings());
        add(fillCombo);
    }

    private void addTool(JPanel panel, Tool tool, boolean selected) {
        tools.put(tool.getName(), tool);
        JToggleButton btn = new JToggleButton(tool.getName());
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setMargin(new Insets(4, 2, 4, 2));
        toolGroup.add(btn);
        btn.addActionListener(e -> {
            activeTool = tool;
            canvas.setActiveTool(tool);
            updateStrokeSize();
            updateFillSettings();
        });
        panel.add(btn);
        if (selected) {
            btn.setSelected(true);
            activeTool = tool;
        }
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    private void updateStrokeSize() {
        int size = (int) strokeSpinner.getValue();
        if (activeTool instanceof PencilTool) ((PencilTool) activeTool).setStrokeSize(size);
        if (activeTool instanceof LineTool) ((LineTool) activeTool).setStrokeSize(size);
        if (activeTool instanceof RectangleTool) ((RectangleTool) activeTool).setStrokeSize(size);
        if (activeTool instanceof OvalTool) ((OvalTool) activeTool).setStrokeSize(size);
        if (activeTool instanceof EraserTool) ((EraserTool) activeTool).setSize(size);
    }

    private void updateFillSettings() {
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
    }
}

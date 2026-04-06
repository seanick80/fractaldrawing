package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.gradient.*;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ToolBar extends JPanel implements ToolSettingsContext {

    private final DrawingCanvas canvas;
    private final Map<String, Tool> tools = new HashMap<>();
    private Tool activeTool;
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final FillRegistry fillRegistry;
    private final GradientToolbar gradientToolbar;
    private java.util.function.BiConsumer<Tool, Tool> toolChangeListener;

    // Container that swaps content per tool
    private final JPanel toolSettingsContainer;

    public ToolBar(DrawingCanvas canvas, FillRegistry fillRegistry, GradientToolbar gradientToolbar) {
        this.canvas = canvas;
        this.fillRegistry = fillRegistry;
        this.gradientToolbar = gradientToolbar;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel toolLabel = new JLabel("Tools");
        toolLabel.setFont(toolLabel.getFont().deriveFont(Font.BOLD, 11f));
        toolLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(toolLabel);
        add(Box.createVerticalStrut(4));

        JPanel buttonPanel = new JPanel(new GridLayout(7, 2, 2, 2));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(140, 220));

        addTool(buttonPanel, new SelectionTool(), false);
        addTool(buttonPanel, new PencilTool(), true);
        addTool(buttonPanel, new PaintbrushTool(), false);
        addTool(buttonPanel, new LineTool(), false);
        addTool(buttonPanel, new RectangleTool(), false);
        addTool(buttonPanel, new OvalTool(), false);
        addTool(buttonPanel, new EraserTool(), false);
        addTool(buttonPanel, new FillTool(), false);
        addTool(buttonPanel, new EyedropperTool(), false);
        addTool(buttonPanel, new MagicWandTool(), false);
        addTool(buttonPanel, new LassoTool(), false);
        FractalTool fractalTool = new FractalTool();
        fractalTool.setGradientToolbar(gradientToolbar);
        addTool(buttonPanel, fractalTool, false);

        add(buttonPanel);

        // Tool settings container (managed here, displayed externally via DockablePanel)
        toolSettingsContainer = new JPanel();
        toolSettingsContainer.setLayout(new BorderLayout());
        toolSettingsContainer.setAlignmentX(LEFT_ALIGNMENT);

        // Show initial tool's settings
        refreshSettingsPanel();
    }

    // --- ToolSettingsContext implementation ---

    @Override
    public FillRegistry getFillRegistry() {
        return fillRegistry;
    }

    @Override
    public GradientToolbar getGradientToolbar() {
        return gradientToolbar;
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
                activeTool.onDeactivated();
            }
            activeTool = tool;
            canvas.setActiveTool(tool);
            refreshSettingsPanel();
            syncGradientToolbar();
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
        }
        toolSettingsContainer.revalidate();
        toolSettingsContainer.repaint();
    }

    /** Returns the tool settings panel (managed by ToolBar, displayed externally). */
    public JPanel getToolSettingsContainer() {
        return toolSettingsContainer;
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    /** Set the gradient toolbar's change callback based on the active tool. */
    private void syncGradientToolbar() {
        if (gradientToolbar == null) return;
        gradientToolbar.setChangeCallback(activeTool.getGradientChangeCallback());
    }
}

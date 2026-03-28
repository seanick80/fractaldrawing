package com.seanick80.drawingapp.layers;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Sidebar panel for layer management: list, add, delete, reorder,
 * opacity slider, blend mode dropdown.
 */
public class LayerPanel extends JPanel implements LayerManager.LayerChangeListener {

    private static final int THUMB_W = 40;
    private static final int THUMB_H = 30;

    private final LayerManager layerManager;
    private final JPanel layerListPanel;
    private final JSlider opacitySlider;
    private final JLabel opacityLabel;
    private final JComboBox<BlendMode> blendCombo;
    private final Runnable repaintCanvas;

    public LayerPanel(LayerManager layerManager, Runnable repaintCanvas) {
        this.layerManager = layerManager;
        this.repaintCanvas = repaintCanvas;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Header
        JLabel header = new JLabel("Layers");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(4));

        // Toolbar buttons
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttonBar.setAlignmentX(LEFT_ALIGNMENT);
        buttonBar.setMaximumSize(new Dimension(160, 28));

        JButton addBtn = smallButton("+", "Add layer");
        addBtn.addActionListener(e -> {
            if (layerManager.addLayer() == null) {
                JOptionPane.showMessageDialog(this,
                    "Maximum " + LayerManager.MAX_LAYERS + " layers reached.",
                    "Layer Limit", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton delBtn = smallButton("-", "Delete layer");
        delBtn.addActionListener(e -> {
            if (layerManager.getLayerCount() <= 1) return;
            layerManager.removeLayer(layerManager.getActiveIndex());
        });

        JButton upBtn = smallButton("\u25B2", "Move up");
        upBtn.addActionListener(e -> layerManager.moveLayerUp(layerManager.getActiveIndex()));

        JButton downBtn = smallButton("\u25BC", "Move down");
        downBtn.addActionListener(e -> layerManager.moveLayerDown(layerManager.getActiveIndex()));

        JButton dupBtn = smallButton("D", "Duplicate layer");
        dupBtn.addActionListener(e -> {
            if (layerManager.duplicateLayer(layerManager.getActiveIndex()) == null) {
                JOptionPane.showMessageDialog(this,
                    "Maximum " + LayerManager.MAX_LAYERS + " layers reached.",
                    "Layer Limit", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton mergeBtn = smallButton("M", "Merge down");
        mergeBtn.addActionListener(e -> {
            if (layerManager.getActiveIndex() > 0) {
                layerManager.mergeDown(layerManager.getActiveIndex());
            }
        });

        JButton flatBtn = smallButton("F", "Flatten all");
        flatBtn.addActionListener(e -> layerManager.flattenAll());

        buttonBar.add(addBtn);
        buttonBar.add(delBtn);
        buttonBar.add(upBtn);
        buttonBar.add(downBtn);
        buttonBar.add(dupBtn);
        buttonBar.add(mergeBtn);
        buttonBar.add(flatBtn);
        add(buttonBar);
        add(Box.createVerticalStrut(4));

        // Layer list (scrollable, top = highest layer)
        layerListPanel = new JPanel();
        layerListPanel.setLayout(new BoxLayout(layerListPanel, BoxLayout.Y_AXIS));
        JScrollPane listScroll = new JScrollPane(layerListPanel);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(160, 150));
        listScroll.setMaximumSize(new Dimension(160, 300));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(listScroll);
        add(Box.createVerticalStrut(4));

        // Opacity
        opacityLabel = new JLabel("Opacity: 100%");
        opacityLabel.setFont(opacityLabel.getFont().deriveFont(10f));
        opacityLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(opacityLabel);

        opacitySlider = new JSlider(0, 100, 100);
        opacitySlider.setAlignmentX(LEFT_ALIGNMENT);
        opacitySlider.setMaximumSize(new Dimension(160, 24));
        opacitySlider.addChangeListener(e -> {
            Layer active = layerManager.getActiveLayer();
            int val = opacitySlider.getValue();
            active.setOpacity(val / 100f);
            opacityLabel.setText("Opacity: " + val + "%");
            if (!opacitySlider.getValueIsAdjusting()) {
                repaintCanvas.run();
            }
        });
        add(opacitySlider);
        add(Box.createVerticalStrut(4));

        // Blend mode
        JLabel blendLabel = new JLabel("Blend:");
        blendLabel.setFont(blendLabel.getFont().deriveFont(Font.BOLD, 10f));
        blendLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(blendLabel);

        blendCombo = new JComboBox<>(BlendMode.values());
        blendCombo.setAlignmentX(LEFT_ALIGNMENT);
        blendCombo.setMaximumSize(new Dimension(160, 24));
        blendCombo.setFont(blendCombo.getFont().deriveFont(10f));
        blendCombo.addActionListener(e -> {
            Layer active = layerManager.getActiveLayer();
            active.setBlendMode((BlendMode) blendCombo.getSelectedItem());
            repaintCanvas.run();
        });
        add(blendCombo);

        layerManager.addChangeListener(this);
        rebuildList();
    }

    @Override
    public void layersChanged() {
        rebuildList();
        repaintCanvas.run();
    }

    private void rebuildList() {
        layerListPanel.removeAll();
        // Show layers top-down (highest index at top of list)
        for (int i = layerManager.getLayerCount() - 1; i >= 0; i--) {
            layerListPanel.add(createLayerRow(i));
        }

        // Sync controls to active layer
        Layer active = layerManager.getActiveLayer();
        opacitySlider.setValue(Math.round(active.getOpacity() * 100));
        opacityLabel.setText("Opacity: " + opacitySlider.getValue() + "%");
        blendCombo.setSelectedItem(active.getBlendMode());

        layerListPanel.revalidate();
        layerListPanel.repaint();
    }

    private JPanel createLayerRow(int index) {
        Layer layer = layerManager.getLayer(index);
        boolean isActive = (index == layerManager.getActiveIndex());

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(160, 36));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        if (isActive) {
            row.setBackground(new Color(200, 220, 255));
        } else {
            row.setBackground(UIManager.getColor("Panel.background"));
        }
        row.setOpaque(true);

        // Visibility toggle
        JCheckBox eyeBox = new JCheckBox();
        eyeBox.setSelected(layer.isVisible());
        eyeBox.setToolTipText("Visibility");
        eyeBox.setOpaque(false);
        eyeBox.addActionListener(e -> {
            layer.setVisible(eyeBox.isSelected());
            repaintCanvas.run();
        });

        // Thumbnail
        BufferedImage thumb = layer.createThumbnail(THUMB_W, THUMB_H);
        JLabel thumbLabel = new JLabel(new ImageIcon(thumb));
        thumbLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Name label
        JLabel nameLabel = new JLabel(layer.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(10f));
        if (layer.isLocked()) nameLabel.setForeground(Color.GRAY);

        // Click to select
        int layerIndex = index;
        MouseAdapter selectAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String newName = JOptionPane.showInputDialog(
                        LayerPanel.this, "Layer name:", layer.getName());
                    if (newName != null && !newName.isBlank()) {
                        layer.setName(newName.trim());
                        layerManager.fireChange();
                    }
                } else {
                    layerManager.setActiveIndex(layerIndex);
                }
            }
        };
        row.addMouseListener(selectAdapter);
        nameLabel.addMouseListener(selectAdapter);
        thumbLabel.addMouseListener(selectAdapter);

        JPanel leftPart = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftPart.setOpaque(false);
        leftPart.add(eyeBox);
        leftPart.add(thumbLabel);

        row.add(leftPart, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        // Lock toggle
        JCheckBox lockBox = new JCheckBox("L");
        lockBox.setFont(lockBox.getFont().deriveFont(9f));
        lockBox.setToolTipText("Lock");
        lockBox.setSelected(layer.isLocked());
        lockBox.setOpaque(false);
        lockBox.addActionListener(e -> {
            layer.setLocked(lockBox.isSelected());
            layerManager.fireChange();
        });
        row.add(lockBox, BorderLayout.EAST);

        return row;
    }

    private static JButton smallButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setToolTipText(tooltip);
        return btn;
    }
}

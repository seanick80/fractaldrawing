package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class TextTool implements Tool {

    private String currentText = "";
    private int textX, textY;
    private Font currentFont = new Font("Arial", Font.PLAIN, 24);
    private int fontSize = 24;
    private String fontFamily = "Arial";
    private boolean bold, italic;
    private boolean editing;

    // Post-commit selection state (floating text with marching ants)
    private BufferedImage floatingText;
    private Rectangle textBounds;
    private boolean moving;
    private int moveOffX, moveOffY, startMoveX, startMoveY;
    private long animFrame;
    private Timer antTimer;

    // Overlay text field added to canvas
    private JTextField textField;

    // Canvas reference for timer repaints
    private DrawingCanvas activeCanvas;

    @Override
    public String getName() { return "Text"; }

    @Override
    public boolean needsPersistentPreview() { return true; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onActivated(BufferedImage image, DrawingCanvas canvas) {
        activeCanvas = canvas;
        antTimer = new Timer(100, e -> {
            animFrame++;
            canvas.repaint();
        });
        antTimer.start();
    }

    @Override
    public void onDeactivated() {
        if (activeCanvas != null) {
            if (floatingText != null) {
                commitFloatingToLayer(activeCanvas.getActiveLayerImage());
            }
            if (textField != null) {
                commitText(activeCanvas);
            }
        }
        if (antTimer != null) {
            antTimer.stop();
            antTimer = null;
        }
        activeCanvas = null;
    }

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        // If we have floating text, check if clicking inside it to move
        if (floatingText != null && textBounds != null) {
            int dx = textBounds.x + moveOffX;
            int dy = textBounds.y + moveOffY;
            if (x >= dx && x <= dx + textBounds.width && y >= dy && y <= dy + textBounds.height) {
                moving = true;
                startMoveX = x;
                startMoveY = y;
                return;
            }
            // Click outside - commit floating text to layer
            commitFloatingToLayer(image);
        }

        // Start new text entry
        startEditing(x, y, canvas);
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving && floatingText != null) {
            moveOffX += x - startMoveX;
            moveOffY += y - startMoveY;
            startMoveX = x;
            startMoveY = y;
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving) {
            moving = false;
        }
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

    @Override
    public void drawPreview(Graphics2D g) {
        // Show live text while editing
        if (editing && !currentText.isEmpty()) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(currentFont);
            g.setColor(Color.BLACK);
            g.drawString(currentText, textX, textY);
        }

        // Show floating text with marching ants after commit
        if (floatingText != null && textBounds != null) {
            int dx = textBounds.x + moveOffX;
            int dy = textBounds.y + moveOffY;
            g.drawImage(floatingText, dx, dy, null);

            // Marching ants
            float dashOffset = (float) (animFrame % 8);
            float[] dash = { 4f, 4f };
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, dash, dashOffset));
            g.drawRect(dx, dy, textBounds.width, textBounds.height);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, dash, (dashOffset + 4f) % 8f));
            g.drawRect(dx, dy, textBounds.width, textBounds.height);
        }
    }

    // -------------------------------------------------------------------------
    // Settings panel
    // -------------------------------------------------------------------------

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Font family dropdown - each entry rendered in its own font
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        JComboBox<String> fontCombo = new JComboBox<>(fontNames);
        fontCombo.setSelectedItem(fontFamily);
        fontCombo.setMaximumSize(new Dimension(200, 28));
        fontCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String name = (String) value;
                setFont(new Font(name, Font.PLAIN, 14));
                setText(name);
                return this;
            }
        });
        fontCombo.addActionListener(e -> {
            fontFamily = (String) fontCombo.getSelectedItem();
            rebuildFont();
        });

        JLabel fontLabel = new JLabel("Font:");
        fontLabel.setFont(fontLabel.getFont().deriveFont(Font.BOLD, 11f));
        fontLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fontLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(fontCombo);
        panel.add(Box.createVerticalStrut(6));

        // Font size spinner
        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 11f));
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sizeLabel);
        panel.add(Box.createVerticalStrut(2));

        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(fontSize, 8, 200, 1));
        sizeSpinner.setMaximumSize(new Dimension(120, 28));
        sizeSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeSpinner.addChangeListener(e -> {
            fontSize = (int) sizeSpinner.getValue();
            rebuildFont();
        });
        panel.add(sizeSpinner);
        panel.add(Box.createVerticalStrut(6));

        // Style toggles
        JLabel styleLabel = new JLabel("Style:");
        styleLabel.setFont(styleLabel.getFont().deriveFont(Font.BOLD, 11f));
        styleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(styleLabel);
        panel.add(Box.createVerticalStrut(2));

        JPanel stylePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        stylePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JToggleButton boldBtn = new JToggleButton("B");
        boldBtn.setFont(boldBtn.getFont().deriveFont(Font.BOLD, 12f));
        boldBtn.setMargin(new Insets(2, 6, 2, 6));
        boldBtn.setSelected(bold);
        boldBtn.addActionListener(e -> {
            bold = boldBtn.isSelected();
            rebuildFont();
        });

        JToggleButton italicBtn = new JToggleButton("I");
        italicBtn.setFont(italicBtn.getFont().deriveFont(Font.ITALIC, 12f));
        italicBtn.setMargin(new Insets(2, 6, 2, 6));
        italicBtn.setSelected(italic);
        italicBtn.addActionListener(e -> {
            italic = italicBtn.isSelected();
            rebuildFont();
        });

        stylePanel.add(boldBtn);
        stylePanel.add(italicBtn);
        panel.add(stylePanel);

        return panel;
    }

    // -------------------------------------------------------------------------
    // Text editing
    // -------------------------------------------------------------------------

    private void startEditing(int x, int y, DrawingCanvas canvas) {
        if (textField != null) {
            commitText(canvas);
        }

        textX = x;
        textY = y;
        editing = true;

        textField = new JTextField(20);
        textField.setFont(currentFont);
        textField.setOpaque(false);
        textField.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
        textField.setBounds(x, y - currentFont.getSize(), 300, currentFont.getSize() + 8);

        textField.addActionListener(e -> commitText(canvas));
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                currentText = textField.getText();
                canvas.repaint();
            }
        });

        canvas.setLayout(null);
        canvas.add(textField);
        canvas.revalidate();
        canvas.repaint();
        textField.requestFocusInWindow();
    }

    private void commitText(DrawingCanvas canvas) {
        if (textField == null || currentText.isEmpty()) {
            removeTextField(canvas);
            return;
        }

        // Measure text to create floating image
        BufferedImage layerImg = canvas.getActiveLayerImage();
        Graphics2D measure = layerImg.createGraphics();
        measure.setFont(currentFont);
        FontMetrics fm = measure.getFontMetrics();
        int tw = fm.stringWidth(currentText);
        int th = fm.getHeight();
        measure.dispose();

        // Create floating text image
        floatingText = new BufferedImage(tw + 4, th + 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = floatingText.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(currentFont);
        g.setColor(canvas.getForegroundColor());
        g.drawString(currentText, 2, fm.getAscent() + 2);
        g.dispose();

        textBounds = new Rectangle(textX, textY - fm.getAscent() - 2, tw + 4, th + 4);
        moveOffX = 0;
        moveOffY = 0;

        removeTextField(canvas);
        editing = false;
        currentText = "";
    }

    private void commitFloatingToLayer(BufferedImage image) {
        if (floatingText == null || textBounds == null) return;
        Graphics2D g = image.createGraphics();
        g.drawImage(floatingText, textBounds.x + moveOffX, textBounds.y + moveOffY, null);
        g.dispose();
        floatingText = null;
        textBounds = null;
        moveOffX = 0;
        moveOffY = 0;
    }

    private void removeTextField(DrawingCanvas canvas) {
        if (textField != null) {
            canvas.remove(textField);
            canvas.revalidate();
            canvas.repaint();
            textField = null;
        }
    }

    // -------------------------------------------------------------------------
    // Font management
    // -------------------------------------------------------------------------

    private void rebuildFont() {
        int style = Font.PLAIN;
        if (bold) style |= Font.BOLD;
        if (italic) style |= Font.ITALIC;
        currentFont = new Font(fontFamily, style, fontSize);

        // Update the overlay text field if active
        if (textField != null) {
            textField.setFont(currentFont);
            textField.setBounds(textX, textY - currentFont.getSize(),
                    300, currentFont.getSize() + 8);
        }
    }
}

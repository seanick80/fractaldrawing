package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.*;
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

    // Overlay text area added to canvas (Issue #10: JTextArea for multiline)
    private JTextArea textArea;
    private JScrollPane scrollPane;

    // Canvas reference for timer repaints
    private DrawingCanvas activeCanvas;

    // Text color from canvas foreground (Issue #12)
    private Color textColor = Color.BLACK;

    // Click-drag text rectangle (Issue #9)
    private int dragStartX, dragStartY, dragEndX, dragEndY;
    private boolean defining;

    // Drag-to-reposition during editing (Issue #2)
    private boolean editDragging;
    private int editDragStartX, editDragStartY;

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
            BufferedImage layerImg = activeCanvas.getActiveLayerImage();
            if (floatingText != null) {
                commitFloatingToLayer(layerImg);
            }
            if (textArea != null) {
                commitText(activeCanvas);
                // commitText creates floatingText — commit it to the layer now
                if (floatingText != null) {
                    commitFloatingToLayer(layerImg);
                }
            }
        }
        // Always clear transient state regardless of activeCanvas
        floatingText = null;
        textBounds = null;
        moveOffX = 0;
        moveOffY = 0;
        editing = false;
        currentText = "";
        defining = false;
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

        // Issue #2: Handle clicks during editing
        if (textArea != null) {
            Rectangle areaBounds = scrollPane != null ? scrollPane.getBounds() : textArea.getBounds();
            // Expand bounds by 20px for drag handle zone
            Rectangle dragZone = new Rectangle(
                areaBounds.x - 20, areaBounds.y - 20,
                areaBounds.width + 40, areaBounds.height + 40);

            if (areaBounds.contains(x, y)) {
                // Click inside text area - let Swing handle text editing
                return;
            } else if (dragZone.contains(x, y)) {
                // Click near the border - start repositioning
                editDragging = true;
                editDragStartX = x;
                editDragStartY = y;
                return;
            } else {
                // Click far outside - commit text to layer and start fresh below
                commitText(canvas);
                if (floatingText != null) {
                    commitFloatingToLayer(image);
                }
            }
        }

        // Issue #9: Start defining text rectangle
        dragStartX = x;
        dragStartY = y;
        dragEndX = x;
        dragEndY = y;
        defining = true;
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving && floatingText != null) {
            moveOffX += x - startMoveX;
            moveOffY += y - startMoveY;
            startMoveX = x;
            startMoveY = y;
            return;
        }

        // Issue #2: Reposition text area during editing
        if (editDragging && textArea != null) {
            int deltaX = x - editDragStartX;
            int deltaY = y - editDragStartY;
            textX += deltaX;
            textY += deltaY;
            editDragStartX = x;
            editDragStartY = y;
            Component target = scrollPane != null ? scrollPane : textArea;
            Rectangle bounds = target.getBounds();
            target.setBounds(bounds.x + deltaX, bounds.y + deltaY,
                    bounds.width, bounds.height);
            canvas.repaint();
            return;
        }

        // Issue #9: Update drag rectangle
        if (defining) {
            dragEndX = x;
            dragEndY = y;
            canvas.repaint();
        }
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        if (moving) {
            moving = false;
            return;
        }

        // Issue #2: Stop edit dragging
        if (editDragging) {
            editDragging = false;
            return;
        }

        // Issue #9: Finish defining text rectangle
        if (defining) {
            defining = false;
            int rx = Math.min(dragStartX, dragEndX);
            int ry = Math.min(dragStartY, dragEndY);
            int rw = Math.abs(dragEndX - dragStartX);
            int rh = Math.abs(dragEndY - dragStartY);

            // Small drag = click: use default width
            int width = (rw < 10 && rh < 10) ? 300 : rw;
            startEditing(rx, ry, width, canvas);
        }
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

    @Override
    public void drawPreview(Graphics2D g) {
        // Issue #9: Show dashed rectangle while defining text area
        if (defining) {
            int rx = Math.min(dragStartX, dragEndX);
            int ry = Math.min(dragStartY, dragEndY);
            int rw = Math.abs(dragEndX - dragStartX);
            int rh = Math.abs(dragEndY - dragStartY);
            float[] dash = { 6f, 4f };
            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, dash, 0f));
            g.drawRect(rx, ry, rw, rh);
        }

        // Live color update: sync textColor with canvas foreground while editing
        if (editing && activeCanvas != null && textArea != null) {
            Color fg = activeCanvas.getForegroundColor();
            if (!fg.equals(textColor)) {
                textColor = fg;
                textArea.setForeground(textColor);
            }
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

    private void startEditing(int x, int y, int width, DrawingCanvas canvas) {
        if (textArea != null) {
            commitText(canvas);
        }

        textX = x;
        textY = y;
        editing = true;

        // Issue #12: capture foreground color
        textColor = canvas.getForegroundColor();

        // Issue #10: JTextArea for multiline editing
        textArea = new JTextArea();
        textArea.setFont(currentFont);
        textArea.setForeground(textColor);
        textArea.setOpaque(false);
        textArea.setBackground(new Color(0, 0, 0, 0));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(null);

        // Ctrl+Enter commits text (Issue #10: Enter creates newline naturally)
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER
                        && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    e.consume();
                    commitText(canvas);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                currentText = textArea.getText();
                canvas.repaint();
            }
        });

        // Wrap in JScrollPane
        scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        int height = Math.max(currentFont.getSize() + 8, currentFont.getSize() * 3);
        scrollPane.setBounds(x, y - currentFont.getSize(), width, height);

        canvas.setLayout(null);
        canvas.add(scrollPane);
        canvas.revalidate();
        canvas.repaint();
        textArea.requestFocusInWindow();
    }

    private void commitText(DrawingCanvas canvas) {
        if (textArea == null || currentText.isEmpty()) {
            removeTextArea(canvas);
            return;
        }

        // Issue #3: Measure multiline text for tight bounds
        BufferedImage layerImg = canvas.getActiveLayerImage();
        Graphics2D measure = layerImg.createGraphics();
        measure.setFont(currentFont);
        FontMetrics fm = measure.getFontMetrics();

        String[] lines = currentText.split("\n", -1);
        // Remove trailing empty line from split
        int lineCount = lines.length;
        if (lineCount > 1 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }

        int maxWidth = 0;
        for (int i = 0; i < lineCount; i++) {
            int lw = fm.stringWidth(lines[i]);
            if (lw > maxWidth) maxWidth = lw;
        }
        int totalHeight = lineCount * fm.getHeight();
        measure.dispose();

        int tw = maxWidth;
        int th = totalHeight;

        // Create floating text image (tight to content + small padding)
        floatingText = new BufferedImage(tw + 4, th + 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = floatingText.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(currentFont);
        g.setColor(textColor); // Issue #12: use stored text color
        int lineY = fm.getAscent() + 2;
        for (int i = 0; i < lineCount; i++) {
            g.drawString(lines[i], 2, lineY);
            lineY += fm.getHeight();
        }
        g.dispose();

        // Issue #3: Tight bounds around actual text content
        textBounds = new Rectangle(textX, textY - fm.getAscent() - 2, tw + 4, th + 4);
        moveOffX = 0;
        moveOffY = 0;

        removeTextArea(canvas);
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

    private void removeTextArea(DrawingCanvas canvas) {
        if (scrollPane != null) {
            canvas.remove(scrollPane);
            scrollPane = null;
        }
        if (textArea != null) {
            // In case textArea was added directly without scrollPane
            canvas.remove(textArea);
            textArea = null;
        }
        canvas.revalidate();
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // Font management
    // -------------------------------------------------------------------------

    private void rebuildFont() {
        int style = Font.PLAIN;
        if (bold) style |= Font.BOLD;
        if (italic) style |= Font.ITALIC;
        currentFont = new Font(fontFamily, style, fontSize);

        // Update the overlay text area if active
        if (textArea != null) {
            textArea.setFont(currentFont);
            if (scrollPane != null) {
                int height = Math.max(currentFont.getSize() + 8, currentFont.getSize() * 3);
                scrollPane.setBounds(textX, textY - currentFont.getSize(),
                        scrollPane.getWidth(), height);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API for external callers (Escape key, undo, etc.)
    // -------------------------------------------------------------------------

    /** Commits any floating text and active editing to the layer, then clears all state. */
    public void commitAndClear(BufferedImage image) {
        commitFloatingToLayer(image);
        if (activeCanvas != null && textArea != null) {
            commitText(activeCanvas);
        }
    }

    // -------------------------------------------------------------------------
    // Package-private accessors for testing
    // -------------------------------------------------------------------------

    String getCurrentText() { return currentText; }
    Color getTextColor() { return textColor; }
    boolean isEditing() { return editing; }
    boolean isDefining() { return defining; }
    JTextArea getTextArea() { return textArea; }
    BufferedImage getFloatingText() { return floatingText; }
    Rectangle getTextBounds() { return textBounds; }
}

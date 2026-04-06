package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.DrawingCanvas;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PaintbrushTool implements Tool {

    enum BrushShape { ROUND, SQUARE, DIAMOND }
    enum BrushTexture { SMOOTH, SPECKLE, CHALK, SCATTER }

    private int lastX, lastY;
    private int previewX = -1, previewY = -1;
    private Color previewColor = Color.BLACK;
    private int strokeSize = 12;
    private float opacity = 1.0f;
    private float hardness = 0.7f;
    private BrushShape brushShape = BrushShape.ROUND;
    private BrushTexture brushTexture = BrushTexture.SMOOTH;
    private JPanel brushPreview;

    @Override
    public String getName() { return "Brush"; }

    @Override
    public boolean hasStrokeSize() { return true; }

    @Override
    public int getDefaultStrokeSize() { return 12; }

    @Override
    public void setStrokeSize(int size) { this.strokeSize = size; }

    @Override
    public boolean needsPersistentPreview() { return true; }

    @Override
    public void mouseMoved(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        previewX = x;
        previewY = y;
        previewColor = canvas.getForegroundColor();
    }

    @Override
    public void mousePressed(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        lastX = x;
        lastY = y;
        previewX = x;
        previewY = y;
        previewColor = canvas.getForegroundColor();
        paintStamp(image, x, y, canvas.getForegroundColor());
    }

    @Override
    public void mouseDragged(BufferedImage image, int x, int y, DrawingCanvas canvas) {
        Color color = canvas.getForegroundColor();
        int spacing = Math.max(1, strokeSize / 4);
        int dx = x - lastX;
        int dy = y - lastY;
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy);
        int steps = (int) Math.ceil(dist / spacing);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int ix = lastX + (int) Math.round(dx * t);
            int iy = lastY + (int) Math.round(dy * t);
            paintStamp(image, ix, iy, color);
        }
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(BufferedImage image, int x, int y, DrawingCanvas canvas) {}

    @Override
    public void drawPreview(Graphics2D g) {
        if (previewX < 0) return;
        int radius = Math.max(1, strokeSize / 2);
        g.setColor(new Color(
            previewColor.getRed(), previewColor.getGreen(),
            previewColor.getBlue(), 128));
        g.setStroke(new BasicStroke(1f));
        switch (brushShape) {
            case ROUND:
                g.drawOval(previewX - radius, previewY - radius,
                    radius * 2, radius * 2);
                break;
            case SQUARE:
                g.drawRect(previewX - radius, previewY - radius,
                    radius * 2, radius * 2);
                break;
            case DIAMOND:
                int[] xp = {previewX, previewX + radius, previewX, previewX - radius};
                int[] yp = {previewY - radius, previewY, previewY + radius, previewY};
                g.drawPolygon(xp, yp, 4);
                break;
        }
    }

    @Override
    public JPanel createSettingsPanel(ToolSettingsContext ctx) {
        // Custom size panel with brush-aware preview
        JSpinner sizeSpinner = ToolSettingsBuilder.createStrokeSpinner(strokeSize, null);

        brushPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage img = new BufferedImage(
                        getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                paintStamp(img, getWidth() / 2, getHeight() / 2, previewColor);
                g.drawImage(img, 0, 0, null);
            }
        };
        brushPreview.setPreferredSize(new Dimension(54, 54));
        brushPreview.setMinimumSize(new Dimension(54, 54));
        brushPreview.setMaximumSize(new Dimension(54, 54));
        brushPreview.setBackground(Color.WHITE);
        brushPreview.setBorder(javax.swing.BorderFactory.createLineBorder(Color.GRAY));
        brushPreview.setAlignmentX(Component.LEFT_ALIGNMENT);

        sizeSpinner.addChangeListener(e -> {
            this.strokeSize = (int) sizeSpinner.getValue();
            brushPreview.repaint();
        });

        JPanel sizePanel = new JPanel();
        sizePanel.setLayout(new BoxLayout(sizePanel, BoxLayout.Y_AXIS));
        sizePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 11f));
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizePanel.add(sizeLabel);
        sizePanel.add(Box.createVerticalStrut(2));
        sizePanel.add(sizeSpinner);
        sizePanel.add(Box.createVerticalStrut(4));
        sizePanel.add(brushPreview);

        JPanel shapePanel = createShapePanel();

        JPanel texturePanel = createTexturePanel();

        JPanel opacityPanel = createSliderPanel("Opacity", (int) (opacity * 100), value -> {
            opacity = value / 100.0f;
            if (brushPreview != null) brushPreview.repaint();
        });

        JPanel hardnessPanel = createSliderPanel("Hardness", (int) (hardness * 100), value -> {
            hardness = value / 100.0f;
            if (brushPreview != null) brushPreview.repaint();
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sizePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(shapePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(texturePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(opacityPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(hardnessPanel);
        return panel;
    }

    private JPanel createShapePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Shape:");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<BrushShape> combo = new JComboBox<>(BrushShape.values());
        combo.setSelectedItem(brushShape);
        combo.setMaximumSize(new Dimension(140, 36));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);

        combo.setRenderer(new ListCellRenderer<BrushShape>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends BrushShape> list,
                    BrushShape value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(4, 0));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                JPanel icon = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(Color.BLACK);
                        int cx = getWidth() / 2, cy = getHeight() / 2, r = 10;
                        switch (value) {
                            case ROUND:
                                g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                                break;
                            case SQUARE:
                                g2.fillRect(cx - r, cy - r, r * 2, r * 2);
                                break;
                            case DIAMOND:
                                int[] xp = {cx, cx + r, cx, cx - r};
                                int[] yp = {cy - r, cy, cy + r, cy};
                                g2.fillPolygon(xp, yp, 4);
                                break;
                        }
                    }
                };
                icon.setPreferredSize(new Dimension(28, 28));
                icon.setOpaque(false);

                JLabel nameLabel = new JLabel(value.name());
                nameLabel.setFont(nameLabel.getFont().deriveFont(10f));
                nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

                cell.add(icon, BorderLayout.WEST);
                cell.add(nameLabel, BorderLayout.CENTER);
                return cell;
            }
        });

        combo.addActionListener(e -> {
            brushShape = (BrushShape) combo.getSelectedItem();
            if (brushPreview != null) brushPreview.repaint();
        });

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(combo);
        return panel;
    }

    private JPanel createTexturePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Texture:");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<BrushTexture> combo = new JComboBox<>(BrushTexture.values());
        combo.setSelectedItem(brushTexture);
        combo.setMaximumSize(new Dimension(140, 40));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);

        combo.setRenderer(new ListCellRenderer<BrushTexture>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends BrushTexture> list,
                    BrushTexture value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(4, 0));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                JPanel preview = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        BufferedImage img = new BufferedImage(
                                getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = img.createGraphics();
                        g2.setColor(Color.WHITE);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.dispose();

                        BrushTexture savedTexture = brushTexture;
                        BrushShape savedShape = brushShape;
                        brushTexture = value;
                        brushShape = BrushShape.ROUND;
                        int previewSize = 8;
                        int savedStroke = strokeSize;
                        strokeSize = previewSize;
                        int cy = getHeight() / 2;
                        for (int x = previewSize; x < getWidth() - previewSize; x += previewSize / 2) {
                            paintStamp(img, x, cy, Color.BLACK);
                        }
                        strokeSize = savedStroke;
                        brushTexture = savedTexture;
                        brushShape = savedShape;

                        g.drawImage(img, 0, 0, null);
                    }
                };
                preview.setPreferredSize(new Dimension(80, 24));
                preview.setOpaque(false);

                JLabel nameLabel = new JLabel(value.name());
                nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
                nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

                cell.add(preview, BorderLayout.CENTER);
                cell.add(nameLabel, BorderLayout.SOUTH);
                return cell;
            }
        });

        combo.addActionListener(e -> {
            brushTexture = (BrushTexture) combo.getSelectedItem();
            if (brushPreview != null) brushPreview.repaint();
        });

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(combo);
        return panel;
    }

    private JPanel createSliderPanel(String labelText, int initialValue, java.util.function.Consumer<Integer> onChange) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSlider slider = new JSlider(0, 100, initialValue);
        slider.setFont(slider.getFont().deriveFont(10f));
        slider.setMaximumSize(new Dimension(120, 28));
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.addChangeListener(e -> onChange.accept(slider.getValue()));

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(slider);
        return panel;
    }

    private float pixelHash(int x, int y) {
        int h = (x * 374761393 + y * 668265263) ^ 0x85ebca6b;
        h = ((h >> 13) ^ h) * 0x165667b1;
        return (float) ((h & 0x7FFFFFFF) / (double) 0x7FFFFFFF);
    }

    void paintStamp(BufferedImage image, int cx, int cy, Color color) {
        int radius = Math.max(1, strokeSize / 2);
        int size = radius * 2;

        BufferedImage stamp = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float ddx = px - radius + 0.5f;
                float ddy = py - radius + 0.5f;

                // Shape-based distance check
                float dist;
                switch (brushShape) {
                    case SQUARE:
                        dist = Math.max(Math.abs(ddx), Math.abs(ddy));
                        break;
                    case DIAMOND:
                        dist = Math.abs(ddx) + Math.abs(ddy);
                        // Scale diamond so it fits within the same radius
                        dist *= 0.5f;
                        break;
                    default: // ROUND
                        dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                        break;
                }
                if (dist > radius) continue;

                float alpha;
                float hardEdge = radius * hardness;
                if (dist <= hardEdge) {
                    alpha = opacity * 255f;
                } else {
                    float t = (dist - hardEdge) / (radius - hardEdge);
                    alpha = opacity * 255f * (1.0f - t);
                }

                // Absolute pixel position for deterministic hash
                int absPx = cx - radius + px;
                int absPy = cy - radius + py;

                // Apply texture
                switch (brushTexture) {
                    case SPECKLE:
                        if (pixelHash(absPx, absPy) < 0.3f) alpha = 0;
                        break;
                    case CHALK:
                        float noise = 0.3f + 0.7f * pixelHash(absPx, absPy);
                        alpha *= noise;
                        break;
                    case SCATTER:
                        int offX = (int) ((pixelHash(absPx, absPy) - 0.5f) * 4);
                        int offY = (int) ((pixelHash(absPy, absPx) - 0.5f) * 4);
                        int srcX = px + offX;
                        int srcY = py + offY;
                        if (srcX < 0 || srcX >= size || srcY < 0 || srcY >= size) {
                            alpha = 0;
                        }
                        break;
                    default: // SMOOTH
                        break;
                }

                int a = Math.max(0, Math.min(255, (int) alpha));
                if (a == 0) continue;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                stamp.setRGB(px, py, argb);
            }
        }

        Graphics2D g2 = image.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        g2.drawImage(stamp, cx - radius, cy - radius, null);
        g2.dispose();
    }
}

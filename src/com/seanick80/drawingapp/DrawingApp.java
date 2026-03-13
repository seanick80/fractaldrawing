package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DrawingApp extends JFrame {

    private final DrawingCanvas canvas;
    private final ToolBar toolBar;
    private final ColorPicker colorPicker;
    private final StatusBar statusBar;
    private final FillRegistry fillRegistry;
    private final UndoManager undoManager;

    public DrawingApp() {
        super("Drawing App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        undoManager = new UndoManager(50);
        fillRegistry = new FillRegistry();
        registerDefaultFills();

        canvas = new DrawingCanvas(800, 600, undoManager);
        toolBar = new ToolBar(canvas, fillRegistry);
        colorPicker = new ColorPicker(canvas);
        statusBar = new StatusBar();

        canvas.setStatusBar(statusBar);
        canvas.setColorPicker(colorPicker);
        canvas.setActiveTool(toolBar.getActiveTool());

        setJMenuBar(createMenuBar());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolBar, BorderLayout.NORTH);
        leftPanel.add(colorPicker, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.getViewport().setBackground(Color.GRAY);

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        pack();
        setSize(1024, 768);
        setLocationRelativeTo(null);
    }

    private void registerDefaultFills() {
        fillRegistry.register(new SolidFill());
        fillRegistry.register(new GradientFill());
        fillRegistry.register(new CheckerboardFill());
        fillRegistry.register(new DiagonalStripeFill());
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');

        JMenuItem newItem = new JMenuItem("New");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newItem.addActionListener(e -> newImage());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openImage());

        JMenuItem saveItem = new JMenuItem("Save As...");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> saveImage());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> {
            undoManager.undo(canvas.getImage());
            canvas.repaint();
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> {
            undoManager.redo(canvas.getImage());
            canvas.repaint();
        });

        JMenuItem clearItem = new JMenuItem("Clear");
        clearItem.addActionListener(e -> {
            canvas.saveUndoState();
            canvas.clearCanvas();
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(clearItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        return menuBar;
    }

    private void newImage() {
        String input = JOptionPane.showInputDialog(this, "Enter size (WxH):", "800x600");
        if (input == null) return;
        String[] parts = input.toLowerCase().split("x");
        try {
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());
            canvas.newImage(w, h);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid size format. Use WxH (e.g. 800x600).");
        }
    }

    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = ImageIO.read(chooser.getSelectedFile());
                if (img != null) {
                    canvas.loadImage(img);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to open image: " + ex.getMessage());
            }
        }
    }

    private void saveImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                String name = file.getName().toLowerCase();
                String format = "png";
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) format = "jpg";
                else if (name.endsWith(".bmp")) format = "bmp";
                else if (!name.endsWith(".png")) file = new File(file.getPath() + ".png");
                ImageIO.write(canvas.getImage(), format, file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save image: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new DrawingApp().setVisible(true);
        });
    }
}

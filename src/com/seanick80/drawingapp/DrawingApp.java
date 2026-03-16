package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
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
        fillRegistry.register(new CustomGradientFill());
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
        menuBar.add(createFractalMenu());
        return menuBar;
    }

    private FractalTool getFractalTool() {
        Tool t = toolBar.getTool("Fractal");
        return (t instanceof FractalTool) ? (FractalTool) t : null;
    }

    private void applyFractalAndRender(java.util.function.Consumer<FractalTool> action) {
        FractalTool ft = getFractalTool();
        if (ft == null) return;
        action.accept(ft);
        // Trigger render if fractal tool is active and canvas is available
        ft.onActivated(canvas.getImage(), canvas);
    }

    private JMenu createFractalMenu() {
        JMenu menu = new JMenu("Fractal");
        menu.setMnemonic('R');

        // --- Fractal Type ---
        JMenu typeMenu = new JMenu("Type");
        ButtonGroup typeGroup = new ButtonGroup();
        JRadioButtonMenuItem mandelbrotItem = new JRadioButtonMenuItem("Mandelbrot", true);
        JRadioButtonMenuItem juliaItem = new JRadioButtonMenuItem("Julia");
        typeGroup.add(mandelbrotItem);
        typeGroup.add(juliaItem);

        mandelbrotItem.addActionListener(e -> applyFractalAndRender(ft -> {
            ft.getRenderer().setType(FractalType.MANDELBROT);
            ft.getRenderer().setBounds(-2, 2, -2, 2);
        }));
        juliaItem.addActionListener(e -> applyFractalAndRender(ft -> {
            ft.getRenderer().setType(FractalType.JULIA);
            ft.getRenderer().setBounds(-2, 2, -2, 2);
        }));

        typeMenu.add(mandelbrotItem);
        typeMenu.add(juliaItem);
        menu.add(typeMenu);

        // --- Color Mode ---
        JMenu colorMenu = new JMenu("Coloring");
        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButtonMenuItem modItem = new JRadioButtonMenuItem("Mod (cyclic)", true);
        JRadioButtonMenuItem divItem = new JRadioButtonMenuItem("Division (linear)");
        colorGroup.add(modItem);
        colorGroup.add(divItem);

        modItem.addActionListener(e -> applyFractalAndRender(ft ->
            ft.getRenderer().setColorMode(FractalRenderer.ColorMode.MOD)));
        divItem.addActionListener(e -> applyFractalAndRender(ft ->
            ft.getRenderer().setColorMode(FractalRenderer.ColorMode.DIVISION)));

        colorMenu.add(modItem);
        colorMenu.add(divItem);
        menu.add(colorMenu);

        // --- Interior Pruning ---
        JCheckBoxMenuItem pruningItem = new JCheckBoxMenuItem("Interior Pruning", true);
        pruningItem.addActionListener(e -> applyFractalAndRender(ft ->
            ft.getRenderer().setInteriorPruning(pruningItem.isSelected())));
        menu.add(pruningItem);

        menu.addSeparator();

        // --- Preset Locations ---
        JMenu presetsMenu = new JMenu("Locations");

        addPreset(presetsMenu, "Full Mandelbrot", FractalType.MANDELBROT,
            "-2", "2", "-2", "2", 256);
        addPreset(presetsMenu, "Full Julia", FractalType.JULIA,
            "-2", "2", "-2", "2", 256);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Seahorse Valley", FractalType.MANDELBROT,
            "-0.7516", "-0.7346", "0.0534", "0.0661", 256);
        addPreset(presetsMenu, "Mini Mandelbrot", FractalType.MANDELBROT,
            "-1.7692", "-1.7642", "-0.0025", "0.0025", 512);
        addPreset(presetsMenu, "Elephant Valley", FractalType.MANDELBROT,
            "0.2501", "0.2601", "-0.0050", "0.0050", 512);
        addPreset(presetsMenu, "Lightning", FractalType.MANDELBROT,
            "-1.9855", "-1.9775", "-0.0010", "0.0010", 1024);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Deep Zoom (1e13)", FractalType.MANDELBROT,
            "-0.6596578041282916240699130664224003",
            "-0.6596578041281954277657226502133863",
            "-0.4505474984324947231692017068002755",
            "-0.4505474984323985268650112905912615", 456);
        addPreset(presetsMenu, "Deeper Zoom (1e17)", FractalType.MANDELBROT,
            "-0.65965780412826339954936433105672396103",
            "-0.65965780412826338780665141718755782163",
            "-0.45054749843244648813621629936085526501",
            "-0.45054749843244647639350338549168912561", 706);
        addPreset(presetsMenu, "Deepest Zoom (1e18)", FractalType.MANDELBROT,
            "-0.659657804128263429441678542563321007371",
            "-0.659657804128263427973839428329675239951",
            "-0.450547498432446486027726571727316188813",
            "-0.450547498432446484559887457493670421393", 506);

        menu.add(presetsMenu);

        return menu;
    }

    private void addPreset(JMenu menu, String name, FractalType type,
                           String minR, String maxR, String minI, String maxI, int iterations) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> applyFractalAndRender(ft -> {
            ft.getRenderer().setType(type);
            ft.getRenderer().setBounds(
                new BigDecimal(minR), new BigDecimal(maxR),
                new BigDecimal(minI), new BigDecimal(maxI));
            ft.getRenderer().setMaxIterations(iterations);
        }));
        menu.add(item);
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
        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--gradient-dir".equals(args[i]) && i + 1 < args.length) {
                java.io.File dir = new java.io.File(args[++i]);
                com.seanick80.drawingapp.gradient.GradientEditorDialog.setDefaultDirectory(dir);
            }
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new DrawingApp().setVisible(true);
        });
    }
}

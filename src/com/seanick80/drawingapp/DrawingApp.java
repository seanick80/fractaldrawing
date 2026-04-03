package com.seanick80.drawingapp;

import com.seanick80.drawingapp.dock.DockManager;
import com.seanick80.drawingapp.dock.DockablePanel;
import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.layers.LayerManager;
import com.seanick80.drawingapp.layers.LayerPanel;
import com.seanick80.drawingapp.tools.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

public class DrawingApp extends JFrame {

    private final DrawingCanvas canvas;
    private final ToolBar toolBar;
    private final ColorPicker colorPicker;
    private final StatusBar statusBar;
    private final FillRegistry fillRegistry;
    private final UndoManager undoManager;
    private final LayerPanel layerPanel;
    private final DockManager dockManager;
    private JMenu activeToolMenu;

    public DrawingApp() {
        super("Drawing App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        undoManager = new UndoManager(80);
        fillRegistry = new FillRegistry();
        registerDefaultFills();

        canvas = new DrawingCanvas(800, 600, undoManager);
        toolBar = new ToolBar(canvas, fillRegistry);
        colorPicker = new ColorPicker(canvas);
        statusBar = new StatusBar();
        layerPanel = new LayerPanel(canvas.getLayerManager(), canvas::repaint);

        canvas.setStatusBar(statusBar);
        canvas.setColorPicker(colorPicker);
        canvas.setActiveTool(toolBar.getActiveTool());
        toolBar.setToolChangeListener(this::onToolChanged);

        dockManager = new DockManager(this);

        // Wrap tool settings, color picker, and layers in dockable panels
        DockablePanel toolSettingsDock = new DockablePanel(
            "Tool Settings", toolBar.getToolSettingsContainer(), dockManager);
        DockablePanel colorDock = new DockablePanel(
            "Colors", colorPicker, dockManager);
        DockablePanel layerDock = new DockablePanel(
            "Layers", layerPanel, dockManager);

        // Left side panel: tool buttons + dockable settings + dockable colors
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(toolBar);
        leftPanel.add(toolSettingsDock);
        leftPanel.add(colorDock);

        // Right side panel: dockable layers
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(layerDock, BorderLayout.NORTH);
        rightPanel.setPreferredSize(new Dimension(170, 0));

        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.getViewport().setBackground(Color.GRAY);

        setJMenuBar(createMenuBar(toolSettingsDock, colorDock, layerDock));

        // Layout callback: collapse side panels + sync View menu checkboxes
        dockManager.setLayoutCallback(() -> {
            leftPanel.setVisible(toolSettingsDock.isDocked() || colorDock.isDocked());
            rightPanel.setVisible(layerDock.isDocked());
            syncViewMenuCheckboxes();
        });

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        pack();
        setSize(1200, 768);
        setLocationRelativeTo(null);
    }

    private void registerDefaultFills() {
        fillRegistry.register(new SolidFill());
        fillRegistry.register(new GradientFill());
        fillRegistry.register(new CustomGradientFill());
        fillRegistry.register(new CheckerboardFill());
        fillRegistry.register(new DiagonalStripeFill());
    }

    private JMenuBar createMenuBar(DockablePanel... dockablePanels) {
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
            undoManager.undo(canvas.getLayerManager());
            canvas.repaint();
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> {
            undoManager.redo(canvas.getLayerManager());
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

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic('V');
        for (DockablePanel dp : dockablePanels) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(dp.getTitle(), dp.isDocked());
            item.addActionListener(e -> dockManager.toggle(dp));
            viewMenu.add(item);
        }
        viewMenu.addSeparator();
        JMenuItem dockAllItem = new JMenuItem("Dock All Panels");
        dockAllItem.addActionListener(e -> dockManager.dockAll());
        viewMenu.add(dockAllItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        return menuBar;
    }

    private void syncViewMenuCheckboxes() {
        JMenuBar menuBar = getJMenuBar();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && "View".equals(menu.getText())) {
                List<DockablePanel> panels = dockManager.getPanels();
                int panelIdx = 0;
                for (int j = 0; j < menu.getItemCount() && panelIdx < panels.size(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item instanceof JCheckBoxMenuItem cb) {
                        cb.setSelected(panels.get(panelIdx).isDocked());
                        panelIdx++;
                    }
                }
                break;
            }
        }
    }

    private void onToolChanged(Tool oldTool, Tool newTool) {
        JMenuBar menuBar = getJMenuBar();
        if (activeToolMenu != null) {
            menuBar.remove(activeToolMenu);
            activeToolMenu = null;
        }
        JMenu toolMenu = newTool.getMenu();
        if (toolMenu != null) {
            menuBar.add(toolMenu);
            activeToolMenu = toolMenu;
        }
        menuBar.revalidate();
        menuBar.repaint();
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
                // Flatten all layers for image export
                BufferedImage flat = canvas.getLayerManager().composite();
                ImageIO.write(flat, format, file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save image: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        File gradientDir = null;
        File locationDir = null;

        for (int i = 0; i < args.length; i++) {
            if ("--gradient-dir".equals(args[i]) && i + 1 < args.length) {
                gradientDir = new File(args[++i]);
            } else if ("--location-dir".equals(args[i]) && i + 1 < args.length) {
                locationDir = new File(args[++i]);
            }
        }

        if (gradientDir == null || locationDir == null) {
            File dataDir = findDataDir();
            if (dataDir != null) {
                if (gradientDir == null) {
                    File g = new File(dataDir, "gradients");
                    if (g.isDirectory()) gradientDir = g;
                }
                if (locationDir == null) {
                    File l = new File(dataDir, "locations");
                    if (l.isDirectory()) locationDir = l;
                }
            }
        }

        if (gradientDir != null) {
            com.seanick80.drawingapp.gradient.GradientEditorDialog.setDefaultDirectory(gradientDir);
        }
        if (locationDir != null) {
            FractalTool.setDefaultLocationDirectory(locationDir);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new DrawingApp().setVisible(true);
        });
    }

    private static File findDataDir() {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator)) {
            File cpDir = new File(entry);
            if (cpDir.isDirectory()) {
                File data = new File(cpDir.getParentFile(), "data");
                if (data.isDirectory()) return data;
            }
        }
        File data = new File("data");
        if (data.isDirectory()) return data;
        return null;
    }
}

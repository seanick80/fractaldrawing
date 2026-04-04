package com.seanick80.drawingapp;

import com.seanick80.drawingapp.dock.DockManager;
import com.seanick80.drawingapp.dock.DockablePanel;
import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.gradient.GradientToolbar;
import com.seanick80.drawingapp.layers.Layer;
import com.seanick80.drawingapp.layers.LayerManager;
import com.seanick80.drawingapp.layers.LayerPanel;
import com.seanick80.drawingapp.tools.*;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.project.AppState;
import com.seanick80.drawingapp.project.FdpSerializer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
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
    private DockablePanel toolSettingsDockPanel;
    private JMenu activeToolMenu;

    public DrawingApp() {
        super("Drawing App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        undoManager = new UndoManager(80);
        fillRegistry = new FillRegistry();
        registerDefaultFills();

        canvas = new DrawingCanvas(800, 600, undoManager);
        GradientToolbar gradientToolbar = new GradientToolbar();
        toolBar = new ToolBar(canvas, fillRegistry, gradientToolbar);
        colorPicker = new ColorPicker(canvas);
        statusBar = new StatusBar();
        layerPanel = new LayerPanel(canvas.getLayerManager(), canvas::repaint);

        canvas.setStatusBar(statusBar);
        canvas.setColorPicker(colorPicker);
        canvas.setActiveTool(toolBar.getActiveTool());
        toolBar.setToolChangeListener(this::onToolChanged);

        dockManager = new DockManager(this);

        // Add padding to tool settings container
        JPanel toolSettingsWrapper = new JPanel(new BorderLayout());
        toolSettingsWrapper.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        toolSettingsWrapper.add(toolBar.getToolSettingsContainer(), BorderLayout.CENTER);

        // Wrap tool settings, color picker, and layers in dockable panels
        toolSettingsDockPanel = new DockablePanel(
            "Tool Settings", toolSettingsWrapper, dockManager);
        DockablePanel toolSettingsDock = toolSettingsDockPanel;
        DockablePanel colorDock = new DockablePanel(
            "Colors", colorPicker, dockManager);
        DockablePanel gradientDock = new DockablePanel(
            "Gradient Editor", gradientToolbar, dockManager);
        gradientDock.setDockEdge(DockManager.DockEdge.SOUTH);
        DockablePanel layerDock = new DockablePanel(
            "Layers", layerPanel, dockManager);
        layerDock.setDockEdge(DockManager.DockEdge.EAST);

        // Place panels in their default edge containers
        JPanel westContainer = dockManager.getWestContainer();
        JPanel eastContainer = dockManager.getEastContainer();
        JPanel northContainer = dockManager.getNorthContainer();
        JPanel southDockContainer = dockManager.getSouthContainer();

        // Tool buttons always visible at top of west side
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolBar, BorderLayout.NORTH);
        leftPanel.add(westContainer, BorderLayout.CENTER);

        // Initial placement
        westContainer.add(toolSettingsDock);
        westContainer.add(colorDock);
        eastContainer.add(layerDock);
        southDockContainer.add(gradientDock);

        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.getViewport().setBackground(Color.GRAY);

        setJMenuBar(createMenuBar(toolSettingsDock, colorDock, layerDock, gradientDock));

        dockManager.setLayoutCallback(() -> {
            // Left panel stays visible for tool buttons even if west dock is empty
            leftPanel.setVisible(true);
            syncToolbarsMenuCheckboxes();
        });

        setLayout(new BorderLayout());
        add(leftPanel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);
        add(eastContainer, BorderLayout.EAST);
        add(northContainer, BorderLayout.NORTH);

        // South: status bar + any south-docked panels
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(southDockContainer, BorderLayout.NORTH);
        southPanel.add(statusBar, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);

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

        JMenu toolbarsMenu = new JMenu("Toolbars");
        toolbarsMenu.setMnemonic('T');
        for (DockablePanel dp : dockablePanels) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(dp.getTitle(), true);
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    dockManager.show(dp);
                } else {
                    dockManager.hide(dp);
                }
            });
            toolbarsMenu.add(item);
        }
        toolbarsMenu.addSeparator();
        JMenuItem dockAllItem = new JMenuItem("Dock All");
        dockAllItem.addActionListener(e -> dockManager.dockAll());
        toolbarsMenu.add(dockAllItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(toolbarsMenu);
        return menuBar;
    }

    private void syncToolbarsMenuCheckboxes() {
        JMenuBar menuBar = getJMenuBar();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && "Toolbars".equals(menu.getText())) {
                List<DockablePanel> panels = dockManager.getPanels();
                int panelIdx = 0;
                for (int j = 0; j < menu.getItemCount() && panelIdx < panels.size(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item instanceof JCheckBoxMenuItem cb) {
                        cb.setSelected(!panels.get(panelIdx).isHidden());
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
        // Resize floating tool settings dialog if content changed
        if (toolSettingsDockPanel != null) {
            toolSettingsDockPanel.repackIfFloating();
        }
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
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("FDP Project (*.fdp)", "fdp"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Image (*.jpg, *.jpeg)", "jpg", "jpeg"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("BMP Image (*.bmp)", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                if (file.getName().toLowerCase().endsWith(".fdp")) {
                    loadFdpProject(file);
                } else {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        canvas.loadImage(img);
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to open: " + ex.getMessage());
            }
        }
    }

    private void saveImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("FDP Project (*.fdp)", "fdp"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Image (*.jpg)", "jpg"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("BMP Image (*.bmp)", "bmp"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                String name = file.getName().toLowerCase();

                if (name.endsWith(".fdp")) {
                    saveFdpProject(file);
                } else {
                    String format = "png";
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) format = "jpg";
                    else if (name.endsWith(".bmp")) format = "bmp";
                    else if (!name.endsWith(".png")) file = new File(file.getPath() + ".png");
                    BufferedImage flat = canvas.getLayerManager().composite();
                    ImageIO.write(flat, format, file);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage());
            }
        }
    }

    private void saveFdpProject(File file) throws java.io.IOException {
        // Get the fractal renderer from the active tool if available
        FractalRenderer renderer = null;
        Tool fractalTool = toolBar.getTool("Fractal");
        if (fractalTool instanceof FractalTool ft) {
            renderer = ft.getRenderer();
        }
        ColorGradient gradient = null;
        if (toolBar.getGradientToolbar() != null) {
            gradient = toolBar.getGradientToolbar().getGradient();
        }
        FdpSerializer.save(file, canvas.getLayerManager(), renderer, gradient);
    }

    private void loadFdpProject(File file) throws java.io.IOException {
        AppState state = FdpSerializer.load(file);

        // Restore layers
        LayerManager lm = canvas.getLayerManager();
        // Clear existing layers and replace with loaded ones
        while (lm.getLayerCount() > 1) {
            lm.removeLayer(1);
        }
        // Replace background layer
        if (!state.layers.isEmpty()) {
            Layer bg = state.layers.get(0);
            Layer existing = lm.getLayer(0);
            existing.setName(bg.getName());
            java.awt.Graphics2D g = existing.getImage().createGraphics();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(bg.getImage(), 0, 0, null);
            g.dispose();
            existing.setOpacity(bg.getOpacity());
            existing.setBlendMode(bg.getBlendMode());
            existing.setVisible(bg.isVisible());
            existing.setLocked(bg.isLocked());

            // Add remaining layers
            for (int i = 1; i < state.layers.size(); i++) {
                Layer loaded = state.layers.get(i);
                Layer added = lm.addLayer();
                if (added != null) {
                    added.setName(loaded.getName());
                    java.awt.Graphics2D g2 = added.getImage().createGraphics();
                    g2.setComposite(java.awt.AlphaComposite.Src);
                    g2.drawImage(loaded.getImage(), 0, 0, null);
                    g2.dispose();
                    added.setOpacity(loaded.getOpacity());
                    added.setBlendMode(loaded.getBlendMode());
                    added.setVisible(loaded.isVisible());
                    added.setLocked(loaded.isLocked());
                }
            }
        }
        if (state.activeLayerIndex >= 0 && state.activeLayerIndex < lm.getLayerCount()) {
            lm.setActiveIndex(state.activeLayerIndex);
        }

        // Restore gradient
        if (state.gradient != null && toolBar.getGradientToolbar() != null) {
            toolBar.getGradientToolbar().getGradient().copyFrom(state.gradient);
            toolBar.getGradientToolbar().repaint();
        }

        canvas.repaint();
        lm.fireChange();
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
            GradientToolbar.setDefaultDirectory(gradientDir);
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

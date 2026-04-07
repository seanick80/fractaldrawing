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
    private File currentFile;

    public DrawingApp() {
        super("Drawing App");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!canvas.isDirty()) {
                    dispose();
                    System.exit(0);
                    return;
                }
                int choice = JOptionPane.showConfirmDialog(
                    DrawingApp.this,
                    "Save before closing?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_CANCEL_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    saveFile();
                    dispose();
                    System.exit(0);
                } else if (choice == JOptionPane.NO_OPTION) {
                    dispose();
                    System.exit(0);
                }
                // CANCEL: do nothing, stay open
            }
        });

        undoManager = new UndoManager(80);
        fillRegistry = new FillRegistry();
        GradientToolbar gradientToolbar = new GradientToolbar();
        registerDefaultFills(gradientToolbar);

        canvas = new DrawingCanvas(800, 600, undoManager);
        toolBar = new ToolBar(canvas, fillRegistry, gradientToolbar);
        colorPicker = new ColorPicker(canvas);
        statusBar = new StatusBar();
        layerPanel = new LayerPanel(canvas.getLayerManager(), canvas::repaint);

        canvas.setStatusBar(statusBar);
        canvas.setColorPicker(colorPicker);
        canvas.setActiveTool(toolBar.getActiveTool());
        toolBar.setToolChangeListener(this::onToolChanged);

        // Escape: deselect all selections
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "deselect");
        canvas.getActionMap().put("deselect", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deselectAll();
            }
        });

        // Delete: delete selected content
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelection");
        canvas.getActionMap().put("deleteSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteActiveSelection();
            }
        });

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

    private void registerDefaultFills(GradientToolbar gradientToolbar) {
        fillRegistry.register(new SolidFill());
        fillRegistry.register(new GradientFill());
        CustomGradientFill customGradient = new CustomGradientFill();
        customGradient.setGradient(gradientToolbar.getGradient());
        fillRegistry.register(customGradient);
        fillRegistry.register(new CheckerboardFill());
        fillRegistry.register(new DiagonalStripeFill());
        fillRegistry.register(new CrosshatchFill());
        fillRegistry.register(new DotGridFill());
        fillRegistry.register(new HorizontalStripeFill());
        fillRegistry.register(new NoiseFill());
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

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> saveFile());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveImageAs());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            // Dispatch a window closing event so the close prompt is reused
            dispatchEvent(new java.awt.event.WindowEvent(
                DrawingApp.this, java.awt.event.WindowEvent.WINDOW_CLOSING));
        });

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> {
            undoManager.undo(canvas.getLayerManager());
            clearAllSelections();
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
            if (hasActiveSelection()) {
                deleteActiveSelection();
            } else {
                canvas.saveUndoState();
                canvas.clearCanvas();
            }
        });

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cutItem.addActionListener(e -> {
            BufferedImage copied = getActiveSelectionCopy();
            if (copied != null) {
                canvas.saveUndoState();
                copyToClipboard(copied);
                SelectionTool sel = getSelectionTool();
                if (sel != null && sel.hasSelection()) sel.cutSelection(canvas.getActiveLayerImage());
                MagicWandTool wand = getMagicWandTool();
                if (wand != null && wand.hasSelection()) wand.cutSelection(canvas.getActiveLayerImage());
                LassoTool lasso = getLassoTool();
                if (lasso != null && lasso.hasSelection()) lasso.cutSelection(canvas.getActiveLayerImage());
                canvas.repaint();
            }
        });

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> {
            BufferedImage copied = getActiveSelectionCopy();
            if (copied != null) {
                copyToClipboard(copied);
            }
        });

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        pasteItem.addActionListener(e -> {
            java.awt.image.BufferedImage img = pasteFromClipboard();
            if (img != null) {
                canvas.saveUndoState();
                SelectionTool sel = getSelectionTool();
                if (sel != null) {
                    sel.commitSelection(canvas.getActiveLayerImage());
                    sel.pasteContent(img);
                    canvas.repaint();
                }
            }
        });

        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        selectAllItem.addActionListener(e -> {
            SelectionTool sel = getSelectionTool();
            if (sel != null) {
                sel.selectAll(canvas.getActiveLayerImage());
                canvas.repaint();
            }
        });

        JMenuItem deselectItem = new JMenuItem("Deselect");
        deselectItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        deselectItem.addActionListener(e -> {
            SelectionTool sel = getSelectionTool();
            if (sel != null) {
                sel.commitSelection(canvas.getActiveLayerImage());
                sel.clearSelection();
            }
            MagicWandTool wand = getMagicWandTool();
            if (wand != null) {
                wand.commitFloating(canvas.getActiveLayerImage());
                wand.clearSelection();
            }
            LassoTool lasso = getLassoTool();
            if (lasso != null) {
                lasso.commitFloating(canvas.getActiveLayerImage());
                lasso.clearSelection();
            }
            canvas.repaint();
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.addSeparator();
        JMenuItem saveSelectionItem = new JMenuItem("Save Selection to Image...");
        saveSelectionItem.addActionListener(e -> saveSelectionToImage());

        editMenu.add(selectAllItem);
        editMenu.add(deselectItem);
        editMenu.add(saveSelectionItem);
        editMenu.addSeparator();
        editMenu.add(clearItem);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic('V');

        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> canvas.zoomIn());

        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> canvas.zoomOut());

        JMenuItem zoomFitItem = new JMenuItem("Zoom to Fit");
        zoomFitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        zoomFitItem.addActionListener(e -> canvas.zoomToFit());

        JMenuItem zoomResetItem = new JMenuItem("Reset Zoom");
        zoomResetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
        zoomResetItem.addActionListener(e -> canvas.resetViewZoom());

        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.addSeparator();
        viewMenu.add(zoomFitItem);
        viewMenu.add(zoomResetItem);

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
        menuBar.add(viewMenu);
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

    private SelectionTool getSelectionTool() {
        Tool t = toolBar.getTool("Select");
        return (t instanceof SelectionTool) ? (SelectionTool) t : null;
    }

    private MagicWandTool getMagicWandTool() {
        Tool t = toolBar.getTool("Magic Wand");
        return (t instanceof MagicWandTool) ? (MagicWandTool) t : null;
    }

    private LassoTool getLassoTool() {
        Tool t = toolBar.getTool("Lasso");
        return (t instanceof LassoTool) ? (LassoTool) t : null;
    }

    /** Returns a copied selection image from whichever selection tool is active, or null. */
    private BufferedImage getActiveSelectionCopy() {
        SelectionTool sel = getSelectionTool();
        if (sel != null && sel.hasSelection()) return sel.copySelection(canvas.getActiveLayerImage());
        MagicWandTool wand = getMagicWandTool();
        if (wand != null && wand.hasSelection()) return wand.copySelection(canvas.getActiveLayerImage());
        LassoTool lasso = getLassoTool();
        if (lasso != null && lasso.hasSelection()) return lasso.copySelection(canvas.getActiveLayerImage());
        return null;
    }

    private void copyToClipboard(java.awt.image.BufferedImage img) {
        java.awt.datatransfer.Transferable transferable = new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[] { java.awt.datatransfer.DataFlavor.imageFlavor };
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                return img;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    private java.awt.image.BufferedImage pasteFromClipboard() {
        try {
            java.awt.datatransfer.Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                java.awt.Image img = (java.awt.Image) t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
                if (img instanceof java.awt.image.BufferedImage) {
                    return (java.awt.image.BufferedImage) img;
                }
                // Convert to BufferedImage
                java.awt.image.BufferedImage bimg = new java.awt.image.BufferedImage(
                        img.getWidth(null), img.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bimg.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                return bimg;
            }
        } catch (Exception ignored) {}
        return null;
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

    private void saveSelectionToImage() {
        BufferedImage sel = getActiveSelectionCopy();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "No active selection.", "Save Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (transparency)", "png"));
        chooser.setSelectedFile(new File("selection.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            try {
                ImageIO.write(sel, "PNG", file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void newImage() {
        if (canvas.isDirty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Save current file before creating a new image?",
                    "Save Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                saveFile();
            } else if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        String input = JOptionPane.showInputDialog(this, "Enter size (WxH):", "800x600");
        if (input == null) return;
        String[] parts = input.toLowerCase().split("x");
        try {
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());
            toolBar.resetAllTools();
            canvas.newImage(w, h);
            currentFile = null;
            updateTitle();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid size format. Use WxH (e.g. 800x600).");
        }
    }

    private void openImage() {
        if (canvas.isDirty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Save current file before opening?",
                    "Save Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                saveFile();
            } else if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("FDP Project (*.fdp)", "fdp"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Image (*.jpg, *.jpeg)", "jpg", "jpeg"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("BMP Image (*.bmp)", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                toolBar.resetAllTools();
                if (file.getName().toLowerCase().endsWith(".fdp")) {
                    loadFdpProject(file);
                } else {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        canvas.loadImage(img);
                    }
                }
                currentFile = file;
                updateTitle();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to open: " + ex.getMessage());
            }
        }
    }

    /** Save to the current file, or fall back to Save As if no file is set. */
    private void saveFile() {
        if (currentFile == null) {
            saveImageAs();
            return;
        }
        try {
            String name = currentFile.getName().toLowerCase();
            if (name.endsWith(".fdp")) {
                saveFdpProject(currentFile);
            } else {
                String format = "png";
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) format = "jpg";
                else if (name.endsWith(".bmp")) format = "bmp";
                BufferedImage flat = canvas.getLayerManager().composite();
                ImageIO.write(flat, format, currentFile);
            }
            canvas.markClean();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage());
        }
    }

    private void updateTitle() {
        setTitle("Drawing App" + (currentFile != null ? " - " + currentFile.getName() : ""));
    }

    private void saveImageAs() {
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
                currentFile = file;
                updateTitle();
                canvas.markClean();
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

        // Restore layers — reset to correct canvas size first
        LayerManager lm = canvas.getLayerManager();
        lm.reset(state.canvasWidth, state.canvasHeight);
        canvas.setPreferredSize(new Dimension(state.canvasWidth, state.canvasHeight));
        canvas.revalidate();

        if (!state.layers.isEmpty()) {
            // Replace background layer
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

    // -------------------------------------------------------------------------
    // Selection helpers (used by Undo, Escape, Delete, Clear)
    // -------------------------------------------------------------------------

    /** Clears all selection tool state without committing floating content. */
    private void clearAllSelections() {
        SelectionTool sel = getSelectionTool();
        if (sel != null) sel.clearSelection();
        MagicWandTool wand = getMagicWandTool();
        if (wand != null) wand.clearSelection();
        LassoTool lasso = getLassoTool();
        if (lasso != null) lasso.clearSelection();
    }

    /** Commits all floating content and clears all selections (Escape / Deselect). */
    private void deselectAll() {
        BufferedImage layerImg = canvas.getActiveLayerImage();
        SelectionTool sel = getSelectionTool();
        if (sel != null) {
            sel.commitSelection(layerImg);
            sel.clearSelection();
        }
        MagicWandTool wand = getMagicWandTool();
        if (wand != null) {
            wand.commitFloating(layerImg);
            wand.clearSelection();
        }
        LassoTool lasso = getLassoTool();
        if (lasso != null) {
            lasso.commitFloating(layerImg);
            lasso.clearSelection();
        }
        // Also handle text tool floating text
        Tool textTool = toolBar.getTool("Text");
        if (textTool instanceof TextTool tt) {
            tt.commitAndClear(layerImg);
        }
        canvas.repaint();
    }

    /** Returns true if any selection tool has an active selection. */
    private boolean hasActiveSelection() {
        SelectionTool sel = getSelectionTool();
        if (sel != null && sel.hasSelection()) return true;
        MagicWandTool wand = getMagicWandTool();
        if (wand != null && wand.hasSelection()) return true;
        LassoTool lasso = getLassoTool();
        if (lasso != null && lasso.hasSelection()) return true;
        return false;
    }

    /** Deletes the active selection content (Delete key). */
    private void deleteActiveSelection() {
        BufferedImage layerImg = canvas.getActiveLayerImage();
        SelectionTool sel = getSelectionTool();
        if (sel != null && sel.hasSelection()) {
            canvas.saveUndoState();
            sel.deleteSelection(layerImg);
            sel.clearSelection();
            canvas.repaint();
            return;
        }
        MagicWandTool wand = getMagicWandTool();
        if (wand != null && wand.hasSelection()) {
            canvas.saveUndoState();
            wand.deleteSelection(layerImg);
            wand.clearSelection();
            canvas.repaint();
            return;
        }
        LassoTool lasso = getLassoTool();
        if (lasso != null && lasso.hasSelection()) {
            canvas.saveUndoState();
            lasso.deleteSelection(layerImg);
            lasso.clearSelection();
            canvas.repaint();
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

package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.*;
import com.seanick80.drawingapp.gradient.ColorGradient;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Builds the Fractal menu for the menu bar. Contains preset locations,
 * type selection, coloring modes, and saved location scanning.
 */
public class FractalMenuBuilder {

    public static JMenu build(FractalRenderer renderer, FractalRenderController renderController,
                              FractalTool fractalTool) {
        JMenu menu = new JMenu("Fractal");
        menu.setMnemonic('R');

        JMenu typeMenu = new JMenu("Type");
        ButtonGroup typeGroup = new ButtonGroup();
        boolean first = true;
        for (FractalType ft : FractalTypeRegistry.getDefault().getAll()) {
            String displayName = formatTypeName(ft.name());
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(displayName, first);
            first = false;
            typeGroup.add(item);
            item.addActionListener(e -> {
                renderer.setType(ft);
                renderer.setBounds(-2, 2, -2, 2);
                if (renderController.getLastImage() != null && renderController.getLastCanvas() != null) {
                    fractalTool.onActivated(renderController.getLastImage(), renderController.getLastCanvas());
                }
            });
            typeMenu.add(item);
        }
        menu.add(typeMenu);

        JMenu colorMenu = new JMenu("Coloring");
        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButtonMenuItem modItem = new JRadioButtonMenuItem("Mod (cyclic)", true);
        JRadioButtonMenuItem divItem = new JRadioButtonMenuItem("Division (linear)");
        colorGroup.add(modItem);
        colorGroup.add(divItem);

        modItem.addActionListener(e -> {
            renderer.setColorMode(FractalRenderer.ColorMode.MOD);
            renderController.triggerRender();
        });
        divItem.addActionListener(e -> {
            renderer.setColorMode(FractalRenderer.ColorMode.DIVISION);
            renderController.triggerRender();
        });

        colorMenu.add(modItem);
        colorMenu.add(divItem);
        menu.add(colorMenu);

        JCheckBoxMenuItem pruningItem = new JCheckBoxMenuItem("Interior Pruning", true);
        pruningItem.addActionListener(e -> {
            renderer.setInteriorPruning(pruningItem.isSelected());
            renderController.triggerRender();
        });
        menu.add(pruningItem);

        menu.addSeparator();

        JMenuItem flyoverItem = new JMenuItem("3D Flyover");
        flyoverItem.addActionListener(e ->
                TerrainViewer.openFromRenderer(renderer, renderController.getGradient()));
        menu.add(flyoverItem);

        menu.addSeparator();

        JMenu animMenu = new JMenu("Animations");
        JMenuItem paletteCycleItem = new JMenuItem("Palette Cycle...");
        paletteCycleItem.addActionListener(e ->
                showPaletteCycleDialog(renderer, renderController, fractalTool));
        animMenu.add(paletteCycleItem);

        JMenuItem iterAnimItem = new JMenuItem("Iteration Animation...");
        iterAnimItem.addActionListener(e ->
                showIterationAnimDialog(renderer, renderController, fractalTool));
        animMenu.add(iterAnimItem);
        menu.add(animMenu);

        menu.addSeparator();

        JMenu presetsMenu = new JMenu("Locations");

        addPreset(presetsMenu, "Full Mandelbrot", FractalType.MANDELBROT,
            "-2", "2", "-2", "2", 256, renderer, renderController);
        addPreset(presetsMenu, "Full Julia", FractalType.JULIA,
            "-2", "2", "-2", "2", 256, renderer, renderController);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Seahorse Valley", FractalType.MANDELBROT,
            "-0.7516", "-0.7346", "0.0534", "0.0661", 256, renderer, renderController);
        addPreset(presetsMenu, "Mini Mandelbrot", FractalType.MANDELBROT,
            "-1.7692", "-1.7642", "-0.0025", "0.0025", 512, renderer, renderController);
        addPreset(presetsMenu, "Elephant Valley", FractalType.MANDELBROT,
            "0.2501", "0.2601", "-0.0050", "0.0050", 512, renderer, renderController);
        addPreset(presetsMenu, "Lightning", FractalType.MANDELBROT,
            "-1.9855", "-1.9775", "-0.0010", "0.0010", 1024, renderer, renderController);

        presetsMenu.addSeparator();

        addPreset(presetsMenu, "Deep Zoom (1e13)", FractalType.MANDELBROT,
            "-0.6596578041282916240699130664224003",
            "-0.6596578041281954277657226502133863",
            "-0.4505474984324947231692017068002755",
            "-0.4505474984323985268650112905912615", 456, renderer, renderController);
        addPreset(presetsMenu, "Deeper Zoom (1e17)", FractalType.MANDELBROT,
            "-0.65965780412826339954936433105672396103",
            "-0.65965780412826338780665141718755782163",
            "-0.45054749843244648813621629936085526501",
            "-0.45054749843244647639350338549168912561", 706, renderer, renderController);
        addPreset(presetsMenu, "Deepest Zoom (1e18)", FractalType.MANDELBROT,
            "-0.659657804128263429441678542563321007371",
            "-0.659657804128263427973839428329675239951",
            "-0.450547498432446486027726571727316188813",
            "-0.450547498432446484559887457493670421393", 506, renderer, renderController);

        menu.add(presetsMenu);

        File locDir = FractalLocationManager.getDefaultLocationDirectory();
        if (locDir != null && locDir.isDirectory()) {
            File[] jsonFiles = locDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".json"));
            if (jsonFiles != null && jsonFiles.length > 0) {
                JMenu savedMenu = new JMenu("Saved Locations");
                Arrays.sort(jsonFiles, (a, b) ->
                        a.getName().compareToIgnoreCase(b.getName()));
                for (File jsonFile : jsonFiles) {
                    String label = jsonFile.getName().replaceFirst("\\.json$", "")
                            .replace('_', ' ');
                    JMenuItem item = new JMenuItem(label);
                    item.addActionListener(e -> {
                        fractalTool.loadLocationFile(jsonFile);
                        if (renderController.getLastImage() != null && renderController.getLastCanvas() != null) {
                            fractalTool.onActivated(renderController.getLastImage(), renderController.getLastCanvas());
                        }
                    });
                    savedMenu.add(item);
                }
                menu.add(savedMenu);
            }
        }

        return menu;
    }

    private static void addPreset(JMenu menu, String name, FractalType type,
                                   String minR, String maxR, String minI, String maxI,
                                   int iterations, FractalRenderer renderer,
                                   FractalRenderController renderController) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            renderer.setType(type);
            renderer.setBounds(
                new BigDecimal(minR), new BigDecimal(maxR),
                new BigDecimal(minI), new BigDecimal(maxI));
            renderer.setMaxIterations(iterations);
            renderController.triggerRender();
        });
        menu.add(item);
    }

    private static void showPaletteCycleDialog(FractalRenderer renderer,
                                                  FractalRenderController renderController,
                                                  FractalTool fractalTool) {
        int[] iters = renderer.getLastRenderIters();
        int[] size = renderer.getLastRenderSize();
        if (iters == null || size[0] <= 0) {
            JOptionPane.showMessageDialog(null, "Render a fractal first.",
                    "No Render Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField framesField = new JTextField("120", 5);
        JTextField fpsField = new JTextField("30", 5);
        JTextField speedField = new JTextField("1.0", 5);

        JPanel settingsPanel = new JPanel(new GridLayout(3, 2, 4, 4));
        settingsPanel.add(new JLabel("Total frames:"));
        settingsPanel.add(framesField);
        settingsPanel.add(new JLabel("FPS:"));
        settingsPanel.add(fpsField);
        settingsPanel.add(new JLabel("Cycle speed (rotations):"));
        settingsPanel.add(speedField);

        int result = JOptionPane.showConfirmDialog(null, settingsPanel,
                "Palette Cycle Animation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        int totalFrames, fpsVal;
        float cycleSpeed;
        try {
            totalFrames = Integer.parseInt(framesField.getText().trim());
            fpsVal = Integer.parseInt(fpsField.getText().trim());
            cycleSpeed = Float.parseFloat(speedField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid number format.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser(FractalLocationManager.getDefaultLocationDirectory());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select output directory");
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File outputDir = fc.getSelectedFile();

        int[] itersCopy = iters.clone();
        ColorGradient grad = renderController.getGradient();
        PaletteCycleAnimator animator = new PaletteCycleAnimator(renderer);
        animator.setTotalFrames(totalFrames);
        animator.setFps(fpsVal);
        animator.setCycleSpeed(cycleSpeed);

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return animator.renderToFiles(outputDir, itersCopy, size[0], size[1], grad,
                        (frame, total, ms) -> publish(String.format("Frame %d/%d (%dms)", frame + 1, total, ms)));
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                // Could update a progress label if available
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    JOptionPane.showMessageDialog(null,
                            count + " frames + palette_cycle.avi saved to:\n" + outputDir.getAbsolutePath(),
                            "Palette Cycle Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(),
                            "Animation Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void showIterationAnimDialog(FractalRenderer renderer,
                                                    FractalRenderController renderController,
                                                    FractalTool fractalTool) {
        int[] lastSize = renderer.getLastRenderSize();
        int defaultW = lastSize[0] > 0 ? lastSize[0] : 640;
        int defaultH = lastSize[1] > 0 ? lastSize[1] : 480;

        JTextField startField = new JTextField("1", 5);
        JTextField endField = new JTextField(String.valueOf(renderer.getMaxIterations()), 5);
        JTextField stepField = new JTextField("1", 5);
        JTextField fpsField = new JTextField("30", 5);
        JTextField widthField = new JTextField(String.valueOf(defaultW), 5);
        JTextField heightField = new JTextField(String.valueOf(defaultH), 5);

        JPanel settingsPanel = new JPanel(new GridLayout(6, 2, 4, 4));
        settingsPanel.add(new JLabel("Start iterations:"));
        settingsPanel.add(startField);
        settingsPanel.add(new JLabel("End iterations:"));
        settingsPanel.add(endField);
        settingsPanel.add(new JLabel("Step:"));
        settingsPanel.add(stepField);
        settingsPanel.add(new JLabel("FPS:"));
        settingsPanel.add(fpsField);
        settingsPanel.add(new JLabel("Frame width:"));
        settingsPanel.add(widthField);
        settingsPanel.add(new JLabel("Frame height:"));
        settingsPanel.add(heightField);

        int result = JOptionPane.showConfirmDialog(null, settingsPanel,
                "Iteration Animation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        int startIter, endIter, stepVal, fpsVal, frameW, frameH;
        try {
            startIter = Integer.parseInt(startField.getText().trim());
            endIter = Integer.parseInt(endField.getText().trim());
            stepVal = Integer.parseInt(stepField.getText().trim());
            fpsVal = Integer.parseInt(fpsField.getText().trim());
            frameW = Integer.parseInt(widthField.getText().trim());
            frameH = Integer.parseInt(heightField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid number format.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser(FractalLocationManager.getDefaultLocationDirectory());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select output directory");
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File outputDir = fc.getSelectedFile();

        ColorGradient grad = renderController.getGradient();
        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(startIter);
        animator.setEndIter(endIter);
        animator.setStep(stepVal);
        animator.setFps(fpsVal);
        animator.setSize(frameW, frameH);

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return animator.renderToFiles(outputDir, renderer, grad,
                        (frame, total, ms) -> publish(String.format("Frame %d/%d (%dms)", frame + 1, total, ms)));
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    JOptionPane.showMessageDialog(null,
                            count + " frames + iteration_anim.avi saved to:\n" + outputDir.getAbsolutePath(),
                            "Iteration Animation Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(),
                            "Animation Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    static String formatTypeName(String registryName) {
        String[] parts = registryName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}

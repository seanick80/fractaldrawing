package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.FractalTypeRegistry;
import com.seanick80.drawingapp.fractal.TerrainViewer;

import javax.swing.*;
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

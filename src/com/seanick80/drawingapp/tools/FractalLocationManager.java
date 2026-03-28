package com.seanick80.drawingapp.tools;

import com.seanick80.drawingapp.fractal.FractalJsonUtil;
import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.fractal.JuliaType;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles save/load of fractal locations as JSON files.
 */
public class FractalLocationManager {

    private File lastDirectory;
    private static File defaultLocationDir;

    public static void setDefaultLocationDirectory(File dir) {
        if (dir != null && dir.isDirectory()) {
            defaultLocationDir = dir;
        }
    }

    public static File getDefaultLocationDirectory() {
        return defaultLocationDir;
    }

    public void saveLocation(FractalRenderer renderer, Component parent) {
        JFileChooser fc = createFileChooser();
        if (fc.showSaveDialog(SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        lastDirectory = file.getParentFile();
        if (!file.getName().contains(".")) file = new File(file.getPath() + ".json");

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", renderer.getType().name());
        data.put("minReal", renderer.getMinRealBig().toPlainString());
        data.put("maxReal", renderer.getMaxRealBig().toPlainString());
        data.put("minImag", renderer.getMinImagBig().toPlainString());
        data.put("maxImag", renderer.getMaxImagBig().toPlainString());
        data.put("maxIterations", String.valueOf(renderer.getMaxIterations()));
        data.put("juliaReal", renderer.getJuliaReal() + "");
        data.put("juliaImag", renderer.getJuliaImag() + "");

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("{\n");
            int i = 0;
            for (var entry : data.entrySet()) {
                String value = entry.getValue();
                boolean isNumber = entry.getKey().equals("maxIterations");
                w.write("  \"" + entry.getKey() + "\": ");
                w.write(isNumber ? value : "\"" + value + "\"");
                if (++i < data.size()) w.write(",");
                w.write("\n");
            }
            w.write("}\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parent),
                "Error saving: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadLocation(FractalRenderer renderer, Component parent,
                              JComboBox<String> typeCombo, JSpinner iterSpinner,
                              Runnable afterLoad) {
        JFileChooser fc = createFileChooser();
        if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(parent)) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        lastDirectory = file.getParentFile();
        loadLocationFile(renderer, file, typeCombo, iterSpinner, afterLoad);
    }

    public void loadLocationFile(FractalRenderer renderer, File file,
                                  JComboBox<String> typeCombo, JSpinner iterSpinner,
                                  Runnable afterLoad) {
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Map<String, String> data = FractalJsonUtil.parseJson(json);

            String typeName = data.getOrDefault("type", "MANDELBROT");
            FractalType type = FractalType.valueOf(typeName);
            if (type == null) type = FractalType.MANDELBROT;
            renderer.setType(type);
            if (typeCombo != null) typeCombo.setSelectedItem(typeName);

            BigDecimal minR = new BigDecimal(data.get("minReal"));
            BigDecimal maxR = new BigDecimal(data.get("maxReal"));
            BigDecimal minI = new BigDecimal(data.get("minImag"));
            BigDecimal maxI = new BigDecimal(data.get("maxImag"));
            renderer.setBounds(minR, maxR, minI, maxI);

            if (data.containsKey("maxIterations")) {
                int iters = Integer.parseInt(data.get("maxIterations"));
                renderer.setMaxIterations(iters);
                if (iterSpinner != null) iterSpinner.setValue(iters);
            }

            if (type instanceof JuliaType
                    && data.containsKey("juliaReal") && data.containsKey("juliaImag")) {
                renderer.setJuliaConstant(
                    new BigDecimal(data.get("juliaReal")),
                    new BigDecimal(data.get("juliaImag")));
            }

            if (afterLoad != null) afterLoad.run();
        } catch (Exception ex) {
            System.err.println("Error loading location: " + ex.getMessage());
        }
    }

    public File getLastDirectory() { return lastDirectory; }
    public void setLastDirectory(File dir) { this.lastDirectory = dir; }

    public JFileChooser createFileChooser() {
        File dir = lastDirectory;
        if (dir == null) dir = defaultLocationDir;
        if (dir == null) dir = new File(".");
        JFileChooser fc = new JFileChooser(dir);
        fc.setFileFilter(new FileNameExtensionFilter("Fractal Location (*.json)", "json"));
        return fc;
    }
}

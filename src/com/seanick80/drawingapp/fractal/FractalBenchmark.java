package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI benchmark for fractal rendering performance.
 * Usage: java FractalBenchmark <location.json> [width] [height] [warmup_runs] [timed_runs]
 */
public class FractalBenchmark {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int DEFAULT_WARMUP = 1;
    private static final int DEFAULT_RUNS = 3;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FractalBenchmark <location.json> [width] [height] [warmup] [runs]");
            System.exit(1);
        }

        File locationFile = new File(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WIDTH;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_HEIGHT;
        int warmup = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_WARMUP;
        int runs = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_RUNS;

        // Load location
        Map<String, String> loc = parseJson(Files.readString(locationFile.toPath(), StandardCharsets.UTF_8));

        FractalRenderer renderer = new FractalRenderer();
        renderer.setType(FractalType.valueOf(loc.get("type")));
        renderer.setBounds(
            new BigDecimal(loc.get("minReal")),
            new BigDecimal(loc.get("maxReal")),
            new BigDecimal(loc.get("minImag")),
            new BigDecimal(loc.get("maxImag"))
        );
        renderer.setMaxIterations(Integer.parseInt(loc.get("maxIterations")));
        if (loc.containsKey("juliaReal") && loc.containsKey("juliaImag")) {
            renderer.setJuliaConstant(
                new BigDecimal(loc.get("juliaReal")),
                new BigDecimal(loc.get("juliaImag"))
            );
        }

        // Default gradient
        ColorGradient gradient = new ColorGradient();
        gradient.getStops().clear();
        gradient.addStop(0.0f, new Color(0, 7, 100));
        gradient.addStop(0.16f, new Color(32, 107, 203));
        gradient.addStop(0.42f, new Color(237, 255, 255));
        gradient.addStop(0.6425f, new Color(255, 170, 0));
        gradient.addStop(0.8575f, new Color(200, 82, 0));
        gradient.addStop(1.0f, new Color(0, 2, 0));

        // Print info
        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double zoom = 4.0 / Math.min(rangeR.abs().doubleValue(), rangeI.abs().doubleValue());
        boolean bigDecimal = renderer.needsBigDecimal();

        System.out.println("=== Fractal Benchmark ===");
        System.out.printf("Location:   %s%n", locationFile.getName());
        System.out.printf("Type:       %s%n", renderer.getType());
        System.out.printf("Size:       %dx%d%n", width, height);
        System.out.printf("Iterations: %d%n", renderer.getMaxIterations());
        System.out.printf("Zoom:       %.2e%n", zoom);
        System.out.printf("Mode:       %s%n", bigDecimal ? "BigDecimal" : "double");
        System.out.printf("Warmup:     %d run(s)%n", warmup);
        System.out.printf("Timed:      %d run(s)%n", runs);
        System.out.println();

        // Warmup
        for (int i = 0; i < warmup; i++) {
            System.out.printf("Warmup %d/%d... ", i + 1, warmup);
            long t0 = System.currentTimeMillis();
            renderer.render(width, height, gradient);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.printf("%s%n", formatTime(elapsed));
        }

        // Timed runs
        long[] times = new long[runs];
        for (int i = 0; i < runs; i++) {
            System.out.printf("Run %d/%d...    ", i + 1, runs);
            long t0 = System.currentTimeMillis();
            BufferedImage img = renderer.render(width, height, gradient);
            long elapsed = System.currentTimeMillis() - t0;
            times[i] = elapsed;
            System.out.printf("%s%n", formatTime(elapsed));
        }

        // Summary
        long min = Long.MAX_VALUE, max = 0, sum = 0;
        for (long t : times) {
            min = Math.min(min, t);
            max = Math.max(max, t);
            sum += t;
        }
        double avg = (double) sum / runs;

        System.out.println();
        System.out.println("=== Results ===");
        System.out.printf("Min:  %s%n", formatTime(min));
        System.out.printf("Max:  %s%n", formatTime(max));
        System.out.printf("Avg:  %s%n", formatTime((long) avg));
        System.out.printf("Total pixels: %,d%n", width * height);
        System.out.printf("Pixels/sec:   %,.0f%n", (double)(width * height) / (avg / 1000.0));
    }

    private static String formatTime(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("%.2fs", sec);
        int min = (int) (sec / 60);
        return String.format("%dm %.1fs", min, sec - min * 60);
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (String line : json.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            map.put(key, value);
        }
        return map;
    }
}

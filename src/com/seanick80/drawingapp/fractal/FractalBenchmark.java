package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * CLI benchmark for fractal rendering performance.
 * Usage: java FractalBenchmark <location.json|directory> [width] [height] [warmup] [runs]
 *
 * If a directory is given, all .json files in it are benchmarked.
 * For deep-zoom locations (BigDecimal), renders in all 3 modes and saves comparison images.
 */
public class FractalBenchmark {

    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 300;
    private static final int DEFAULT_WARMUP = 1;
    private static final int DEFAULT_RUNS = 3;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FractalBenchmark <location.json|directory> [width] [height] [warmup] [runs]");
            System.exit(1);
        }

        File target = new File(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WIDTH;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_HEIGHT;
        int warmup = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_WARMUP;
        int runs = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_RUNS;

        File[] files;
        if (target.isDirectory()) {
            files = target.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) {
                System.err.println("No .json files found in " + target);
                System.exit(1);
            }
            java.util.Arrays.sort(files);
        } else {
            files = new File[]{target};
        }

        for (File locationFile : files) {
            System.out.println();
            benchmarkFile(locationFile, width, height, warmup, runs);
        }
    }

    private static void benchmarkFile(File locationFile, int width, int height,
                                       int warmup, int runs) throws Exception {
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
        if ("JULIA".equals(loc.get("type")) && loc.containsKey("juliaReal") && loc.containsKey("juliaImag")) {
            renderer.setJuliaConstant(
                new BigDecimal(loc.get("juliaReal")),
                new BigDecimal(loc.get("juliaImag"))
            );
        }

        ColorGradient gradient = createGradient();

        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double zoom = 4.0 / Math.min(rangeR.abs().doubleValue(), rangeI.abs().doubleValue());
        boolean isDeepZoom = renderer.needsBigDecimal();

        String baseName = locationFile.getName().replaceFirst("\\.json$", "");

        System.out.println("=== " + baseName + " ===");
        System.out.printf("Type:       %s%n", renderer.getType());
        System.out.printf("Size:       %dx%d%n", width, height);
        System.out.printf("Iterations: %d%n", renderer.getMaxIterations());
        System.out.printf("Zoom:       %.2e%n", zoom);
        System.out.printf("Deep zoom:  %s%n", isDeepZoom ? "yes" : "no");
        System.out.println();

        if (isDeepZoom) {
            // Compare all 3 modes for deep zoom locations
            FractalRenderer.RenderMode[] modes = {
                FractalRenderer.RenderMode.PERTURBATION,
                FractalRenderer.RenderMode.BIGDECIMAL,
                FractalRenderer.RenderMode.DOUBLE
            };

            for (FractalRenderer.RenderMode mode : modes) {
                renderer.setRenderMode(mode);
                // Clear all caches between modes
                renderer.getCache().clear();
                renderer.clearPrevRenderCache();

                System.out.printf("--- Mode: %s ---%n", mode);

                // Warmup
                for (int i = 0; i < warmup; i++) {
                    System.out.printf("  Warmup %d/%d... ", i + 1, warmup);
                    long t0 = System.currentTimeMillis();
                    renderer.render(width, height, gradient);
                    renderer.getCache().clear();
                    renderer.clearPrevRenderCache();
                    System.out.printf("%s%n", formatTime(System.currentTimeMillis() - t0));
                }

                // Timed runs
                long[] times = new long[runs];
                BufferedImage lastImage = null;
                for (int i = 0; i < runs; i++) {
                    System.out.printf("  Run %d/%d...    ", i + 1, runs);
                    renderer.getCache().clear();
                    renderer.clearPrevRenderCache();
                    long t0 = System.currentTimeMillis();
                    lastImage = renderer.render(width, height, gradient);
                    times[i] = System.currentTimeMillis() - t0;
                    System.out.printf("%s%n", formatTime(times[i]));
                }

                printSummary(times, width, height);

                // Save output image
                String imgName = baseName + "_" + mode.name().toLowerCase() + ".png";
                File imgFile = new File(locationFile.getParentFile(), imgName);
                ImageIO.write(lastImage, "PNG", imgFile);
                System.out.printf("  Image:  %s%n", imgFile.getName());
                System.out.println();
            }
        } else {
            // Double mode only for non-deep-zoom locations
            renderer.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

            for (int i = 0; i < warmup; i++) {
                System.out.printf("Warmup %d/%d... ", i + 1, warmup);
                long t0 = System.currentTimeMillis();
                renderer.render(width, height, gradient);
                renderer.getCache().clear();
                renderer.clearPrevRenderCache();
                System.out.printf("%s%n", formatTime(System.currentTimeMillis() - t0));
            }

            long[] times = new long[runs];
            BufferedImage lastImage = null;
            for (int i = 0; i < runs; i++) {
                System.out.printf("Run %d/%d...    ", i + 1, runs);
                renderer.getCache().clear();
                renderer.clearPrevRenderCache();
                long t0 = System.currentTimeMillis();
                lastImage = renderer.render(width, height, gradient);
                times[i] = System.currentTimeMillis() - t0;
                System.out.printf("%s%n", formatTime(times[i]));
            }

            printSummary(times, width, height);

            String imgName = baseName + "_double.png";
            File imgFile = new File(locationFile.getParentFile(), imgName);
            ImageIO.write(lastImage, "PNG", imgFile);
            System.out.printf("Image:  %s%n", imgFile.getName());
        }
    }

    private static void printSummary(long[] times, int width, int height) {
        long min = Long.MAX_VALUE, max = 0, sum = 0;
        for (long t : times) {
            min = Math.min(min, t);
            max = Math.max(max, t);
            sum += t;
        }
        double avg = (double) sum / times.length;
        System.out.printf("  Min: %s  Max: %s  Avg: %s  (%,.0f px/s)%n",
            formatTime(min), formatTime(max), formatTime((long) avg),
            (double)(width * height) / (avg / 1000.0));
    }

    private static ColorGradient createGradient() {
        return ColorGradient.fractalDefault();
    }

    private static String formatTime(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        if (sec < 60) return String.format("%.2fs", sec);
        int min = (int) (sec / 60);
        return String.format("%dm %.1fs", min, sec - min * 60);
    }

    private static Map<String, String> parseJson(String json) {
        return FractalJsonUtil.parseJson(json);
    }
}

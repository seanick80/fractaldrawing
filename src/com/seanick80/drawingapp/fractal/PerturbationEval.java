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
 * Standalone evaluation comparing perturbation vs BigDecimal rendering.
 * Reports correctness (pixel diff, false escapes, interior accuracy) and timing.
 *
 * Usage: java PerturbationEval <location.json|directory> [width] [height]
 */
public class PerturbationEval {

    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 300;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: PerturbationEval <location.json|directory> [width] [height]");
            System.exit(1);
        }

        File target = new File(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WIDTH;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_HEIGHT;

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

        boolean allPassed = true;
        for (File f : files) {
            System.out.println();
            if (!evalFile(f, width, height)) allPassed = false;
        }

        System.out.println();
        System.out.println(allPassed ? "=== ALL PASSED ===" : "=== SOME FAILED ===");
        if (!allPassed) System.exit(1);
    }

    private static boolean evalFile(File locationFile, int width, int height) throws Exception {
        Map<String, String> loc = parseJson(Files.readString(locationFile.toPath(), StandardCharsets.UTF_8));
        String baseName = locationFile.getName().replaceFirst("\\.json$", "");

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

        ColorGradient gradient = createGradient();

        BigDecimal rangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        BigDecimal rangeI = renderer.getMaxImagBig().subtract(renderer.getMinImagBig());
        double zoom = 4.0 / Math.min(rangeR.abs().doubleValue(), rangeI.abs().doubleValue());
        boolean isDeepZoom = renderer.needsBigDecimal();

        System.out.println("=== " + baseName + " ===");
        System.out.printf("Type:       %s%n", renderer.getType());
        System.out.printf("Size:       %dx%d%n", width, height);
        System.out.printf("Iterations: %d%n", renderer.getMaxIterations());
        System.out.printf("Zoom:       %.2e%n", zoom);
        System.out.printf("Deep zoom:  %s%n", isDeepZoom ? "yes" : "no");

        if (!isDeepZoom) {
            System.out.println("  Skipped (not deep zoom, perturbation not used)");
            return true;
        }

        // --- BigDecimal (ground truth) ---
        renderer.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        renderer.getCache().clear();
        System.out.print("  BigDecimal...   ");
        System.out.flush();
        long t0 = System.currentTimeMillis();
        BufferedImage bdImg = renderer.render(width, height, gradient);
        long bdTime = System.currentTimeMillis() - t0;
        System.out.printf("%s%n", formatTime(bdTime));

        // --- Perturbation ---
        renderer.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        renderer.getCache().clear();
        System.out.print("  Perturbation... ");
        System.out.flush();
        t0 = System.currentTimeMillis();
        BufferedImage ptImg = renderer.render(width, height, gradient);
        long ptTime = System.currentTimeMillis() - t0;
        System.out.printf("%s%n", formatTime(ptTime));

        // --- Comparison ---
        int[] bdPx = bdImg.getRGB(0, 0, width, height, null, 0, width);
        int[] ptPx = ptImg.getRGB(0, 0, width, height, null, 0, width);
        int total = width * height;
        int black = Color.BLACK.getRGB();

        int exactMatch = 0;
        int closeMatch = 0;
        int bdBlack = 0, ptBlack = 0;
        int falseEscape = 0;   // BD=black, PT=color (interior pixel misclassified)
        int missedEscape = 0;  // BD=color, PT=black (escaped pixel missed)
        int maxColorDist = 0;
        long colorDistSum = 0;

        for (int i = 0; i < total; i++) {
            if (bdPx[i] == black) bdBlack++;
            if (ptPx[i] == black) ptBlack++;
            if (bdPx[i] == black && ptPx[i] != black) falseEscape++;
            if (bdPx[i] != black && ptPx[i] == black) missedEscape++;

            if (bdPx[i] == ptPx[i]) {
                exactMatch++;
                closeMatch++;
            } else {
                int d = colorDistance(bdPx[i], ptPx[i]);
                colorDistSum += d;
                maxColorDist = Math.max(maxColorDist, d);
                if (d < 80) closeMatch++;
            }
        }

        int diffCount = total - exactMatch;
        double avgColorDist = diffCount > 0 ? (double) colorDistSum / diffCount : 0;
        double speedup = (double) bdTime / Math.max(1, ptTime);

        System.out.println();
        System.out.println("  --- Correctness ---");
        System.out.printf("  Exact match:    %d/%d (%.1f%%)%n", exactMatch, total, 100.0 * exactMatch / total);
        System.out.printf("  Close match:    %d/%d (%.1f%%)%n", closeMatch, total, 100.0 * closeMatch / total);
        System.out.printf("  Avg color dist: %.1f (of differing pixels)%n", avgColorDist);
        System.out.printf("  Max color dist: %d%n", maxColorDist);
        System.out.printf("  BD interior:    %d (%.1f%%)%n", bdBlack, 100.0 * bdBlack / total);
        System.out.printf("  PT interior:    %d (%.1f%%)%n", ptBlack, 100.0 * ptBlack / total);
        System.out.printf("  False escape:   %d%n", falseEscape);
        System.out.printf("  Missed escape:  %d%n", missedEscape);

        System.out.println();
        System.out.println("  --- Performance ---");
        System.out.printf("  BigDecimal:     %s%n", formatTime(bdTime));
        System.out.printf("  Perturbation:   %s%n", formatTime(ptTime));
        System.out.printf("  Speedup:        %.1fx%n", speedup);

        // Save comparison images
        File outDir = locationFile.getParentFile();
        ImageIO.write(bdImg, "PNG", new File(outDir, baseName + "_eval_bd.png"));
        ImageIO.write(ptImg, "PNG", new File(outDir, baseName + "_eval_pt.png"));

        // Diff image: red=false escape, blue=missed escape, bright=different, dark=match
        BufferedImage diffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] diffPx = new int[total];
        for (int i = 0; i < total; i++) {
            if (bdPx[i] == black && ptPx[i] != black) {
                diffPx[i] = 0xFFFF0000; // red
            } else if (bdPx[i] != black && ptPx[i] == black) {
                diffPx[i] = 0xFF0000FF; // blue
            } else if (bdPx[i] != ptPx[i]) {
                diffPx[i] = 0xFFFFFF00; // yellow: color differs
            } else {
                int r = ((bdPx[i] >> 16) & 0xFF) / 4;
                int g = ((bdPx[i] >> 8) & 0xFF) / 4;
                int b = (bdPx[i] & 0xFF) / 4;
                diffPx[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        diffImg.setRGB(0, 0, width, height, diffPx, 0, width);
        ImageIO.write(diffImg, "PNG", new File(outDir, baseName + "_eval_diff.png"));

        System.out.printf("  Images:         %s_eval_{bd,pt,diff}.png%n", baseName);

        // Pass/fail criteria
        boolean passed = falseEscape == 0 && missedEscape == 0;
        System.out.println();
        System.out.println("  " + (passed ? "PASS" : "FAIL"));
        return passed;
    }

    private static int colorDistance(int rgb1, int rgb2) {
        return Math.abs(((rgb1 >> 16) & 0xFF) - ((rgb2 >> 16) & 0xFF))
             + Math.abs(((rgb1 >> 8) & 0xFF) - ((rgb2 >> 8) & 0xFF))
             + Math.abs((rgb1 & 0xFF) - (rgb2 & 0xFF));
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

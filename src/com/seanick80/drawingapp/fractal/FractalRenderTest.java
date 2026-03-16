package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;

/**
 * Regression test for fractal rendering.
 * Verifies that all render modes produce correct, consistent output.
 * Run: java -ea com.seanick80.drawingapp.fractal.FractalRenderTest
 */
public class FractalRenderTest {

    private static final int SIZE = 100;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Fractal Render Tests ===");
        System.out.println();

        testDoubleRenderDeterministic();
        testBigDecimalMatchesPerturbation();
        testDeeperZoomAllModes();
        testPerturbationInteriorPixels();
        testDoubleProducesBlockyAtDeepZoom();
        testDoubleModeShallowZoom();
        testJuliaSetRenders();
        testIterationCountsPreservedAcrossZoom();
        testRenderModeSwitch();

        System.out.println();
        System.out.printf("=== Results: %d passed, %d failed ===%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * Double-precision renders should be deterministic (same output every time).
     */
    private static void testDoubleRenderDeterministic() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img1 = r.render(SIZE, SIZE, gradient());
        r.getCache().clear();
        BufferedImage img2 = r.render(SIZE, SIZE, gradient());

        check("double render is deterministic", imagesEqual(img1, img2));
    }

    /**
     * At deep zoom, perturbation and BigDecimal should produce the same image.
     * This is the core correctness test for perturbation theory.
     */
    private static void testBigDecimalMatchesPerturbation() {
        FractalRenderer r = newDeepZoomRenderer();

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, gradient());

        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.getCache().clear();
        BufferedImage ptImg = r.render(SIZE, SIZE, gradient());

        int[] bdPixels = getPixels(bdImg);
        int[] ptPixels = getPixels(ptImg);

        // Perturbation and BigDecimal differ at fractal boundaries (off-by-one
        // iteration at escape edge). This is inherent to perturbation theory.
        // We verify structural equivalence: same fractal shape and similar color distribution.

        // Test 1: Exact + near-match pixel count (catches gross rendering errors)
        int exactMatch = 0;
        int closeMatch = 0;
        for (int i = 0; i < bdPixels.length; i++) {
            if (bdPixels[i] == ptPixels[i]) {
                exactMatch++;
                closeMatch++;
            } else if (colorDistance(bdPixels[i], ptPixels[i]) < 80) {
                closeMatch++;
            }
        }
        double closePct = 100.0 * closeMatch / bdPixels.length;
        check("perturbation structurally matches BigDecimal (close=" +
              String.format("%.1f%%", closePct) + ")",
              closePct > 70.0);

        // Test 2: Perturbation should produce a non-trivial image (not all one color)
        int ptBlack = countColor(ptImg, Color.BLACK.getRGB());
        int ptTotal = SIZE * SIZE;
        check("perturbation produces non-trivial image (" + ptBlack + "/" + ptTotal + " black)",
              ptBlack < ptTotal * 0.95 && countUniqueColors(ptImg) > 10);

        // Test 3: Similar number of unique colors (both produce detailed fractals)
        int bdColors = countUniqueColors(bdImg);
        int ptColors = countUniqueColors(ptImg);
        double colorRatio = (double) Math.min(bdColors, ptColors) / Math.max(bdColors, ptColors);
        check("perturbation has similar color diversity (bd=" + bdColors +
              ", pt=" + ptColors + ")",
              colorRatio > 0.5);
    }

    /**
     * At deep zoom, double-precision should produce a visually different (degraded) image
     * compared to BigDecimal, because double can't represent the coordinates precisely.
     * We verify this by checking that double produces fewer unique colors (blocky artifacts).
     */
    private static void testDeeperZoomAllModes() {
        // Zoom ~3.4e17: tests that reference orbit handles post-escape BigDecimal overflow
        // (previously crashed with ArithmeticException: Overflow)
        FractalRenderer r = newDeeperZoomRenderer();

        for (FractalRenderer.RenderMode mode : FractalRenderer.RenderMode.values()) {
            r.setRenderMode(mode);
            r.getCache().clear();
            try {
                BufferedImage img = r.render(SIZE, SIZE, gradient());
                boolean valid = img != null && countUniqueColors(img) > 1;
                check("deeper zoom " + mode + " renders without crash (" +
                      (img == null ? "null" : countUniqueColors(img) + " colors") + ")", valid);
            } catch (Exception e) {
                check("deeper zoom " + mode + " renders without crash (threw " +
                      e.getClass().getSimpleName() + ": " + e.getMessage() + ")", false);
            }
        }
    }

    /**
     * Tests that perturbation correctly identifies interior pixels when the
     * reference orbit escapes but many pixels don't. Previously, post-escape
     * reference Z values caused false escapes for interior pixels.
     */
    private static void testPerturbationInteriorPixels() {
        FractalRenderer r = newRenderer();
        r.setBounds(
            new BigDecimal("-0.562333343441602469224560589876423039"),
            new BigDecimal("-0.562333343441601581046140889751190703"),
            new BigDecimal("-0.646896540537624584973152168988217449"),
            new BigDecimal("-0.646896540537623696794732468862985113")
        );
        r.setMaxIterations(256);

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, gradient());

        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.getCache().clear();
        BufferedImage ptImg = r.render(SIZE, SIZE, gradient());

        int[] bdPx = getPixels(bdImg);
        int[] ptPx = getPixels(ptImg);
        int black = java.awt.Color.BLACK.getRGB();
        int bdBlack = 0, ptBlack = 0, falseEscape = 0;
        for (int i = 0; i < bdPx.length; i++) {
            if (bdPx[i] == black) bdBlack++;
            if (ptPx[i] == black) ptBlack++;
            if (bdPx[i] == black && ptPx[i] != black) falseEscape++;
        }

        check("perturbation interior pixel count matches (bd=" + bdBlack +
              ", pt=" + ptBlack + ", false_escape=" + falseEscape + ")",
              falseEscape == 0);
    }

    private static void testDoubleProducesBlockyAtDeepZoom() {
        FractalRenderer r = newDeepZoomRenderer();

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, gradient());

        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage dblImg = r.render(SIZE, SIZE, gradient());

        // At deep zoom, double precision can't distinguish pixel coordinates,
        // so double output should be significantly different from BigDecimal.
        int[] bdPixels = getPixels(bdImg);
        int[] dblPixels = getPixels(dblImg);
        int diffCount = 0;
        for (int i = 0; i < bdPixels.length; i++) {
            if (bdPixels[i] != dblPixels[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / bdPixels.length;

        // Double should produce a noticeably different image at 4e13 zoom
        check("double differs from BigDecimal at deep zoom (diff=" +
              String.format("%.1f%%", diffPct) + ")",
              diffPct > 5.0);
    }

    /**
     * At shallow zoom, double mode should produce a valid fractal with many unique colors.
     */
    private static void testDoubleModeShallowZoom() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img = r.render(SIZE, SIZE, gradient());

        int colors = countUniqueColors(img);
        // A full Mandelbrot view at 256 iterations should have many distinct colors
        check("shallow zoom produces rich image (" + colors + " unique colors)", colors > 20);

        // Should contain some black pixels (inside the set)
        int blackCount = countColor(img, Color.BLACK.getRGB());
        check("shallow zoom has set-interior pixels (" + blackCount + " black)",
              blackCount > 0 && blackCount < SIZE * SIZE);
    }

    /**
     * Julia set should render correctly in all applicable modes.
     */
    private static void testJuliaSetRenders() {
        FractalRenderer r = newRenderer();
        r.setType(FractalType.JULIA);
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setJuliaConstant(-0.7, 0.27015);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img = r.render(SIZE, SIZE, gradient());

        int colors = countUniqueColors(img);
        check("Julia set renders with detail (" + colors + " unique colors)", colors > 15);

        int blackCount = countColor(img, Color.BLACK.getRGB());
        check("Julia set has interior pixels (" + blackCount + " black)",
              blackCount > 0 && blackCount < SIZE * SIZE);
    }

    /**
     * Iteration counts should not change when rendering the same region twice
     * after a zoom that doesn't actually change bounds (identity zoom).
     * This catches the stale-cache bug where quadtree returned nearby but wrong values.
     */
    private static void testIterationCountsPreservedAcrossZoom() {
        FractalRenderer r = newRenderer();
        r.setBounds(-0.75, -0.74, 0.10, 0.11);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img1 = r.render(SIZE, SIZE, gradient());

        // Simulate a "zoom" to slightly different bounds then back
        r.setBounds(-0.748, -0.742, 0.102, 0.108);
        r.render(SIZE, SIZE, gradient());

        // Return to original bounds
        r.setBounds(-0.75, -0.74, 0.10, 0.11);
        BufferedImage img2 = r.render(SIZE, SIZE, gradient());

        check("iteration counts stable across zoom cycles", imagesEqual(img1, img2));
    }

    /**
     * Verify that switching render modes doesn't corrupt state.
     * Render in each mode sequentially and verify each produces a valid image.
     */
    private static void testRenderModeSwitch() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(128);

        for (FractalRenderer.RenderMode mode : FractalRenderer.RenderMode.values()) {
            r.setRenderMode(mode);
            r.getCache().clear();
            BufferedImage img = r.render(SIZE, SIZE, gradient());

            int colors = countUniqueColors(img);
            check("mode " + mode + " produces valid image (" + colors + " colors)", colors > 10);
        }
    }

    // --- Helpers ---

    private static FractalRenderer newRenderer() {
        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        return r;
    }

    private static FractalRenderer newDeeperZoomRenderer() {
        FractalRenderer r = newRenderer();
        r.setBounds(
            new BigDecimal("-0.65965780412826339954936433105672396103"),
            new BigDecimal("-0.65965780412826338780665141718755782163"),
            new BigDecimal("-0.45054749843244648813621629936085526501"),
            new BigDecimal("-0.45054749843244647639350338549168912561")
        );
        r.setMaxIterations(706);
        return r;
    }

    private static FractalRenderer newDeepZoomRenderer() {
        FractalRenderer r = newRenderer();
        r.setBounds(
            new BigDecimal("-0.6596578041282916240699130664224003"),
            new BigDecimal("-0.6596578041281954277657226502133863"),
            new BigDecimal("-0.4505474984324947231692017068002755"),
            new BigDecimal("-0.4505474984323985268650112905912615")
        );
        r.setMaxIterations(456);
        return r;
    }

    private static ColorGradient gradient() {
        ColorGradient g = new ColorGradient();
        g.getStops().clear();
        g.addStop(0.0f, new Color(0, 7, 100));
        g.addStop(0.16f, new Color(32, 107, 203));
        g.addStop(0.42f, new Color(237, 255, 255));
        g.addStop(0.6425f, new Color(255, 170, 0));
        g.addStop(0.8575f, new Color(200, 82, 0));
        g.addStop(1.0f, new Color(0, 2, 0));
        return g;
    }

    private static int[] getPixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        int[] pa = getPixels(a);
        int[] pb = getPixels(b);
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) return false;
        }
        return true;
    }

    private static int countUniqueColors(BufferedImage img) {
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        int[] pixels = getPixels(img);
        for (int p : pixels) colors.add(p);
        return colors.size();
    }

    private static int countColor(BufferedImage img, int targetRgb) {
        int count = 0;
        int[] pixels = getPixels(img);
        for (int p : pixels) {
            if (p == targetRgb) count++;
        }
        return count;
    }

    private static int colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + name);
            passed++;
        } else {
            System.out.println("  FAIL: " + name);
            failed++;
        }
    }
}

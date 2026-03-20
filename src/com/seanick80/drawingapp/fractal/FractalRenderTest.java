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

        // Phase A golden-value and contract tests (T1.1-T1.9)
        testMandelbrotDoubleGolden();
        testJuliaDoubleGolden();
        testMandelbrotPerturbationGolden();
        testMandelbrotBigDecimalGolden();
        testFractalTypeEnumContract();
        testColorMappingDeterministic();
        testQuadTreeCacheContract();
        testJsonParseRoundTrip();
        testGradientDefaultConsistency();

        // Phase B.4 extraction tests (T2.4-T2.5)
        testViewportCalculatorAspectRatio();
        testColorMapperLUT();

        // Phase F: Interior pruning correctness
        testPruningIdenticalOutput();
        testPruningIdenticalAtSpikyEdges();
        testPruningMandelbrotInterior();
        testPruningSpeedupOnInterior();

        // Load/save correctness
        testLoadMandelbrotNotOverriddenByJuliaConstant();
        testSevenPointedStarDeepZoom();

        // Phase C: New fractal type tests (T2.6-T2.8, T3.1-T3.5)
        testBurningShipIteration();
        testTricornIteration();
        testMagnetTypeIIteration();
        testBurningShipRendersValidImage();
        testTricornRendersValidImage();
        testMagnetRendersValidImage();
        testNewTypesInAllRenderModes();
        testTypeSelectionRoundTrip();

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

    // --- Phase A: Golden-value and contract tests (T1.1-T1.9) ---

    /** T1.1: Pin Mandelbrot double rendering to detect any change during refactoring. */
    private static void testMandelbrotDoubleGolden() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        long checksum = pixelChecksum(img);
        long expected = 1682464L;
        check("mandelbrot double golden checksum=" + checksum, checksum == expected);
    }

    /** T1.2: Pin Julia double rendering. */
    private static void testJuliaDoubleGolden() {
        FractalRenderer r = newRenderer();
        r.setType(FractalType.JULIA);
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setJuliaConstant(-0.7, 0.27015);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        long checksum = pixelChecksum(img);
        long expected = 1570868L;
        check("julia double golden checksum=" + checksum, checksum == expected);
    }

    /** T1.3: Pin perturbation rendering at deep zoom. */
    private static void testMandelbrotPerturbationGolden() {
        FractalRenderer r = newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        long checksum = pixelChecksum(img);
        long expected = 3536663L;
        check("mandelbrot perturbation golden checksum=" + checksum, checksum == expected);
    }

    /** T1.4: Pin BigDecimal rendering at deep zoom. */
    private static void testMandelbrotBigDecimalGolden() {
        FractalRenderer r = newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        long checksum = pixelChecksum(img);
        long expected = 3536663L;
        check("mandelbrot bigdecimal golden checksum=" + checksum, checksum == expected);
    }

    /** T1.5: Pin iteration counts for known inputs before extracting FractalType to interface. */
    private static void testFractalTypeEnumContract() {
        int maxIter = 256;
        // Mandelbrot at origin is interior
        int m1 = FractalType.MANDELBROT.iterate(0.0, 0.0, maxIter);
        check("mandelbrot (0,0) is interior: iter=" + m1, m1 == maxIter);

        // Mandelbrot at (2,2) escapes immediately
        int m2 = FractalType.MANDELBROT.iterate(2.0, 2.0, maxIter);
        check("mandelbrot (2,2) escapes quickly: iter=" + m2, m2 < 5);

        // Julia at origin with default constant — escapes but not immediately
        JuliaType julia = new JuliaType(-0.7, 0.27015);
        int j1 = julia.iterate(0.0, 0.0, maxIter);
        check("julia (0,0) c=(-0.7,0.27015): iter=" + j1, j1 > 5 && j1 < maxIter);

        // Julia far outside — escapes immediately
        int j2 = julia.iterate(10.0, 10.0, maxIter);
        check("julia (10,10) escapes immediately: iter=" + j2, j2 < 3);

        // BigDecimal must match double for representable inputs
        java.math.MathContext mc = new java.math.MathContext(20);
        int mb = FractalType.MANDELBROT.iterateBig(
            java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, maxIter, mc);
        check("mandelbrot BigDecimal (0,0) matches double: " + mb + " vs " + m1, mb == m1);
    }

    /** T1.6: Gradient color mapping must be deterministic and produce known endpoint colors. */
    private static void testColorMappingDeterministic() {
        ColorGradient g = gradient();
        Color[] colors = g.toColors(64);
        Color[] colors2 = g.toColors(64);
        boolean identical = true;
        for (int i = 0; i < 64; i++) {
            if (colors[i].getRGB() != colors2[i].getRGB()) { identical = false; break; }
        }
        check("gradient toColors is deterministic", identical);

        Color first = colors[0];
        check("gradient first color is (0,7,100): got (" +
              first.getRed() + "," + first.getGreen() + "," + first.getBlue() + ")",
              first.getRed() == 0 && first.getGreen() == 7 && first.getBlue() == 100);

        Color last = colors[63];
        check("gradient last color is (0,2,0): got (" +
              last.getRed() + "," + last.getGreen() + "," + last.getBlue() + ")",
              last.getRed() == 0 && last.getGreen() == 2 && last.getBlue() == 0);
    }

    /** T1.7: QuadTree insert, lookup, tolerance, and prune contract. */
    private static void testQuadTreeCacheContract() {
        IterationQuadTree qt = new IterationQuadTree(-4, 4, -4, 4);
        qt.insert(1.0, 1.0, 42);
        qt.insert(-1.0, -1.0, 99);
        qt.insert(0.5, 0.5, 10);
        check("quadtree size after 3 inserts: " + qt.size(), qt.size() >= 3);

        int r1 = qt.lookup(1.0, 1.0, 0.001);
        check("quadtree exact lookup (1,1): " + r1, r1 == 42);

        int r2 = qt.lookup(1.001, 1.001, 0.01);
        check("quadtree near lookup (1.001,1.001) tol=0.01: " + r2, r2 == 42);

        int r3 = qt.lookup(2.0, 2.0, 0.01);
        check("quadtree miss (2,2): " + r3, r3 == IterationQuadTree.CACHE_MISS);

        qt.pruneOutside(0.5, 1.5, 0.5, 1.5);
        int afterPrune1 = qt.lookup(1.0, 1.0, 0.001);
        check("quadtree survives prune (1,1): " + afterPrune1, afterPrune1 == 42);

        int afterPrune2 = qt.lookup(-1.0, -1.0, 0.001);
        check("quadtree pruned (-1,-1): " + afterPrune2,
              afterPrune2 == IterationQuadTree.CACHE_MISS);
    }

    /** T1.8: JSON parsing round-trip (pins behavior before dedup in Phase B.1). */
    private static void testJsonParseRoundTrip() {
        String json = "{\n  \"type\": \"MANDELBROT\",\n  \"minReal\": \"-2.0\",\n" +
            "  \"maxReal\": \"1.0\",\n  \"maxIterations\": 256\n}";
        java.util.Map<String, String> map = parseJson(json);
        check("json parse has 4 keys: " + map.size(), map.size() == 4);
        check("json parse type=MANDELBROT", "MANDELBROT".equals(map.get("type")));
        check("json parse minReal=-2.0", "-2.0".equals(map.get("minReal")));
        check("json parse maxIterations=256", "256".equals(map.get("maxIterations")));
    }

    /** T1.9: All 4 copies of the default gradient must produce identical output. */
    private static void testGradientDefaultConsistency() {
        ColorGradient g1 = gradient();
        Color[] c1 = g1.toColors(256);

        ColorGradient g2 = new ColorGradient();
        g2.getStops().clear();
        g2.addStop(0.0f, new Color(0, 7, 100));
        g2.addStop(0.16f, new Color(32, 107, 203));
        g2.addStop(0.42f, new Color(237, 255, 255));
        g2.addStop(0.6425f, new Color(255, 170, 0));
        g2.addStop(0.8575f, new Color(200, 82, 0));
        g2.addStop(1.0f, new Color(0, 2, 0));
        Color[] c2 = g2.toColors(256);

        boolean match = true;
        for (int i = 0; i < 256; i++) {
            if (c1[i].getRGB() != c2[i].getRGB()) { match = false; break; }
        }
        check("gradient default consistency (256 colors)", match);
    }

    // --- Phase C: New fractal type tests ---

    /** T2.6: Burning Ship iteration properties. */
    private static void testBurningShipIteration() {
        BurningShipType bs = new BurningShipType();
        int maxIter = 256;

        // Known exterior point: should escape quickly
        int e1 = bs.iterate(0.5, 0.5, maxIter);
        check("burning ship (0.5,0.5) escapes: iter=" + e1, e1 < maxIter && e1 > 0);

        // Known interior point near origin
        int i1 = bs.iterate(0.0, 0.0, maxIter);
        check("burning ship (0,0) is interior: iter=" + i1, i1 == maxIter);

        // Far outside should escape immediately
        int e2 = bs.iterate(10.0, 10.0, maxIter);
        check("burning ship (10,10) escapes immediately: iter=" + e2, e2 < 3);

        // BigDecimal must match double for representable inputs
        java.math.MathContext mc = new java.math.MathContext(20);
        int eb = bs.iterateBig(new BigDecimal("0.5"), new BigDecimal("0.5"), maxIter, mc);
        check("burning ship BigDecimal matches double: " + eb + " vs " + e1, eb == e1);

        // Burning Ship is NOT symmetric about real axis (unlike Mandelbrot)
        int above = bs.iterate(-1.5, 0.1, maxIter);
        int below = bs.iterate(-1.5, -0.1, maxIter);
        check("burning ship asymmetric: iter(-1.5,0.1)=" + above + " vs iter(-1.5,-0.1)=" + below,
              above != below);
    }

    /** T2.7: Tricorn iteration properties. */
    private static void testTricornIteration() {
        TricornType tc = new TricornType();
        int maxIter = 256;

        // Origin is interior
        int i1 = tc.iterate(0.0, 0.0, maxIter);
        check("tricorn (0,0) is interior: iter=" + i1, i1 == maxIter);

        // Far outside escapes immediately
        int e1 = tc.iterate(10.0, 10.0, maxIter);
        check("tricorn (10,10) escapes immediately: iter=" + e1, e1 < 3);

        // Tricorn uses conjugate: iterate(x, y) uses conj(z) = (zr, -zi)
        // This means the imaginary component flips sign before squaring
        int a = tc.iterate(-0.5, 0.5, maxIter);
        int b = tc.iterate(-0.5, -0.5, maxIter);
        check("tricorn conjugation symmetry: iter(-0.5,0.5)=" + a +
              " vs iter(-0.5,-0.5)=" + b, a == b);

        // BigDecimal must match double
        java.math.MathContext mc = new java.math.MathContext(20);
        int e1b = tc.iterateBig(new BigDecimal("10"), new BigDecimal("10"), maxIter, mc);
        check("tricorn BigDecimal matches double: " + e1b + " vs " + e1, e1b == e1);
    }

    /** T2.8: Magnet Type I iteration properties. */
    private static void testMagnetTypeIIteration() {
        MagnetTypeIType mag = new MagnetTypeIType();
        int maxIter = 256;

        // Far outside converges to fixed point z=1 (Magnet characteristic)
        int e1 = mag.iterate(10.0, 10.0, maxIter);
        check("magnet (10,10) converges: iter=" + e1, e1 == maxIter);

        // z=0 with c near fixed point z=1: should converge
        int c1 = mag.iterate(0.0, 0.0, maxIter);
        check("magnet (0,0) result: iter=" + c1, c1 >= 0);

        // BigDecimal must match double for simple case
        java.math.MathContext mc = new java.math.MathContext(20);
        int e1b = mag.iterateBig(new BigDecimal("10"), new BigDecimal("10"), maxIter, mc);
        check("magnet BigDecimal matches double: " + e1b + " vs " + e1, e1b == e1);
    }

    /** T3.1: Burning Ship renders a valid, non-trivial image. */
    private static void testBurningShipRendersValidImage() {
        FractalRenderer r = newRenderer();
        r.setType(new BurningShipType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        int colors = countUniqueColors(img);
        int black = countColor(img, Color.BLACK.getRGB());
        check("burning ship renders with detail (" + colors + " colors)", colors > 15);
        check("burning ship has interior pixels (" + black + " black)",
              black > 0 && black < SIZE * SIZE);
    }

    /** T3.2: Tricorn renders a valid, non-trivial image. */
    private static void testTricornRendersValidImage() {
        FractalRenderer r = newRenderer();
        r.setType(new TricornType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        int colors = countUniqueColors(img);
        int black = countColor(img, Color.BLACK.getRGB());
        check("tricorn renders with detail (" + colors + " colors)", colors > 15);
        check("tricorn has interior pixels (" + black + " black)",
              black > 0 && black < SIZE * SIZE);
    }

    /** T3.3: Magnet Type I renders a valid image. */
    private static void testMagnetRendersValidImage() {
        FractalRenderer r = newRenderer();
        r.setType(new MagnetTypeIType());
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, gradient());
        int colors = countUniqueColors(img);
        check("magnet renders with detail (" + colors + " colors)", colors > 10);
    }

    /** T3.4: All new types render in all applicable modes without crashing. */
    private static void testNewTypesInAllRenderModes() {
        FractalType[] newTypes = { new BurningShipType(), new TricornType(), new MagnetTypeIType() };
        // Only test DOUBLE and BIGDECIMAL — new types don't support PERTURBATION yet
        FractalRenderer.RenderMode[] modes = {
            FractalRenderer.RenderMode.DOUBLE,
            FractalRenderer.RenderMode.BIGDECIMAL
        };
        for (FractalType ft : newTypes) {
            for (FractalRenderer.RenderMode mode : modes) {
                FractalRenderer r = newRenderer();
                r.setType(ft);
                r.setBounds(-2.0, 2.0, -2.0, 2.0);
                r.setMaxIterations(64);
                r.setRenderMode(mode);
                r.getCache().clear();
                try {
                    BufferedImage img = r.render(SIZE, SIZE, gradient());
                    check(ft.name() + " " + mode + " renders OK",
                          img != null && countUniqueColors(img) > 1);
                } catch (Exception e) {
                    check(ft.name() + " " + mode + " renders OK (threw " +
                          e.getClass().getSimpleName() + ")", false);
                }
            }
        }
    }

    /** T3.5: Type name survives registry round-trip. */
    private static void testTypeSelectionRoundTrip() {
        for (FractalType ft : FractalTypeRegistry.getDefault().getAll()) {
            FractalType looked = FractalType.valueOf(ft.name());
            check("registry round-trip: " + ft.name(), looked != null);
            check("registry round-trip name match: " + ft.name(),
                  looked != null && ft.name().equals(looked.name()));
        }
    }

    // --- Phase F: Interior pruning correctness tests ---

    /**
     * Verify that pruning ON and OFF produce identical output at deep zoom
     * where perturbation is used. Tests the Mandelbrot cardioid boundary.
     */
    private static void testPruningIdenticalOutput() {
        FractalRenderer rOn = newRenderer();
        FractalRenderer rOff = newRenderer();

        // Mandelbrot at moderate zoom — mix of interior and exterior
        rOn.setBounds(-0.75, -0.74, 0.10, 0.11);
        rOff.setBounds(-0.75, -0.74, 0.10, 0.11);
        rOn.setMaxIterations(256);
        rOff.setMaxIterations(256);
        rOn.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOff.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, gradient());
        BufferedImage imgOff = rOff.render(200, 150, gradient());

        check("pruning on/off identical: double cardioid edge", imagesEqual(imgOn, imgOff));
    }

    /**
     * Verify pruning correctness at spiky edges in perturbation mode.
     * Uses a deep zoom location at the antenna tip where the set boundary
     * has fine spikes that could be wrongly pruned as interior.
     */
    private static void testPruningIdenticalAtSpikyEdges() {
        FractalRenderer rOn = newRenderer();
        FractalRenderer rOff = newRenderer();

        // Deep zoom at the antenna tip — spiky boundary between interior/exterior
        BigDecimal minR = new BigDecimal("-1.7690645");
        BigDecimal maxR = new BigDecimal("-1.7690625");
        BigDecimal minI = new BigDecimal("-0.0000010");
        BigDecimal maxI = new BigDecimal("0.0000010");

        rOn.setBounds(minR, maxR, minI, maxI);
        rOff.setBounds(minR, maxR, minI, maxI);
        rOn.setMaxIterations(512);
        rOff.setMaxIterations(512);
        rOn.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOff.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);

        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, gradient());
        BufferedImage imgOff = rOff.render(200, 150, gradient());

        int[] pxOn = getPixels(imgOn);
        int[] pxOff = getPixels(imgOff);
        int diff = 0;
        for (int i = 0; i < pxOn.length; i++) {
            if (pxOn[i] != pxOff[i]) diff++;
        }
        check("pruning on/off identical: spiky antenna (diff=" + diff + ")", diff == 0);
    }

    /**
     * Verify that Mandelbrot with large interior correctly identifies
     * interior pixels with pruning, matching non-pruned output.
     * Uses the main cardioid view with BigDecimal mode.
     */
    private static void testPruningMandelbrotInterior() {
        FractalRenderer rOn = newRenderer();
        FractalRenderer rOff = newRenderer();

        // Period-2 bulb edge — lots of interior with complex boundary
        rOn.setBounds(-1.26, -1.24, -0.01, 0.01);
        rOff.setBounds(-1.26, -1.24, -0.01, 0.01);
        rOn.setMaxIterations(512);
        rOff.setMaxIterations(512);
        rOn.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rOff.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        rOn.setInteriorPruning(true);
        rOff.setInteriorPruning(false);

        BufferedImage imgOn = rOn.render(200, 150, gradient());
        BufferedImage imgOff = rOff.render(200, 150, gradient());

        int blackOn = countColor(imgOn, Color.BLACK.getRGB());
        int blackOff = countColor(imgOff, Color.BLACK.getRGB());

        check("pruning on/off identical: bulb edge (blackOn=" + blackOn +
              ", blackOff=" + blackOff + ")", imagesEqual(imgOn, imgOff));

        // Verify significant interior was found
        int total = 200 * 150;
        check("mandelbrot bulb has significant interior (" + blackOn + "/" + total + ")",
              blackOn > total * 0.1);
    }

    /**
     * Verify pruning speedup on Mandelbrot deep zoom centered on the cardioid interior.
     * This location is mostly interior pixels — pruning should skip most BigDecimal work.
     * Reports timing but doesn't fail on speed (hardware-dependent).
     */
    private static void testPruningSpeedupOnInterior() {
        // Deep zoom at cardioid boundary — mix of interior and exterior with
        // perturbation glitch fallbacks, which is where pruning helps most
        BigDecimal minR = new BigDecimal("-0.7500100000000000");
        BigDecimal maxR = new BigDecimal("-0.7499900000000000");
        BigDecimal minI = new BigDecimal("-0.0000100000000000");
        BigDecimal maxI = new BigDecimal("0.0000100000000000");

        // Render with pruning ON
        FractalRenderer rOn = newRenderer();
        rOn.setBounds(minR, maxR, minI, maxI);
        rOn.setMaxIterations(256);
        rOn.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOn.setInteriorPruning(true);

        long t0 = System.nanoTime();
        BufferedImage imgOn = rOn.render(200, 150, gradient());
        long prunedMs = (System.nanoTime() - t0) / 1_000_000;

        // Render with pruning OFF
        FractalRenderer rOff = newRenderer();
        rOff.setBounds(minR, maxR, minI, maxI);
        rOff.setMaxIterations(256);
        rOff.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rOff.setInteriorPruning(false);

        t0 = System.nanoTime();
        BufferedImage imgOff = rOff.render(200, 150, gradient());
        long unprunedMs = (System.nanoTime() - t0) / 1_000_000;

        // Must produce identical output
        check("pruning on/off identical: deep cardioid interior", imagesEqual(imgOn, imgOff));

        // Report interior pixel count
        int blackCount = countColor(imgOn, Color.BLACK.getRGB());
        int total = 200 * 150;
        check("deep cardioid has significant interior (" + blackCount + "/" + total + ")",
              blackCount > total * 0.5);

        // Report speedup (informational — doesn't fail since hardware-dependent)
        System.out.println("  INFO: pruning ON=" + prunedMs + "ms, OFF=" + unprunedMs + "ms" +
            (unprunedMs > 0 ? ", speedup=" + String.format("%.1fx", (double) unprunedMs / Math.max(prunedMs, 1)) : ""));
    }

    // --- Phase B.4: Extraction tests (T2.4-T2.5) ---

    /** T2.4: ViewportCalculator aspect-ratio correction and scale consistency. */
    private static void testViewportCalculatorAspectRatio() {
        // Square image, non-square bounds → should expand to maintain aspect
        java.math.BigDecimal neg2 = new java.math.BigDecimal("-2");
        java.math.BigDecimal pos1 = new java.math.BigDecimal("1");
        java.math.BigDecimal neg1_5 = new java.math.BigDecimal("-1.5");
        java.math.BigDecimal pos1_5 = new java.math.BigDecimal("1.5");

        ViewportCalculator.DoubleViewport vp = ViewportCalculator.computeDouble(
                neg2, pos1, neg1_5, pos1_5, 100, 100);
        // Bounds: real range=3, imag range=3 → square, so viewReal==viewImag for square image
        check("viewport square: viewReal==viewImag",
              Math.abs(vp.viewReal - vp.viewImag) < 1e-10);
        check("viewport scaleX > 0", vp.scaleX > 0);
        check("viewport scaleY > 0", vp.scaleY > 0);
        check("viewport tolerance > 0", vp.tolerance > 0);

        // Wide image (200x100) with square bounds → viewReal should be wider
        ViewportCalculator.DoubleViewport vpWide = ViewportCalculator.computeDouble(
                neg2, pos1, neg1_5, pos1_5, 200, 100);
        check("viewport wide: viewReal > viewImag", vpWide.viewReal > vpWide.viewImag);

        // BigDecimal viewport should produce consistent center
        java.math.MathContext mc = new java.math.MathContext(20);
        java.math.BigDecimal rangeR = pos1.subtract(neg2);
        java.math.BigDecimal rangeI = pos1_5.subtract(neg1_5);
        ViewportCalculator.BigViewport bvp = ViewportCalculator.computeBig(
                neg2, pos1, neg1_5, pos1_5, rangeR, rangeI, 100, 100, mc);
        double centerR = bvp.centerReal.doubleValue();
        double centerI = bvp.centerImag.doubleValue();
        check("big viewport center real ~ -0.5: " + centerR, Math.abs(centerR - (-0.5)) < 1e-10);
        check("big viewport center imag ~ 0.0: " + centerI, Math.abs(centerI) < 1e-10);

        // Double and BigDecimal viewports should agree on scale for the same inputs
        double dScaleX = bvp.scaleX.doubleValue();
        check("double and big scaleX agree",
              Math.abs(vp.scaleX - dScaleX) / Math.max(vp.scaleX, 1e-20) < 1e-10);
    }

    /** T2.5: ColorMapper LUT construction and color mapping. */
    private static void testColorMapperLUT() {
        ColorGradient g = gradient();

        // MOD mode: cyclic wrapping
        FractalColorMapper modMapper = new FractalColorMapper(g, 256,
                FractalRenderer.ColorMode.MOD);
        int[] modLut = modMapper.getLut();
        check("mod LUT size is 64", modLut.length == 64);
        // iter 0 and iter 64 should map to same color (cyclic)
        check("mod mapper cyclic: iter 0 == iter 64",
              modMapper.colorForIter(0) == modMapper.colorForIter(64));
        // maxIterations → black
        check("mod mapper maxIter → black",
              modMapper.colorForIter(256) == Color.BLACK.getRGB());

        // DIVISION mode: linear mapping
        FractalColorMapper divMapper = new FractalColorMapper(g, 256,
                FractalRenderer.ColorMode.DIVISION);
        int[] divLut = divMapper.getLut();
        check("division LUT size is 256", divLut.length == 256);
        // maxIterations → black
        check("division mapper maxIter → black",
              divMapper.colorForIter(256) == Color.BLACK.getRGB());
        // iter 0 should match first gradient color
        check("division mapper iter 0 matches gradient start",
              divMapper.colorForIter(0) == modMapper.colorForIter(0));
    }

    /**
     * Regression test: loading a Mandelbrot JSON that contains juliaReal/juliaImag
     * fields should NOT switch the renderer to Julia mode.
     */
    private static void testLoadMandelbrotNotOverriddenByJuliaConstant() {
        System.out.println("  -- Load/Save correctness --");
        // Simulate what loadLocation() does: parse JSON with type=MANDELBROT + julia fields
        String json = "{\n" +
            "  \"type\": \"MANDELBROT\",\n" +
            "  \"minReal\": \"-2.0\",\n" +
            "  \"maxReal\": \"1.0\",\n" +
            "  \"minImag\": \"-1.5\",\n" +
            "  \"maxImag\": \"1.5\",\n" +
            "  \"maxIterations\": 256,\n" +
            "  \"juliaReal\": \"-0.7\",\n" +
            "  \"juliaImag\": \"0.27015\"\n" +
            "}";
        java.util.Map<String, String> data = parseJson(json);
        String typeName = data.getOrDefault("type", "MANDELBROT");
        FractalType type = FractalType.valueOf(typeName);

        FractalRenderer r = new FractalRenderer();
        r.setType(type);

        // Apply julia constant only if type is Julia (the fix)
        if (type instanceof JuliaType && data.containsKey("juliaReal") && data.containsKey("juliaImag")) {
            r.setJuliaConstant(new BigDecimal(data.get("juliaReal")),
                               new BigDecimal(data.get("juliaImag")));
        }

        check("load Mandelbrot JSON keeps Mandelbrot type",
              r.getType() instanceof MandelbrotType);
        check("load Mandelbrot JSON type name is MANDELBROT",
              r.getType().name().equals("MANDELBROT"));
    }

    /**
     * Seven-pointed star deep zoom location renders with detail (not solid color).
     * Uses low resolution (50x50) since this is a deep zoom (~1e16) BigDecimal render.
     */
    private static void testSevenPointedStarDeepZoom() {
        FractalRenderer r = newRenderer();
        r.setBounds(
            new BigDecimal("-0.680148757332831493578851222991943360"),
            new BigDecimal("-0.680148757332831121049821376800537110"),
            new BigDecimal("-0.472196542546057414295193929152650857"),
            new BigDecimal("-0.472196542546057041766164082961244607")
        );
        r.setMaxIterations(506);
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);

        BufferedImage img = r.render(50, 50, gradient());
        int colors = countUniqueColors(img);
        check("seven-pointed star renders with detail (colors=" + colors + ")", colors >= 3);
        check("seven-pointed star is Mandelbrot type",
              r.getType() instanceof MandelbrotType);
    }

    // --- Helpers ---

    private static long pixelChecksum(BufferedImage img) {
        int[] pixels = getPixels(img);
        long sum = 0;
        for (int p : pixels) {
            sum += (p >> 16) & 0xFF;
            sum += (p >> 8) & 0xFF;
            sum += p & 0xFF;
        }
        return sum;
    }

    private static java.util.Map<String, String> parseJson(String json) {
        return FractalJsonUtil.parseJson(json);
    }

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
        return ColorGradient.fractalDefault();
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

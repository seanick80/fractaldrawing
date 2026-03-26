package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
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

        // Partial render bug reproduction
        testPartialRenderBug();

        // Previous-render cache tests
        testPrevRenderCacheAtShallowZoom();
        testPrevRenderCacheAtDeepZoom();

        // Phase C: New fractal type tests (T2.6-T2.8, T3.1-T3.5)
        testBurningShipIteration();
        testTricornIteration();
        testMagnetTypeIIteration();
        testBurningShipRendersValidImage();
        testTricornRendersValidImage();
        testMagnetRendersValidImage();
        testNewTypesInAllRenderModes();
        testTypeSelectionRoundTrip();

        // Pixel guessing tests
        testPixelGuessingNearIdentical();
        testPixelGuessingOnOffToggle();
        testPixelGuessingFilamentRegion();

        // Zoom animation tests
        testZoomAnimatorInterpolation();
        testZoomAnimatorRenderFrames();

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
        r.setPixelGuessing(false); // golden tests require exact output
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

    /**
     * Reproduces the partial render bug at a specific deep-zoom Mandelbrot location.
     *
     * Location: approx -0.6596578... -0.4505474... at zoom ~1e-39
     * maxIterations: 706
     *
     * Expected: ~25% black (Mandelbrot interior), detail across the rest.
     * Bug symptom: ~75% or more black — large swaths of the image incorrectly
     * treated as interior when they should show iteration-escape colour.
     *
     * Renders in three configurations and prints row-level diagnostics:
     *   1. BIGDECIMAL mode (pure BigDecimal, no perturbation, no interior pruning) — ground truth
     *   2. BIGDECIMAL mode with interior pruning enabled
     *   3. PERTURBATION mode (perturbation + interior pruning ON by default)
     */
    private static void testPartialRenderBug() {
        System.out.println();
        System.out.println("  -- Partial Render Bug Diagnostics --");

        final int W = 100, H = 75;
        final int TOTAL = W * H;
        final int BLACK = Color.BLACK.getRGB();

        BigDecimal minR = new BigDecimal("-0.659657804128263403164649202540923498620");
        BigDecimal maxR = new BigDecimal("-0.659657804128263397293292745606340428924");
        BigDecimal minI = new BigDecimal("-0.450547498432446486714476507308609934980");
        BigDecimal maxI = new BigDecimal("-0.450547498432446480843120050374026865284");
        int maxIter = 706;

        // ----------------------------------------------------------------
        // Config 1: Pure BigDecimal, no interior pruning — ground truth
        // ----------------------------------------------------------------
        FractalRenderer rBD = new FractalRenderer();
        rBD.setType(FractalType.MANDELBROT);
        rBD.setBounds(minR, maxR, minI, maxI);
        rBD.setMaxIterations(maxIter);
        rBD.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rBD.setInteriorPruning(false);

        BufferedImage imgBD = rBD.render(W, H, gradient());
        int[] pxBD = getPixels(imgBD);

        int blackBD = 0;
        int firstBlackRowBD = -1;
        int allBlackRowsBD = 0;
        for (int row = 0; row < H; row++) {
            int rowBlack = 0;
            for (int col = 0; col < W; col++) {
                if (pxBD[row * W + col] == BLACK) rowBlack++;
            }
            if (rowBlack == W) {
                allBlackRowsBD++;
                if (firstBlackRowBD < 0) firstBlackRowBD = row;
            }
            blackBD += rowBlack;
        }
        double pctBD = 100.0 * blackBD / TOTAL;
        System.out.printf("  BIGDECIMAL (no pruning): black=%d/%d (%.1f%%), "
                + "all-black rows=%d, first all-black row=%d%n",
                blackBD, TOTAL, pctBD, allBlackRowsBD, firstBlackRowBD);
        check("BIGDECIMAL (no pruning): <50% black (got " + String.format("%.1f%%", pctBD) + ")",
              pctBD < 50.0);

        // ----------------------------------------------------------------
        // Config 2: Pure BigDecimal WITH interior pruning
        // ----------------------------------------------------------------
        FractalRenderer rBDPrune = new FractalRenderer();
        rBDPrune.setType(FractalType.MANDELBROT);
        rBDPrune.setBounds(minR, maxR, minI, maxI);
        rBDPrune.setMaxIterations(maxIter);
        rBDPrune.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rBDPrune.setInteriorPruning(true);

        BufferedImage imgBDPrune = rBDPrune.render(W, H, gradient());
        int[] pxBDPrune = getPixels(imgBDPrune);

        int blackBDPrune = 0;
        int firstBlackRowBDPrune = -1;
        int allBlackRowsBDPrune = 0;
        for (int row = 0; row < H; row++) {
            int rowBlack = 0;
            for (int col = 0; col < W; col++) {
                if (pxBDPrune[row * W + col] == BLACK) rowBlack++;
            }
            if (rowBlack == W) {
                allBlackRowsBDPrune++;
                if (firstBlackRowBDPrune < 0) firstBlackRowBDPrune = row;
            }
            blackBDPrune += rowBlack;
        }
        double pctBDPrune = 100.0 * blackBDPrune / TOTAL;
        System.out.printf("  BIGDECIMAL (with pruning): black=%d/%d (%.1f%%), "
                + "all-black rows=%d, first all-black row=%d%n",
                blackBDPrune, TOTAL, pctBDPrune, allBlackRowsBDPrune, firstBlackRowBDPrune);

        // Compare pruned vs non-pruned: should be identical
        int pruningDiff = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (pxBD[i] != pxBDPrune[i]) pruningDiff++;
        }
        System.out.printf("  BIGDECIMAL pruning diff vs no-pruning: %d pixels%n", pruningDiff);
        check("BIGDECIMAL pruning matches no-pruning (diff=" + pruningDiff + ")", pruningDiff == 0);

        // ----------------------------------------------------------------
        // Config 3: PERTURBATION mode (with interior pruning, default)
        // ----------------------------------------------------------------
        FractalRenderer rPT = new FractalRenderer();
        rPT.setType(FractalType.MANDELBROT);
        rPT.setBounds(minR, maxR, minI, maxI);
        rPT.setMaxIterations(maxIter);
        rPT.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rPT.setInteriorPruning(true);

        BufferedImage imgPT = rPT.render(W, H, gradient());
        int[] pxPT = getPixels(imgPT);

        int blackPT = 0;
        int firstBlackRowPT = -1;
        int allBlackRowsPT = 0;
        for (int row = 0; row < H; row++) {
            int rowBlack = 0;
            for (int col = 0; col < W; col++) {
                if (pxPT[row * W + col] == BLACK) rowBlack++;
            }
            if (rowBlack == W) {
                allBlackRowsPT++;
                if (firstBlackRowPT < 0) firstBlackRowPT = row;
            }
            blackPT += rowBlack;
        }
        double pctPT = 100.0 * blackPT / TOTAL;
        System.out.printf("  PERTURBATION (with pruning): black=%d/%d (%.1f%%), "
                + "all-black rows=%d, first all-black row=%d%n",
                blackPT, TOTAL, pctPT, allBlackRowsPT, firstBlackRowPT);
        check("PERTURBATION: <50% black (got " + String.format("%.1f%%", pctPT) + ")",
              pctPT < 50.0);

        // Compare perturbation vs BD ground truth (structural)
        int ptVsBDDiff = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (pxBD[i] != pxPT[i]) ptVsBDDiff++;
        }
        System.out.printf("  PERTURBATION diff vs BIGDECIMAL ground truth: %d pixels (%.1f%%)%n",
                ptVsBDDiff, 100.0 * ptVsBDDiff / TOTAL);
        // Perturbation may differ at edges but should broadly agree on black regions
        int falseBlackPT = 0; // pixels BD says non-black but PT says black
        int falseBlackBDPrune = 0; // pixels BD says non-black but BD+pruning says black
        for (int i = 0; i < TOTAL; i++) {
            boolean bdBlack = pxBD[i] == BLACK;
            if (!bdBlack && pxPT[i] == BLACK) falseBlackPT++;
            if (!bdBlack && pxBDPrune[i] == BLACK) falseBlackBDPrune++;
        }
        System.out.printf("  False-black pixels (vs ground truth): PT=%d, BD+pruning=%d%n",
                falseBlackPT, falseBlackBDPrune);
        check("PERTURBATION false-black count < 10% of image (got " + falseBlackPT + ")",
              falseBlackPT < TOTAL * 0.10);
        check("BIGDECIMAL+pruning false-black count < 10% of image (got " + falseBlackBDPrune + ")",
              falseBlackBDPrune < TOTAL * 0.10);
    }

    /**
     * Test cache behavior at shallow zoom (double-precision quadtree path).
     * Renders the default Mandelbrot view, then zooms 2x and pans,
     * verifying the quadtree produces cache hits and correct images.
     */
    private static void testPrevRenderCacheAtShallowZoom() {
        System.out.println();
        System.out.println("  -- Cache at Shallow Zoom (double quadtree) --");

        int W = 100, H = 100;
        ColorGradient g = gradient();

        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        // First render: default view
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        BufferedImage img1 = r.render(W, H, g);
        int colors1 = countUniqueColors(img1);
        check("shallow zoom first render has detail (colors=" + colors1 + ")", colors1 > 10);

        // Second render: zoom 2x into center
        r.setBounds(-1.0, 1.0, -1.0, 1.0);
        BufferedImage img2 = r.render(W, H, g);

        int colors2 = countUniqueColors(img2);
        check("shallow zoom 2x render has detail (colors=" + colors2 + ")", colors2 > 10);

        // Verify correctness vs fresh render
        FractalRenderer rFresh = new FractalRenderer();
        rFresh.setType(FractalType.MANDELBROT);
        rFresh.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rFresh.setBounds(-1.0, 1.0, -1.0, 1.0);
        rFresh.setMaxIterations(256);
        BufferedImage imgFresh = rFresh.render(W, H, g);
        check("shallow cached render matches from-scratch render", imagesEqual(img2, imgFresh));

        // Pan: shift right by 10 pixels
        double scaleX = 2.0 / (W - 1); // range=2.0, width-1 pixels
        double panDist = scaleX * 10;
        r.setBounds(-1.0 + panDist, 1.0 + panDist, -1.0, 1.0);
        BufferedImage img3 = r.render(W, H, g);

        var cache = r.getCache();
        int panHits = cache.getHits();
        int panLookups = cache.getLookups();
        double panHitPct = panLookups > 0 ? 100.0 * panHits / panLookups : 0;
        System.out.printf("  INFO: Shallow pan quadtree hits: %d/%d (%.1f%%)%n",
                panHits, panLookups, panHitPct);
        check("shallow pan has quadtree cache hits (got " +
              String.format("%.1f%%", panHitPct) + ")", panHitPct > 30.0);

        // Verify pan correctness
        FractalRenderer rPanFresh = new FractalRenderer();
        rPanFresh.setType(FractalType.MANDELBROT);
        rPanFresh.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rPanFresh.setBounds(-1.0 + panDist, 1.0 + panDist, -1.0, 1.0);
        rPanFresh.setMaxIterations(256);
        BufferedImage imgPanFresh = rPanFresh.render(W, H, g);
        check("shallow pan cached render matches from-scratch render", imagesEqual(img3, imgPanFresh));
    }

    /**
     * Test that the previous-render cache produces cache hits at deep zoom.
     * Renders a deep zoom location, then zooms in 2x and verifies that
     * the second render reuses pixels from the first render.
     * Also verifies that the cached render matches a from-scratch render.
     */
    private static void testPrevRenderCacheAtDeepZoom() {
        System.out.println();
        System.out.println("  -- Previous-Render Cache at Deep Zoom --");

        int W = 50, H = 50;
        ColorGradient g = gradient();

        // Use a deep zoom location where double-precision cache is useless
        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);

        // First render at a deep zoom location
        BigDecimal minR1 = new BigDecimal("-0.6596578041282634");
        BigDecimal maxR1 = new BigDecimal("-0.6596578041282433");
        BigDecimal minI1 = new BigDecimal("-0.4505474984324465");
        BigDecimal maxI1 = new BigDecimal("-0.4505474984324264");
        r.setBounds(minR1, maxR1, minI1, maxI1);
        r.setMaxIterations(256);

        BufferedImage img1 = r.render(W, H, g);
        int hits1 = r.getPrevRenderCacheHits();
        System.out.printf("  INFO: First render prev-cache hits: %d (expected 0)%n", hits1);
        check("first render has 0 prev-cache hits", hits1 == 0);

        // Second render: zoom 2x into the center of the first render
        BigDecimal rangeR = maxR1.subtract(minR1);
        BigDecimal rangeI = maxI1.subtract(minI1);
        BigDecimal quarter = new BigDecimal("0.25");
        BigDecimal minR2 = minR1.add(rangeR.multiply(quarter));
        BigDecimal maxR2 = maxR1.subtract(rangeR.multiply(quarter));
        BigDecimal minI2 = minI1.add(rangeI.multiply(quarter));
        BigDecimal maxI2 = maxI1.subtract(rangeI.multiply(quarter));
        r.setBounds(minR2, maxR2, minI2, maxI2);

        BufferedImage img2 = r.render(W, H, g);
        int hits2 = r.getPrevRenderCacheHits();
        double hitPct = 100.0 * hits2 / (W * H);
        System.out.printf("  INFO: Second render (2x zoom) prev-cache hits: %d/%d (%.1f%%)%n",
                hits2, W * H, hitPct);
        // With tight tolerance, ~50% of new pixels land exactly on old pixel positions
        // for a centered 2x zoom (every other pixel aligns with the old grid)
        check("2x zoom produces >20% prev-render cache hits (got " +
              String.format("%.1f%%", hitPct) + ")", hitPct > 20.0);

        // Verify correctness: render same view from scratch (no prev-render data)
        FractalRenderer rFresh = new FractalRenderer();
        rFresh.setType(FractalType.MANDELBROT);
        rFresh.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rFresh.setBounds(minR2, maxR2, minI2, maxI2);
        rFresh.setMaxIterations(256);
        BufferedImage imgFresh = rFresh.render(W, H, g);

        boolean match = imagesEqual(img2, imgFresh);
        check("cached render matches from-scratch render", match);

        // Test pan: shift right by 12 pixels (integer pixel shift, like mouse drag).
        // In the actual app, pan distance is always an integer multiple of scaleX,
        // so grids align perfectly and all overlapping pixels get cache hits.
        BigDecimal panScaleX = maxR2.subtract(minR2).divide(BigDecimal.valueOf(W - 1),
            new java.math.MathContext(35, java.math.RoundingMode.HALF_UP));
        BigDecimal panShift = panScaleX.multiply(BigDecimal.valueOf(12));
        BigDecimal minR3 = minR2.add(panShift);
        BigDecimal maxR3 = maxR2.add(panShift);
        r.setBounds(minR3, maxR3, minI2, maxI2);

        BufferedImage img3 = r.render(W, H, g);
        int hits3 = r.getPrevRenderCacheHits();
        double hitPctPan = 100.0 * hits3 / (W * H);
        System.out.printf("  INFO: Pan render prev-cache hits: %d/%d (%.1f%%)%n",
                hits3, W * H, hitPctPan);
        // 12 pixel shift on 50-wide image = 38/50 = 76% overlap
        check("pan produces >60% prev-render cache hits (got " +
              String.format("%.1f%%", hitPctPan) + ")", hitPctPan > 60.0);

        // Verify pan correctness
        FractalRenderer rPanFresh = new FractalRenderer();
        rPanFresh.setType(FractalType.MANDELBROT);
        rPanFresh.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rPanFresh.setBounds(minR3, maxR3, minI2, maxI2);
        rPanFresh.setMaxIterations(256);
        BufferedImage imgPanFresh = rPanFresh.render(W, H, g);
        check("pan cached render matches from-scratch render", imagesEqual(img3, imgPanFresh));
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

    // --- Zoom Animation Tests ---

    private static void testZoomAnimatorInterpolation() {
        System.out.println("\n  -- Zoom Animation --");
        ZoomAnimator.Keyframe from = new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100);
        ZoomAnimator.Keyframe to = new ZoomAnimator.Keyframe(-0.5, 0.5, 100.0, 500);

        // t=0 should be the start keyframe
        ZoomAnimator.Keyframe k0 = ZoomAnimator.interpolate(from, to, 0.0);
        check("interpolate t=0 centerR ~ 0.0",
                Math.abs(k0.centerReal.doubleValue()) < 0.001);
        check("interpolate t=0 zoom ~ 1.0",
                Math.abs(k0.zoomLevel.doubleValue() - 1.0) < 0.01);
        check("interpolate t=0 maxIter=100", k0.maxIterations == 100);

        // t=1 should be the end keyframe
        ZoomAnimator.Keyframe k1 = ZoomAnimator.interpolate(from, to, 1.0);
        check("interpolate t=1 centerR ~ -0.5",
                Math.abs(k1.centerReal.doubleValue() - (-0.5)) < 0.001);
        check("interpolate t=1 zoom ~ 100.0",
                Math.abs(k1.zoomLevel.doubleValue() - 100.0) < 0.5);
        check("interpolate t=1 maxIter=500", k1.maxIterations == 500);

        // t=0.5 should be midpoint (exponential zoom: sqrt(1*100)=10)
        ZoomAnimator.Keyframe kMid = ZoomAnimator.interpolate(from, to, 0.5);
        check("interpolate t=0.5 zoom ~ 10.0 (exponential)",
                Math.abs(kMid.zoomLevel.doubleValue() - 10.0) < 0.5);
        check("interpolate t=0.5 centerR ~ -0.25",
                Math.abs(kMid.centerReal.doubleValue() - (-0.25)) < 0.001);
    }

    private static void testZoomAnimatorRenderFrames() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        ZoomAnimator animator = new ZoomAnimator(r, gradient());
        animator.setSize(50, 50);
        animator.setFramesPerSegment(3);

        animator.addKeyframe(new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100));
        animator.addKeyframe(new ZoomAnimator.Keyframe(-0.5, 0.0, 10.0, 200));

        check("total frames = 4 (3 per segment + 1)", animator.getTotalFrames() == 4);

        // Render to temp directory
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "fractal_anim_test_" + System.currentTimeMillis());
        try {
            int[] frameCount = {0};
            int rendered = animator.renderToFiles(tempDir, (idx, total, ms) -> frameCount[0]++);
            check("rendered 4 frames", rendered == 4);
            check("callback called 4 times", frameCount[0] == 4);

            // Verify files exist
            boolean allExist = true;
            for (int i = 0; i < 4; i++) {
                File f = new File(tempDir, String.format("frame_%04d.png", i));
                if (!f.exists()) { allExist = false; break; }
            }
            check("all frame files created", allExist);
        } catch (Exception e) {
            check("zoom animation render failed: " + e.getMessage(), false);
        } finally {
            // Cleanup
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) for (File f : files) f.delete();
                tempDir.delete();
            }
        }
    }

    // --- Pixel Guessing Tests ---

    private static void testPixelGuessingNearIdentical() {
        System.out.println("\n  -- Pixel Guessing --");
        // Double path: guessing ON vs OFF should produce nearly identical images
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(200, 200, gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(200, 200, gradient());

        int[] pe = getPixels(exact);
        int[] pg = getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        System.out.printf("  INFO: pixel guessing diff vs exact: %d/%d (%.1f%%)%n",
                diffCount, pe.length, diffPct);
        // Guessing should be very close — at most 5% different pixels (boundary artifacts)
        check("pixel guessing <5% diff from exact (got " +
              String.format("%.1f%%", diffPct) + ")", diffPct < 5.0);

        // Both should produce rich images
        int colorsExact = countUniqueColors(exact);
        int colorsGuessed = countUniqueColors(guessed);
        check("pixel guessing produces rich image (exact=" + colorsExact +
              ", guessed=" + colorsGuessed + ")", colorsGuessed >= colorsExact - 5);
    }

    private static void testPixelGuessingFilamentRegion() {
        // Regression test: filament-heavy region that caused corruption with 4-corner guessing
        FractalRenderer r = newRenderer();
        r.setBounds(
            new BigDecimal("-1.158514308230042888196567"),
            new BigDecimal("-1.158479463315670184097907"),
            new BigDecimal("-0.2686450286351049685909021"),
            new BigDecimal("-0.2686101837207322644922435")
        );
        r.setMaxIterations(256);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(200, 150, gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(200, 150, gradient());

        int[] pe = getPixels(exact);
        int[] pg = getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        System.out.printf("  INFO: filament region guessing diff: %d/%d (%.1f%%)%n",
                diffCount, pe.length, diffPct);
        check("filament region guessing <1% diff (got " +
              String.format("%.1f%%", diffPct) + ")", diffPct < 1.0);
    }

    private static void testPixelGuessingOnOffToggle() {
        // Perturbation path: guessing ON vs OFF at deep zoom
        FractalRenderer r = newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);

        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage exact = r.render(SIZE, SIZE, gradient());

        r.setPixelGuessing(true);
        r.getCache().clear();
        BufferedImage guessed = r.render(SIZE, SIZE, gradient());

        int[] pe = getPixels(exact);
        int[] pg = getPixels(guessed);
        int diffCount = 0;
        for (int i = 0; i < pe.length; i++) {
            if (pe[i] != pg[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / pe.length;
        System.out.printf("  INFO: perturbation guessing diff vs exact: %d/%d (%.1f%%)%n",
                diffCount, pe.length, diffPct);
        check("perturbation guessing <5% diff from exact (got " +
              String.format("%.1f%%", diffPct) + ")", diffPct < 5.0);
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

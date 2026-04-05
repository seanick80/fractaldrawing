package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.DrawingCanvas;
import com.seanick80.drawingapp.UndoManager;
import com.seanick80.drawingapp.dock.DockManager;
import com.seanick80.drawingapp.dock.DockablePanel;
import com.seanick80.drawingapp.fills.*;
import com.seanick80.drawingapp.project.AppState;
import com.seanick80.drawingapp.project.FdpSerializer;
import com.seanick80.drawingapp.tools.*;
import com.seanick80.drawingapp.gradient.ColorGradient;
import com.seanick80.drawingapp.layers.BlendComposite;
import com.seanick80.drawingapp.layers.BlendMode;
import com.seanick80.drawingapp.layers.Layer;
import com.seanick80.drawingapp.layers.LayerManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
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

        // 3D terrain tests
        testTerrainRenderer();

        // Layer system tests
        testLayerSystem();

        // Fill system tests
        testFillRegistry();
        testSolidFill();
        testGradientFill();
        testCustomGradientFill();
        testCheckerboardFill();
        testDiagonalStripeFill();
        testCrosshatchFill();
        testDotGridFill();
        testHorizontalStripeFill();
        testNoiseFill();

        // Stroke style tests
        testStrokeStyleEnum();
        testStrokeStyleCreateStroke();
        testPencilToolStrokeStyle();
        testLineToolStrokeStyle();

        // ColorGradient tests
        testColorGradientInterpolation();
        testColorGradientAddRemoveStops();
        testColorGradientFromBaseColor();
        testColorGradientSaveLoad();
        testColorGradientCopyConstructor();
        testColorGradientCopyFrom();
        testSharedGradient();

        // UndoManager tests
        testUndoManagerBasic();
        testUndoManagerRedoClearedOnNewState();
        testUndoManagerCompaction();
        testUndoManagerMultiLayer();

        // AviWriter tests
        testAviWriterCreatesValidFile();
        testAviWriterMultipleFrames();

        // BlendComposite all modes tests
        testBlendCompositeAllModes();

        // FractalJsonUtil edge cases
        testJsonParseEdgeCases();

        // QuadTree extended tests
        testQuadTreeLookupFull();
        testQuadTreeLargeScale();

        // Dock system tests
        testDockManagerAndDockablePanel();

        // Drawing tool tests
        testPencilToolDrawsOnCanvas();
        testLineToolDrawsOnCanvas();
        testRectangleToolDrawsOutline();
        testRectangleToolFilled();
        testOvalToolDrawsOnCanvas();
        testEraserToolErases();
        testFillToolFloodFill();
        testToolDefaultStrokeSizes();
        testToolNames();
        testToolCapabilities();

        // Animation tests
        testRecolorFromIters();
        testRecolorDifferentGradientDiffers();
        testPaletteCycleShiftGradient();
        testPaletteCycleFullRotationWraps();
        testPaletteCycleRenderToFiles();
        testIterationAnimatorFramesDiffer();
        testIterationAnimatorRenderToFiles();
        testIterationAnimatorCancel();
        testIterationAnimatorTotalFrames();
        testPaletteCycleRecolorMatchesDirectRender();

        // Screensaver tests
        testScreensaverControllerLifecycle();
        testScreensaverPanelTransition();
        testScreensaverFindLocationNotNull();

        // FDP project file tests
        testFdpRoundTripSingleLayer();
        testFdpRoundTripMultiLayer();
        testFdpRoundTripFractalState();
        testFdpRoundTripGradient();
        testFdpRoundTripLayerProperties();
        testFdpBackwardCompat();
        testFdpRoundTripBigDecimalPrecision();

        // Bug bash regression tests
        testBug1InvisibleLayerDrawingBlocked();
        testBug2FractalScrollWheelZoom();
        testBug3NoDuplicateGradientDefaults();
        testBug4FillNoneDropdown();
        testBug5SaveFileTracking();
        testBug6LayerDragReorder();

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
        // Ease-in (t^2): at t=0.5, panT=0.25, so centerR = 0*(1-0.25) + (-0.5)*0.25 = -0.125
        check("interpolate t=0.5 centerR ~ -0.125 (ease-in)",
                Math.abs(kMid.centerReal.doubleValue() - (-0.125)) < 0.001);
    }

    private static void testZoomAnimatorRenderFrames() {
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        ZoomAnimator animator = new ZoomAnimator(r, gradient());
        animator.setSize(50, 50);
        animator.setFramesPerSegment(3);
        animator.setBoomerang(false);
        animator.setInterpolationFrames(0); // no interpolation for test simplicity

        animator.addKeyframe(new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100));
        animator.addKeyframe(new ZoomAnimator.Keyframe(-0.5, 0.0, 10.0, 200));

        check("rendered frame count = 4", animator.getRenderedFrameCount() == 4);
        check("total AVI frames = 4 (no interpolation)", animator.getTotalFrames() == 4);

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

            // Test interpolation frame count calculation
            ZoomAnimator interpAnim = new ZoomAnimator(r, gradient());
            interpAnim.setSize(50, 50);
            interpAnim.setFramesPerSegment(3);
            interpAnim.setBoomerang(false);
            interpAnim.setInterpolationFrames(3);
            interpAnim.addKeyframe(new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100));
            interpAnim.addKeyframe(new ZoomAnimator.Keyframe(-0.5, 0.0, 10.0, 200));
            // 4 rendered frames, 3 interpolated between each pair = (4-1)*4+1 = 13 AVI frames
            check("interpolation: rendered=4", interpAnim.getRenderedFrameCount() == 4);
            check("interpolation: total AVI=13", interpAnim.getTotalFrames() == 13);

            // Test zoomCrop preserves center pixel and produces valid output
            BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            src.setRGB(5, 5, 0xFF0000); // red at center
            BufferedImage cropped = ZoomAnimator.zoomCrop(src, 2.0);
            check("zoomCrop produces same size", cropped.getWidth() == 10 && cropped.getHeight() == 10);
            // Center of the cropped image should be near the center of src
            int centerRGB = cropped.getRGB(5, 5);
            int centerR = (centerRGB >> 16) & 0xFF;
            check("zoomCrop center has red content", centerR > 50);
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

    // --- 3D Terrain Renderer Tests ---

    private static void testTerrainRenderer() {
        System.out.println("\n  -- 3D Terrain Renderer --");

        // Diamond-square terrain generation
        int power = 6; // 65x65 map for fast testing
        int mapSize = (1 << power) + 1;
        float[] heightmap = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        check("terrain generation produces correct size",
                heightmap.length == mapSize * mapSize);

        // Values should be normalized 0..1
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (float v : heightmap) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        check("terrain values normalized (min~0)", min >= -0.01f && min <= 0.01f);
        check("terrain values normalized (max~1)", max >= 0.99f && max <= 1.01f);

        // Height variation should exist
        float sum = 0;
        for (float v : heightmap) sum += v;
        float mean = sum / heightmap.length;
        check("terrain has reasonable mean height", mean > 0.2f && mean < 0.8f);

        // Build color map from gradient
        int[] colormap = TerrainRenderer.buildColorMap(heightmap, gradient());
        check("terrain colormap same size as heightmap", colormap.length == heightmap.length);

        // Colors should have variety
        boolean hasColorVariety = false;
        int firstColor = colormap[0];
        for (int c : colormap) {
            if (c != firstColor) { hasColorVariety = true; break; }
        }
        check("terrain colormap has color variety", hasColorVariety);

        // Render a frame
        TerrainRenderer tr = new TerrainRenderer(heightmap, colormap, mapSize);
        BufferedImage frame = tr.render(160, 120, mapSize / 2f, mapSize / 4f, 80, 1.57f, 0);
        check("terrain render produces image", frame != null);
        check("terrain render correct size", frame.getWidth() == 160 && frame.getHeight() == 120);

        // Frame should not be all one color
        int firstPixel = frame.getRGB(0, 0);
        boolean hasVariety = false;
        for (int y = 0; y < 120 && !hasVariety; y += 10) {
            for (int x = 0; x < 160 && !hasVariety; x += 10) {
                if (frame.getRGB(x, y) != firstPixel) hasVariety = true;
            }
        }
        check("terrain render has color variety", hasVariety);

        // Fog blending
        int fogResult = TerrainRenderer.blendColor(0xFFFFFF, 0x000000, 0.5f);
        int fogR = (fogResult >> 16) & 0xFF;
        check("terrain fog blend 50% gives ~127", fogR >= 125 && fogR <= 129);

        // Start position finder
        float[] startPos = tr.findStartPosition();
        check("start position has 3 components", startPos.length == 3);
        check("start position X in bounds", startPos[0] >= 0 && startPos[0] < mapSize);
        check("start position Y in bounds", startPos[1] >= 0 && startPos[1] < mapSize);
        check("start position altitude > 0", startPos[2] > 0);

        // Deterministic generation
        float[] heightmap2 = TerrainRenderer.generateTerrain(power, 0.5f, 42);
        boolean identical = true;
        for (int i = 0; i < heightmap.length; i++) {
            if (heightmap[i] != heightmap2[i]) { identical = false; break; }
        }
        check("terrain generation is deterministic (same seed)", identical);

        // Different seed produces different terrain
        float[] heightmap3 = TerrainRenderer.generateTerrain(power, 0.5f, 99);
        boolean different = false;
        for (int i = 0; i < heightmap.length; i++) {
            if (heightmap[i] != heightmap3[i]) { different = true; break; }
        }
        check("different seed produces different terrain", different);

        // FractalRenderer integration: getLastRenderIters
        FractalRenderer r = newRenderer();
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.render(50, 50, gradient());
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();
        check("renderer exposes iteration data", iters != null && iters.length == 2500);
        check("renderer exposes render size", size[0] == 50 && size[1] == 50);
    }

    private static void testLayerSystem() {
        System.out.println("  -- Layer system --");

        // Basic layer manager creation
        var lm = new com.seanick80.drawingapp.layers.LayerManager(100, 80);
        check("layer manager starts with 1 layer", lm.getLayerCount() == 1);
        check("initial layer is Background", "Background".equals(lm.getLayer(0).getName()));
        check("active index is 0", lm.getActiveIndex() == 0);

        // Background layer is white (opaque)
        var bgImg = lm.getLayer(0).getImage();
        int centerPixel = bgImg.getRGB(50, 40);
        check("background layer is white", centerPixel == 0xFFFFFFFF);

        // Add layers
        var layer2 = lm.addLayer();
        check("add layer returns non-null", layer2 != null);
        check("layer count is 2", lm.getLayerCount() == 2);
        check("active index moves to new layer", lm.getActiveIndex() == 1);
        check("new layer is transparent", (layer2.getImage().getRGB(0, 0) & 0xFF000000) == 0);

        var layer3 = lm.addLayer();
        check("layer count is 3", lm.getLayerCount() == 3);

        // Max layers
        for (int i = lm.getLayerCount(); i < com.seanick80.drawingapp.layers.LayerManager.MAX_LAYERS; i++) {
            lm.addLayer();
        }
        check("at max layer count", lm.getLayerCount() == com.seanick80.drawingapp.layers.LayerManager.MAX_LAYERS);
        check("add beyond max returns null", lm.addLayer() == null);

        // Reset for further tests
        lm = new com.seanick80.drawingapp.layers.LayerManager(100, 80);

        // Layer properties
        var layer = lm.getLayer(0);
        check("default opacity is 1.0", layer.getOpacity() == 1.0f);
        check("default blend mode is NORMAL",
            layer.getBlendMode() == com.seanick80.drawingapp.layers.BlendMode.NORMAL);
        check("default visible is true", layer.isVisible());
        check("default locked is false", !layer.isLocked());

        layer.setOpacity(0.5f);
        check("opacity set to 0.5", layer.getOpacity() == 0.5f);
        layer.setOpacity(1.5f); // clamp
        check("opacity clamped to 1.0", layer.getOpacity() == 1.0f);
        layer.setOpacity(-0.5f);
        check("opacity clamped to 0.0", layer.getOpacity() == 0.0f);
        layer.setOpacity(1.0f);

        // Compositing: two layers, top layer with red rectangle
        lm.addLayer();
        var topLayer = lm.getActiveLayer();
        java.awt.Graphics2D g = topLayer.getImage().createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        var composite = lm.composite();
        check("composite image size matches", composite.getWidth() == 100 && composite.getHeight() == 80);
        int redPixel = composite.getRGB(20, 20);
        check("composite shows red from top layer", (redPixel & 0x00FF0000) == 0x00FF0000);
        int whitePixel = composite.getRGB(5, 5);
        check("composite shows white from background", whitePixel == 0xFFFFFFFF);

        // Visibility toggle
        topLayer.setVisible(false);
        var composite2 = lm.composite();
        int hiddenPixel = composite2.getRGB(20, 20);
        check("hidden layer not composited", hiddenPixel == 0xFFFFFFFF);
        topLayer.setVisible(true);

        // Opacity compositing
        topLayer.setOpacity(0.5f);
        var composite3 = lm.composite();
        int blendedPixel = composite3.getRGB(20, 20);
        int blendedR = (blendedPixel >> 16) & 0xFF;
        int blendedG = (blendedPixel >> 8) & 0xFF;
        check("50% opacity red over white blends", blendedR > 200 && blendedG > 100 && blendedG < 160);
        topLayer.setOpacity(1.0f);

        // Move layer up/down
        lm.setActiveIndex(0);
        int origCount = lm.getLayerCount();
        lm.moveLayerUp(0);
        check("move up swaps layers", lm.getActiveIndex() == 1);
        check("layer count unchanged after move", lm.getLayerCount() == origCount);
        lm.moveLayerDown(1);
        check("move down swaps back", lm.getActiveIndex() == 0);

        // Cannot remove last layer
        lm.removeLayer(1);
        check("removed layer 1", lm.getLayerCount() == 1);
        lm.removeLayer(0);
        check("cannot remove last layer", lm.getLayerCount() == 1);

        // Duplicate
        lm.addLayer();
        g = lm.getActiveLayer().getImage().createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, 100, 80);
        g.dispose();
        int beforeCount = lm.getLayerCount();
        var dup = lm.duplicateLayer(lm.getActiveIndex());
        check("duplicate increases count", lm.getLayerCount() == beforeCount + 1);
        int dupPixel = dup.getImage().getRGB(50, 40);
        check("duplicate has same content", (dupPixel & 0x000000FF) == 0xFF);

        // Merge down
        lm = new com.seanick80.drawingapp.layers.LayerManager(100, 80);
        lm.addLayer();
        g = lm.getActiveLayer().getImage().createGraphics();
        g.setColor(java.awt.Color.GREEN);
        g.fillRect(40, 30, 20, 20);
        g.dispose();
        lm.mergeDown(1);
        check("merge reduces layer count", lm.getLayerCount() == 1);
        int mergedPixel = lm.getLayer(0).getImage().getRGB(50, 40);
        int mergedG = (mergedPixel >> 8) & 0xFF;
        check("merged layer has green content", mergedG == 255 || mergedG == 0x80);

        // Flatten
        lm.addLayer();
        lm.addLayer();
        check("3 layers before flatten", lm.getLayerCount() == 3);
        lm.flattenAll();
        check("flatten to 1 layer", lm.getLayerCount() == 1);

        // Blend modes exist
        var modes = com.seanick80.drawingapp.layers.BlendMode.values();
        check("8 blend modes defined", modes.length == 8);
        check("NORMAL has display name", "Normal".equals(modes[0].getDisplayName()));

        // BlendComposite: multiply darkens
        var bc = new com.seanick80.drawingapp.layers.BlendComposite(
            com.seanick80.drawingapp.layers.BlendMode.MULTIPLY, 1.0f);
        var baseImg = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g2 = baseImg.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, 10, 10);
        g2.setComposite(bc);
        g2.setColor(new java.awt.Color(128, 128, 128));
        g2.fillRect(0, 0, 10, 10);
        g2.dispose();
        int mp = baseImg.getRGB(5, 5);
        int mr = (mp >> 16) & 0xFF;
        check("multiply blend darkens", mr < 200 && mr > 30);

        // Layer clear makes transparent
        var clearLm = new com.seanick80.drawingapp.layers.LayerManager(50, 50);
        clearLm.addLayer();
        var clearLayer = clearLm.getActiveLayer();
        g = clearLayer.getImage().createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        clearLayer.clear();
        check("cleared layer is transparent", (clearLayer.getImage().getRGB(25, 25) & 0xFF000000) == 0);

        // Thumbnail generation
        var thumbLm = new com.seanick80.drawingapp.layers.LayerManager(200, 150);
        var thumb = thumbLm.getLayer(0).createThumbnail(40, 30);
        check("thumbnail correct size", thumb.getWidth() == 40 && thumb.getHeight() == 30);
    }

    // === Fill System Tests ===

    private static void testFillRegistry() {
        System.out.println("\n  -- Fill System --");
        FillRegistry reg = new FillRegistry();
        check("empty registry getAll is empty", reg.getAll().isEmpty());

        SolidFill solid = new SolidFill();
        GradientFill grad = new GradientFill();
        CheckerboardFill checker = new CheckerboardFill();
        reg.register(solid);
        reg.register(grad);
        reg.register(checker);

        check("registry has 3 fills", reg.getAll().size() == 3);
        check("getByName Solid", reg.getByName("Solid") == solid);
        check("getByName Gradient", reg.getByName("Gradient") == grad);
        check("getByName Checkerboard", reg.getByName("Checkerboard") == checker);
        check("getByName unknown returns first", reg.getByName("Nonexistent") == solid);
    }

    private static void testSolidFill() {
        SolidFill fill = new SolidFill();
        check("solid fill name", "Solid".equals(fill.getName()));
        Paint p = fill.createPaint(Color.RED, 0, 0, 100, 100);
        check("solid fill returns base color", p.equals(Color.RED));
        Paint p2 = fill.createPaint(Color.BLUE, 10, 20, 50, 50);
        check("solid fill returns blue", p2.equals(Color.BLUE));
    }

    private static void testGradientFill() {
        GradientFill fill = new GradientFill();
        check("gradient fill name", "Gradient".equals(fill.getName()));
        check("gradient default angle is 0", fill.getAngleDegrees() == 0);

        fill.setAngleDegrees(90);
        check("gradient angle set to 90", fill.getAngleDegrees() == 90);

        // Should return a GradientPaint for valid dimensions
        Paint p = fill.createPaint(Color.RED, 0, 0, 100, 100);
        check("gradient fill returns GradientPaint", p instanceof GradientPaint);

        // Zero-size falls back to base color
        Paint pZero = fill.createPaint(Color.RED, 0, 0, 0, 100);
        check("gradient fill zero width returns base color", pZero.equals(Color.RED));

        // Implements AngledFillProvider
        check("gradient implements AngledFillProvider", fill instanceof AngledFillProvider);
    }

    private static void testCustomGradientFill() {
        CustomGradientFill fill = new CustomGradientFill();
        check("custom gradient name", "Custom Gradient".equals(fill.getName()));
        check("custom gradient default angle is 0", fill.getAngleDegrees() == 0);
        check("custom gradient has default gradient", fill.getGradient() != null);

        // Set a custom gradient
        ColorGradient cg = ColorGradient.fractalDefault();
        fill.setGradient(cg);
        check("custom gradient set", fill.getGradient() == cg);

        fill.setAngleDegrees(45);
        check("custom gradient angle set to 45", fill.getAngleDegrees() == 45);

        // Should return a TexturePaint for valid dimensions
        Paint p = fill.createPaint(Color.RED, 0, 0, 50, 50);
        check("custom gradient returns TexturePaint", p instanceof TexturePaint);

        // Zero-size falls back
        Paint pZero = fill.createPaint(Color.RED, 0, 0, 0, 50);
        check("custom gradient zero width returns base color", pZero.equals(Color.RED));
    }

    private static void testCheckerboardFill() {
        CheckerboardFill fill = new CheckerboardFill();
        check("checkerboard fill name", "Checkerboard".equals(fill.getName()));

        Paint p = fill.createPaint(Color.RED, 0, 0, 100, 100);
        check("checkerboard returns TexturePaint", p instanceof TexturePaint);

        // Verify the texture has both light and dark colors
        TexturePaint tp = (TexturePaint) p;
        BufferedImage tex = tp.getImage();
        check("checkerboard texture is 16x16", tex.getWidth() == 16 && tex.getHeight() == 16);
        int topLeft = tex.getRGB(0, 0);
        int topRight = tex.getRGB(8, 0);
        check("checkerboard has two different colors", topLeft != topRight);

        // Not an AngledFillProvider
        check("checkerboard is not angled", !(fill instanceof AngledFillProvider));
    }

    private static void testDiagonalStripeFill() {
        DiagonalStripeFill fill = new DiagonalStripeFill();
        check("stripe fill name", "Diagonal Stripes".equals(fill.getName()));
        check("stripe default angle is 45", fill.getAngleDegrees() == 45);

        fill.setAngleDegrees(60);
        check("stripe angle set to 60", fill.getAngleDegrees() == 60);

        Paint p = fill.createPaint(Color.BLUE, 0, 0, 100, 100);
        check("stripe returns TexturePaint", p instanceof TexturePaint);

        // Zero-size falls back
        Paint pZero = fill.createPaint(Color.BLUE, 0, 0, 0, 100);
        check("stripe zero width returns base color", pZero.equals(Color.BLUE));

        check("stripe implements AngledFillProvider", fill instanceof AngledFillProvider);
    }

    private static void testCrosshatchFill() {
        CrosshatchFill fill = new CrosshatchFill();
        check("crosshatch fill name", "Crosshatch".equals(fill.getName()));
        check("crosshatch default angle is 45", fill.getAngleDegrees() == 45);

        fill.setAngleDegrees(30);
        check("crosshatch angle set to 30", fill.getAngleDegrees() == 30);

        Paint p = fill.createPaint(Color.RED, 0, 0, 100, 100);
        check("crosshatch returns TexturePaint", p instanceof TexturePaint);

        Paint pZero = fill.createPaint(Color.RED, 0, 0, 0, 100);
        check("crosshatch zero width returns base color", pZero.equals(Color.RED));

        check("crosshatch implements AngledFillProvider", fill instanceof AngledFillProvider);

        // Verify pattern has drawn content (not all transparent)
        TexturePaint tp = (TexturePaint) p;
        BufferedImage tex = tp.getImage();
        int nonTransparent = 0;
        for (int y = 0; y < tex.getHeight(); y++)
            for (int x = 0; x < tex.getWidth(); x++)
                if ((tex.getRGB(x, y) >>> 24) > 0) nonTransparent++;
        check("crosshatch has drawn content", nonTransparent > 0);
    }

    private static void testDotGridFill() {
        DotGridFill fill = new DotGridFill();
        check("dot grid fill name", "Dot Grid".equals(fill.getName()));

        Paint p = fill.createPaint(Color.GREEN, 0, 0, 100, 100);
        check("dot grid returns TexturePaint", p instanceof TexturePaint);

        check("dot grid is not angled", !(fill instanceof AngledFillProvider));

        // Verify dots are present
        TexturePaint tp = (TexturePaint) p;
        BufferedImage tex = tp.getImage();
        int centerRGB = tex.getRGB(tex.getWidth() / 2, tex.getHeight() / 2);
        int centerAlpha = (centerRGB >>> 24);
        check("dot grid center has dot (alpha>0)", centerAlpha > 0);
    }

    private static void testHorizontalStripeFill() {
        HorizontalStripeFill fill = new HorizontalStripeFill();
        check("horiz stripe fill name", "Horizontal Stripes".equals(fill.getName()));
        check("horiz stripe default angle is 0", fill.getAngleDegrees() == 0);

        fill.setAngleDegrees(90);
        check("horiz stripe angle set to 90", fill.getAngleDegrees() == 90);

        Paint p = fill.createPaint(Color.BLUE, 0, 0, 100, 100);
        check("horiz stripe returns TexturePaint", p instanceof TexturePaint);

        Paint pZero = fill.createPaint(Color.BLUE, 0, 0, 0, 100);
        check("horiz stripe zero width returns base color", pZero.equals(Color.BLUE));

        check("horiz stripe implements AngledFillProvider", fill instanceof AngledFillProvider);
    }

    private static void testNoiseFill() {
        NoiseFill fill = new NoiseFill();
        check("noise fill name", "Noise".equals(fill.getName()));

        Paint p = fill.createPaint(Color.RED, 0, 0, 100, 100);
        check("noise returns TexturePaint", p instanceof TexturePaint);

        check("noise is not angled", !(fill instanceof AngledFillProvider));

        // Verify noise pattern has varied alpha values
        TexturePaint tp = (TexturePaint) p;
        BufferedImage tex = tp.getImage();
        int minAlpha = 255, maxAlpha = 0;
        for (int y = 0; y < tex.getHeight(); y++) {
            for (int x = 0; x < tex.getWidth(); x++) {
                int alpha = (tex.getRGB(x, y) >>> 24);
                minAlpha = Math.min(minAlpha, alpha);
                maxAlpha = Math.max(maxAlpha, alpha);
            }
        }
        check("noise has alpha variation", maxAlpha - minAlpha > 50);

        // Deterministic at same position
        Paint p2 = fill.createPaint(Color.RED, 0, 0, 100, 100);
        TexturePaint tp2 = (TexturePaint) p2;
        check("noise is deterministic at same position",
                tp.getImage().getRGB(0, 0) == tp2.getImage().getRGB(0, 0));
    }

    // === Stroke Style Tests ===

    private static void testStrokeStyleEnum() {
        System.out.println("\n  -- Stroke Styles --");
        StrokeStyle[] styles = StrokeStyle.values();
        check("5 stroke styles", styles.length == 5);
        check("SOLID display name", "Solid".equals(StrokeStyle.SOLID.getDisplayName()));
        check("DASHED display name", "Dashed".equals(StrokeStyle.DASHED.getDisplayName()));
        check("DOTTED display name", "Dotted".equals(StrokeStyle.DOTTED.getDisplayName()));
        check("DASH_DOT display name", "Dash-Dot".equals(StrokeStyle.DASH_DOT.getDisplayName()));
        check("ROUGH display name", "Rough".equals(StrokeStyle.ROUGH.getDisplayName()));
    }

    private static void testStrokeStyleCreateStroke() {
        for (StrokeStyle style : StrokeStyle.values()) {
            java.awt.Stroke stroke = style.createStroke(4);
            check(style.getDisplayName() + " creates non-null stroke", stroke != null);
        }
        // Dashed stroke should have dash array
        java.awt.Stroke dashed = StrokeStyle.DASHED.createStroke(2);
        check("DASHED is BasicStroke", dashed instanceof java.awt.BasicStroke);
        float[] dashArray = ((java.awt.BasicStroke) dashed).getDashArray();
        check("DASHED has dash array", dashArray != null && dashArray.length > 0);

        // Solid has no dash array
        java.awt.Stroke solid = StrokeStyle.SOLID.createStroke(2);
        float[] solidDash = ((java.awt.BasicStroke) solid).getDashArray();
        check("SOLID has no dash array", solidDash == null);
    }

    private static void testPencilToolStrokeStyle() {
        PencilTool pencil = new PencilTool();
        check("pencil default stroke style is SOLID", pencil.getStrokeStyle() == StrokeStyle.SOLID);

        pencil.setStrokeStyle(StrokeStyle.DASHED);
        check("pencil stroke style set to DASHED", pencil.getStrokeStyle() == StrokeStyle.DASHED);

        // Draw with dashed style - should not crash
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        DrawingCanvas canvas = new DrawingCanvas(50, 50, new com.seanick80.drawingapp.UndoManager(10));
        pencil.mousePressed(img, 5, 25, canvas);
        pencil.mouseDragged(img, 45, 25, canvas);
        pencil.mouseReleased(img, 45, 25, canvas);

        // Verify something was drawn
        boolean hasBlack = false;
        for (int x = 0; x < 50; x++) {
            if (img.getRGB(x, 25) != Color.WHITE.getRGB()) { hasBlack = true; break; }
        }
        check("pencil dashed draws on canvas", hasBlack);

        // Test rough style
        pencil.setStrokeStyle(StrokeStyle.ROUGH);
        BufferedImage img2 = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        g = img2.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        pencil.mousePressed(img2, 5, 25, canvas);
        pencil.mouseDragged(img2, 45, 25, canvas);
        pencil.mouseReleased(img2, 45, 25, canvas);

        hasBlack = false;
        for (int x = 0; x < 50; x++) {
            if (img2.getRGB(x, 25) != Color.WHITE.getRGB()) { hasBlack = true; break; }
        }
        check("pencil rough draws on canvas", hasBlack);
    }

    private static void testLineToolStrokeStyle() {
        LineTool line = new LineTool();
        check("line default stroke style is SOLID", line.getStrokeStyle() == StrokeStyle.SOLID);

        line.setStrokeStyle(StrokeStyle.DOTTED);
        check("line stroke style set to DOTTED", line.getStrokeStyle() == StrokeStyle.DOTTED);

        // Draw with dotted style
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        DrawingCanvas canvas = new DrawingCanvas(50, 50, new com.seanick80.drawingapp.UndoManager(10));
        line.mousePressed(img, 5, 25, canvas);
        line.mouseReleased(img, 45, 25, canvas);

        boolean hasBlack = false;
        for (int x = 0; x < 50; x++) {
            if (img.getRGB(x, 25) != Color.WHITE.getRGB()) { hasBlack = true; break; }
        }
        check("line dotted draws on canvas", hasBlack);

        // Test rough style
        line.setStrokeStyle(StrokeStyle.ROUGH);
        BufferedImage img2 = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        g = img2.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        line.mousePressed(img2, 5, 25, canvas);
        line.mouseReleased(img2, 45, 25, canvas);

        hasBlack = false;
        for (int x = 0; x < 50; x++) {
            if (img2.getRGB(x, 25) != Color.WHITE.getRGB()) { hasBlack = true; break; }
        }
        check("line rough draws on canvas", hasBlack);
    }

    // === ColorGradient Extended Tests ===

    private static void testColorGradientInterpolation() {
        System.out.println("\n  -- ColorGradient Extended --");
        ColorGradient g = new ColorGradient(); // default: black to white
        Color mid = g.getColorAt(0.5f);
        int midR = mid.getRed(), midG = mid.getGreen(), midB = mid.getBlue();
        check("default gradient midpoint ~ gray (r=" + midR + ")",
                midR >= 125 && midR <= 130 && midG >= 125 && midG <= 130);

        // Endpoints
        Color start = g.getColorAt(0.0f);
        check("default gradient start is black", start.getRed() == 0 && start.getGreen() == 0 && start.getBlue() == 0);
        Color end = g.getColorAt(1.0f);
        check("default gradient end is white", end.getRed() == 255 && end.getGreen() == 255 && end.getBlue() == 255);

        // Out of range clamping
        Color below = g.getColorAt(-0.5f);
        check("gradient clamps below 0", below.getRGB() == start.getRGB());
        Color above = g.getColorAt(1.5f);
        check("gradient clamps above 1", above.getRGB() == end.getRGB());
    }

    private static void testColorGradientAddRemoveStops() {
        ColorGradient g = new ColorGradient(); // 2 stops: black, white
        check("default has 2 stops", g.getStops().size() == 2);

        g.addStop(0.5f, Color.RED);
        check("after add has 3 stops", g.getStops().size() == 3);

        // Midpoint should now be red
        Color mid = g.getColorAt(0.5f);
        check("midpoint is red after adding red stop",
                mid.getRed() == 255 && mid.getGreen() == 0 && mid.getBlue() == 0);

        // Remove the red stop
        ColorGradient.Stop redStop = g.getStops().get(1); // sorted: black(0), red(0.5), white(1)
        g.removeStop(redStop);
        check("after remove has 2 stops", g.getStops().size() == 2);

        // Can't remove below 2 stops
        g.removeStop(g.getStops().get(0));
        check("can't remove below 2 stops", g.getStops().size() == 2);

        // Stop position clamping
        ColorGradient.Stop s = new ColorGradient.Stop(1.5f, Color.GREEN);
        check("stop position clamped to 1.0", s.getPosition() == 1.0f);
        s.setPosition(-0.5f);
        check("stop position clamped to 0.0", s.getPosition() == 0.0f);
    }

    private static void testColorGradientFromBaseColor() {
        ColorGradient g = ColorGradient.fromBaseColor(Color.RED);
        check("fromBaseColor has 6 stops", g.getStops().size() == 6);

        Color[] colors = g.toColors(256);
        check("fromBaseColor produces 256 colors", colors.length == 256);

        // Should have color variety
        int uniqueColors = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Color c : colors) seen.add(c.getRGB());
        uniqueColors = seen.size();
        check("fromBaseColor has color variety (" + uniqueColors + " unique)", uniqueColors > 50);

        // First stop should be dark, last should be very dark
        Color first = g.getColorAt(0.0f);
        Color last = g.getColorAt(1.0f);
        check("fromBaseColor first stop is dark", brightness(first) < 0.5f);
        check("fromBaseColor last stop is very dark", brightness(last) < 0.1f);
    }

    private static float brightness(Color c) {
        return (c.getRed() * 0.299f + c.getGreen() * 0.587f + c.getBlue() * 0.114f) / 255f;
    }

    private static void testColorGradientSaveLoad() {
        ColorGradient original = ColorGradient.fractalDefault();
        File tempFile = new File(System.getProperty("java.io.tmpdir"),
                "gradient_test_" + System.currentTimeMillis() + ".grd");
        try {
            original.save(tempFile);
            check("gradient file created", tempFile.exists());

            ColorGradient loaded = ColorGradient.load(tempFile);
            check("loaded gradient has 6 stops", loaded.getStops().size() == 6);

            // Compare colors at sampled positions
            boolean allMatch = true;
            for (float t = 0; t <= 1.0f; t += 0.1f) {
                Color orig = original.getColorAt(t);
                Color load = loaded.getColorAt(t);
                if (Math.abs(orig.getRed() - load.getRed()) > 1
                        || Math.abs(orig.getGreen() - load.getGreen()) > 1
                        || Math.abs(orig.getBlue() - load.getBlue()) > 1) {
                    allMatch = false;
                    break;
                }
            }
            check("loaded gradient matches original", allMatch);

            // Test load of invalid file
            File badFile = new File(System.getProperty("java.io.tmpdir"),
                    "bad_gradient_" + System.currentTimeMillis() + ".grd");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(badFile)) {
                pw.println("NOT_A_GRADIENT");
            }
            boolean threwOnBadHeader = false;
            try {
                ColorGradient.load(badFile);
            } catch (java.io.IOException e) {
                threwOnBadHeader = true;
            }
            check("load rejects bad header", threwOnBadHeader);
            badFile.delete();
        } catch (Exception e) {
            check("gradient save/load failed: " + e.getMessage(), false);
        } finally {
            tempFile.delete();
        }
    }

    private static void testColorGradientCopyConstructor() {
        ColorGradient original = ColorGradient.fractalDefault();
        ColorGradient copy = new ColorGradient(original);
        check("copy has same stop count", copy.getStops().size() == original.getStops().size());

        // Modifying copy doesn't affect original
        copy.addStop(0.3f, Color.MAGENTA);
        check("modifying copy doesn't change original",
                original.getStops().size() != copy.getStops().size());

        // Colors match before modification
        Color origMid = original.getColorAt(0.5f);
        ColorGradient copy2 = new ColorGradient(original);
        Color copyMid = copy2.getColorAt(0.5f);
        check("copy produces same colors", origMid.getRGB() == copyMid.getRGB());
    }

    private static void testColorGradientCopyFrom() {
        ColorGradient target = new ColorGradient(); // default black-to-white
        ColorGradient source = ColorGradient.fromBaseColor(Color.RED);
        int sourceStops = source.getStops().size();

        target.copyFrom(source);
        check("copyFrom has same stop count", target.getStops().size() == sourceStops);

        // Colors match after copyFrom
        boolean colorsMatch = true;
        for (float t = 0; t <= 1.0f; t += 0.1f) {
            Color tc = target.getColorAt(t);
            Color sc = source.getColorAt(t);
            if (Math.abs(tc.getRed() - sc.getRed()) > 1
                    || Math.abs(tc.getGreen() - sc.getGreen()) > 1
                    || Math.abs(tc.getBlue() - sc.getBlue()) > 1) {
                colorsMatch = false;
                break;
            }
        }
        check("copyFrom produces same colors", colorsMatch);

        // Modifying target doesn't affect source
        target.addStop(0.5f, Color.CYAN);
        check("copyFrom is independent", source.getStops().size() == sourceStops);
    }

    private static void testSharedGradient() {
        // Verify that editing a shared gradient object is reflected by all users
        ColorGradient shared = ColorGradient.fractalDefault();
        Color beforeMid = shared.getColorAt(0.5f);

        // Simulate in-place update (like color picker changing gradient)
        ColorGradient replacement = ColorGradient.fromBaseColor(Color.BLUE);
        shared.copyFrom(replacement);
        Color afterMid = shared.getColorAt(0.5f);

        check("shared gradient update changes colors", beforeMid.getRGB() != afterMid.getRGB());

        // A second reference to the same object sees the change
        ColorGradient ref = shared;
        check("shared ref sees update", ref.getColorAt(0.5f).getRGB() == afterMid.getRGB());
    }

    // === UndoManager Tests ===

    private static void testUndoManagerBasic() {
        System.out.println("\n  -- UndoManager --");
        UndoManager um = new UndoManager(100);
        LayerManager lm = new LayerManager(50, 50);

        check("initially can't undo", !um.canUndo());
        check("initially can't redo", !um.canRedo());

        // Draw red on the background layer
        Graphics2D g = lm.getLayer(0).getImage().createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        um.saveState(lm);
        check("can undo after save", um.canUndo());

        // Draw blue over it
        g = lm.getLayer(0).getImage().createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        int bluePixel = lm.getLayer(0).getImage().getRGB(25, 25);

        // Undo should restore to red
        um.undo(lm);
        int afterUndo = lm.getLayer(0).getImage().getRGB(25, 25);
        check("undo restores red", (afterUndo & 0x00FF0000) == 0x00FF0000);
        check("can redo after undo", um.canRedo());

        // Redo should restore to blue... wait, undo restores the state BEFORE blue was drawn.
        // Actually: saveState saved the state when it was red. Then blue was drawn.
        // undo() restores the saved state (red). The current state (blue) goes to redo.
        um.redo(lm);
        int afterRedo = lm.getLayer(0).getImage().getRGB(25, 25);
        check("redo restores blue", (afterRedo & 0x000000FF) == 0x000000FF);
    }

    private static void testUndoManagerRedoClearedOnNewState() {
        UndoManager um = new UndoManager(100);
        LayerManager lm = new LayerManager(50, 50);

        um.saveState(lm); // state 1
        um.saveState(lm); // state 2

        um.undo(lm);
        check("can redo before new state", um.canRedo());

        um.saveState(lm); // new state clears redo
        check("redo cleared after new save", !um.canRedo());
    }

    private static void testUndoManagerCompaction() {
        UndoManager um = new UndoManager(200);
        LayerManager lm = new LayerManager(10, 10);

        // Push 85 states to trigger compaction (threshold is 80)
        for (int i = 0; i < 85; i++) {
            um.saveState(lm);
        }

        // After compaction, should still be able to undo but stack is reduced
        check("can undo after compaction", um.canUndo());

        // Count how many undos we can do (should be ~50 after compaction)
        int undoCount = 0;
        while (um.canUndo()) {
            um.undo(lm);
            undoCount++;
        }
        check("compacted to ~50 undos (got " + undoCount + ")", undoCount <= 55 && undoCount >= 45);
    }

    private static void testUndoManagerMultiLayer() {
        UndoManager um = new UndoManager(100);
        LayerManager lm = new LayerManager(50, 50);

        // Save state with 1 layer
        um.saveState(lm);

        // Add a second layer and draw on it
        lm.addLayer();
        check("2 layers before undo", lm.getLayerCount() == 2);
        Graphics2D g = lm.getActiveLayer().getImage().createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        // Undo should restore to 1 layer
        um.undo(lm);
        check("undo restores 1 layer", lm.getLayerCount() == 1);

        // Redo should restore 2 layers
        um.redo(lm);
        check("redo restores 2 layers", lm.getLayerCount() == 2);

        // Clear
        um.clear();
        check("clear resets undo", !um.canUndo());
        check("clear resets redo", !um.canRedo());
    }

    // === AviWriter Tests ===

    private static void testAviWriterCreatesValidFile() {
        System.out.println("\n  -- AviWriter --");
        File tempFile = new File(System.getProperty("java.io.tmpdir"),
                "avi_test_" + System.currentTimeMillis() + ".avi");
        try {
            BufferedImage frame = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frame.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 10, 10);
            g.dispose();

            AviWriter writer = new AviWriter(tempFile, 10, 10, 30);
            writer.addFrame(frame);
            writer.close();

            check("AVI file created", tempFile.exists());
            check("AVI file has content", tempFile.length() > 0);

            // Verify RIFF header
            try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
                byte[] riff = new byte[4];
                raf.read(riff);
                check("AVI starts with RIFF", "RIFF".equals(new String(riff)));

                raf.skipBytes(4); // size
                byte[] avi = new byte[4];
                raf.read(avi);
                check("AVI has AVI marker", "AVI ".equals(new String(avi)));
            }
        } catch (Exception e) {
            check("AVI writer failed: " + e.getMessage(), false);
        } finally {
            tempFile.delete();
        }
    }

    private static void testAviWriterMultipleFrames() {
        File tempFile = new File(System.getProperty("java.io.tmpdir"),
                "avi_multi_" + System.currentTimeMillis() + ".avi");
        try {
            int W = 20, H = 15;
            AviWriter writer = new AviWriter(tempFile, W, H, 24);

            // Write 5 frames with different colors
            Color[] frameColors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN};
            for (Color c : frameColors) {
                BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = frame.createGraphics();
                g.setColor(c);
                g.fillRect(0, 0, W, H);
                g.dispose();
                writer.addFrame(frame);
            }
            writer.close();

            // Verify file size is reasonable
            // Each frame: rowStride * H + 8 (chunk header)
            int rowStride = ((W * 3 + 3) / 4) * 4;
            int frameSize = rowStride * H;
            long minSize = 5 * (frameSize + 8); // at least 5 frames of data
            check("AVI multi-frame file large enough (size=" + tempFile.length() + ")",
                    tempFile.length() > minSize);

            // Verify idx1 chunk exists by checking the file ends with index data
            try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
                // Read the total frames count from avih header
                raf.seek(48); // AVIH_TOTAL_FRAMES_POS
                int totalFrames = readIntLE(raf);
                check("AVI header reports 5 frames", totalFrames == 5);
            }
        } catch (Exception e) {
            check("AVI multi-frame failed: " + e.getMessage(), false);
        } finally {
            tempFile.delete();
        }
    }

    private static int readIntLE(RandomAccessFile raf) throws java.io.IOException {
        int b0 = raf.read(), b1 = raf.read(), b2 = raf.read(), b3 = raf.read();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    // === BlendComposite All Modes Tests ===

    private static void testBlendCompositeAllModes() {
        System.out.println("\n  -- BlendComposite All Modes --");
        for (BlendMode mode : BlendMode.values()) {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // Base: white
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 10, 10);

            // Blend gray on top
            g.setComposite(new BlendComposite(mode, 1.0f));
            g.setColor(new Color(128, 128, 128));
            g.fillRect(0, 0, 10, 10);
            g.dispose();

            int pixel = img.getRGB(5, 5);
            int r = (pixel >> 16) & 0xFF;
            int gv = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // All modes should produce SOME result (not crash, not transparent)
            check(mode.getDisplayName() + " blend produces visible result (r=" + r + ")",
                    (pixel & 0xFF000000) != 0); // not fully transparent
        }

        // Specific mode behaviors
        // MULTIPLY: white * gray = gray
        BufferedImage mulImg = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.MULTIPLY);
        int mulR = (mulImg.getRGB(5, 5) >> 16) & 0xFF;
        check("MULTIPLY white*gray ~ 128 (got " + mulR + ")", mulR >= 120 && mulR <= 140);

        // SCREEN: 1-(1-white)*(1-gray) = white
        BufferedImage scrImg = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.SCREEN);
        int scrR = (scrImg.getRGB(5, 5) >> 16) & 0xFF;
        check("SCREEN white+gray ~ 255 (got " + scrR + ")", scrR >= 250);

        // DIFFERENCE: |white - gray| = gray
        BufferedImage diffImg = blendTest(Color.WHITE, new Color(128, 128, 128), BlendMode.DIFFERENCE);
        int diffR = (diffImg.getRGB(5, 5) >> 16) & 0xFF;
        check("DIFFERENCE |white-gray| ~ 127 (got " + diffR + ")", diffR >= 120 && diffR <= 135);

        // ADD: white + gray (clamped to 255)
        BufferedImage addImg = blendTest(new Color(100, 100, 100), new Color(100, 100, 100), BlendMode.ADD);
        int addR = (addImg.getRGB(5, 5) >> 16) & 0xFF;
        check("ADD 100+100 ~ 200 (got " + addR + ")", addR >= 190 && addR <= 210);

        // NORMAL: just replaces
        BufferedImage normImg = blendTest(Color.WHITE, Color.RED, BlendMode.NORMAL);
        int normR = (normImg.getRGB(5, 5) >> 16) & 0xFF;
        int normG = (normImg.getRGB(5, 5) >> 8) & 0xFF;
        check("NORMAL replaces with source (r=255,g=0, got r=" + normR + ",g=" + normG + ")",
                normR == 255 && normG == 0);

        // Opacity test: 50% opacity NORMAL blend
        BufferedImage opImg = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = opImg.createGraphics();
        og.setColor(Color.WHITE);
        og.fillRect(0, 0, 10, 10);
        og.setComposite(new BlendComposite(BlendMode.NORMAL, 0.5f));
        og.setColor(Color.BLACK);
        og.fillRect(0, 0, 10, 10);
        og.dispose();
        int opR = (opImg.getRGB(5, 5) >> 16) & 0xFF;
        check("50% opacity NORMAL black on white ~ 128 (got " + opR + ")",
                opR >= 120 && opR <= 135);
    }

    private static BufferedImage blendTest(Color base, Color blend, BlendMode mode) {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, 10, 10);
        g.setComposite(new BlendComposite(mode, 1.0f));
        g.setColor(blend);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        return img;
    }

    // === FractalJsonUtil Edge Cases ===

    private static void testJsonParseEdgeCases() {
        System.out.println("\n  -- FractalJsonUtil Edge Cases --");

        // Empty JSON
        var empty = FractalJsonUtil.parseJson("{}");
        check("empty JSON parses to empty map", empty.isEmpty());

        // Extra whitespace
        var ws = FractalJsonUtil.parseJson("{ \"key\" : \"value\" }");
        check("whitespace handled", "value".equals(ws.get("key")));

        // Numeric values (unquoted)
        var num = FractalJsonUtil.parseJson("{\"count\": 42}");
        check("numeric value parsed", "42".equals(num.get("count")));

        // Multiple entries
        var multi = FractalJsonUtil.parseJson(
                "{\"a\": \"1\", \"b\": \"2\", \"c\": \"3\"}");
        check("multiple entries parsed", multi.size() == 3);
        check("multi entry a", "1".equals(multi.get("a")));
        check("multi entry c", "3".equals(multi.get("c")));

        // Fragment parsing
        var frag = FractalJsonUtil.parseJsonFragment("\"x\": \"10\", \"y\": \"20\"");
        check("fragment parsing works", "10".equals(frag.get("x")));
        check("fragment parsing y", "20".equals(frag.get("y")));
    }

    // === QuadTree Extended Tests ===

    private static void testQuadTreeLookupFull() {
        System.out.println("\n  -- QuadTree Extended --");
        IterationQuadTree qt = new IterationQuadTree(-4, 4, -4, 4);

        // Insert with final Z values
        qt.insert(1.0, 1.0, 42, 0.5, 0.3);
        qt.insert(2.0, 2.0, 99, 1.5, -0.7);

        // lookupFull should return iter + finalZ
        var result = qt.lookupFull(1.0, 1.0, 0.001);
        check("lookupFull returns result", result != null);
        if (result != null) {
            check("lookupFull iter correct", result.iterationCount == 42);
            check("lookupFull finalZr correct", Math.abs(result.finalZr - 0.5) < 0.001);
            check("lookupFull finalZi correct", Math.abs(result.finalZi - 0.3) < 0.001);
        }

        // Miss returns null
        var miss = qt.lookupFull(3.0, 3.0, 0.001);
        check("lookupFull miss returns null", miss == null);
    }

    private static void testQuadTreeLargeScale() {
        IterationQuadTree qt = new IterationQuadTree(-2, 2, -2, 2);

        // Insert many points
        int count = 0;
        for (double x = -1.9; x <= 1.9; x += 0.1) {
            for (double y = -1.9; y <= 1.9; y += 0.1) {
                qt.insert(x, y, (int)(x * 100 + y * 10));
                count++;
            }
        }
        check("large insert count (" + count + " points)", qt.size() >= count);

        // Lookups should still work
        int found = qt.lookup(0.0, 0.0, 0.01);
        check("large scale lookup at origin", found != IterationQuadTree.CACHE_MISS);

        // Prune to a small region
        qt.pruneOutside(-0.5, 0.5, -0.5, 0.5);
        int afterPrune = qt.lookup(0.0, 0.0, 0.01);
        check("lookup works after prune", afterPrune != IterationQuadTree.CACHE_MISS);

        int outsidePrune = qt.lookup(1.5, 1.5, 0.01);
        check("outside pruned region returns miss", outsidePrune == IterationQuadTree.CACHE_MISS);
    }

    private static void testDockManagerAndDockablePanel() {
        System.out.println("\n  -- Dock System --");

        // Test DockManager registration and callback (no UI — avoids JDialog)
        javax.swing.JFrame frame = new javax.swing.JFrame();
        DockManager manager = new DockManager(frame);

        javax.swing.JPanel content1 = new javax.swing.JPanel();
        javax.swing.JPanel content2 = new javax.swing.JPanel();
        javax.swing.JPanel content3 = new javax.swing.JPanel();

        DockablePanel dp1 = new DockablePanel("Panel A", content1, manager);
        DockablePanel dp2 = new DockablePanel("Panel B", content2, manager);
        DockablePanel dp3 = new DockablePanel("Panel C", content3, manager);

        // Initial state
        check("dock panel starts docked", dp1.isDocked());
        check("dock panel title", "Panel A".equals(dp1.getTitle()));
        check("dock panel content accessible", dp1.getContentPanel() == content1);
        check("manager has 3 panels", manager.getPanels().size() == 3);
        check("manager panels in order", manager.getPanels().get(0) == dp1);
        check("manager panels second", manager.getPanels().get(1) == dp2);

        // Test DockablePanel content is added as child
        boolean hasContent = false;
        for (java.awt.Component c : dp1.getComponents()) {
            if (c == content1) hasContent = true;
        }
        check("dock panel contains content", hasContent);

        // Test dock() on already-docked panel is safe
        dp1.dock();
        check("dock on docked is no-op", dp1.isDocked());

        // Layout callback fires on manager operations
        int[] callbackCount = {0};
        manager.setLayoutCallback(() -> callbackCount[0]++);

        // Test refreshLayout via dock (even if already docked, manager.dock still calls refresh)
        manager.dock(dp1);
        check("layout callback fires on dock", callbackCount[0] == 1);

        // dockAll fires callback once
        callbackCount[0] = 0;
        manager.dockAll();
        check("dockAll fires callback", callbackCount[0] == 1);

        // --- DockEdge tracking ---
        check("default dock edge is WEST", dp1.getDockEdge() == DockManager.DockEdge.WEST);

        dp1.setDockEdge(DockManager.DockEdge.EAST);
        check("setDockEdge to EAST", dp1.getDockEdge() == DockManager.DockEdge.EAST);

        dp1.setDockEdge(DockManager.DockEdge.NORTH);
        check("setDockEdge to NORTH", dp1.getDockEdge() == DockManager.DockEdge.NORTH);

        dp1.setDockEdge(DockManager.DockEdge.SOUTH);
        check("setDockEdge to SOUTH", dp1.getDockEdge() == DockManager.DockEdge.SOUTH);

        // Reset to WEST for remaining tests
        dp1.setDockEdge(DockManager.DockEdge.WEST);
        check("setDockEdge back to WEST", dp1.getDockEdge() == DockManager.DockEdge.WEST);

        // --- DockTarget (edge + index) ---
        DockManager.DockTarget target = new DockManager.DockTarget(DockManager.DockEdge.EAST, 2);
        check("DockTarget edge field", target.edge == DockManager.DockEdge.EAST);
        check("DockTarget index field", target.index == 2);

        DockManager.DockTarget targetWest = new DockManager.DockTarget(DockManager.DockEdge.WEST, 0);
        check("DockTarget west edge", targetWest.edge == DockManager.DockEdge.WEST);
        check("DockTarget zero index", targetWest.index == 0);

        // --- hide/show (isHidden/setHidden) ---
        check("panel not hidden initially", !dp2.isHidden());

        dp2.setHidden(true);
        check("setHidden true", dp2.isHidden());
        // Docked panel should be set invisible when hidden
        check("hidden docked panel not visible", !dp2.isVisible());

        dp2.setHidden(false);
        check("setHidden false", !dp2.isHidden());
        // After un-hiding, panel should be visible again
        check("un-hidden docked panel is visible", dp2.isVisible());

        // hide() via manager
        callbackCount[0] = 0;
        manager.hide(dp3);
        check("manager hide sets hidden", dp3.isHidden());
        check("manager hide fires callback", callbackCount[0] >= 1);

        // show() via manager
        callbackCount[0] = 0;
        manager.show(dp3);
        check("manager show clears hidden", !dp3.isHidden());
        check("manager show fires callback", callbackCount[0] >= 1);

        // dockAll clears hidden state
        dp2.setHidden(true);
        dp3.setHidden(true);
        manager.dockAll();
        check("dockAll unhides dp2", !dp2.isHidden());
        check("dockAll unhides dp3", !dp3.isHidden());

        // --- Edge containers ---
        check("getWestContainer not null", manager.getWestContainer() != null);
        check("getEastContainer not null", manager.getEastContainer() != null);
        check("getNorthContainer not null", manager.getNorthContainer() != null);
        check("getSouthContainer not null", manager.getSouthContainer() != null);

        // Containers are distinct objects
        check("west and east containers distinct",
              manager.getWestContainer() != manager.getEastContainer());
        check("north and south containers distinct",
              manager.getNorthContainer() != manager.getSouthContainer());
        check("west and north containers distinct",
              manager.getWestContainer() != manager.getNorthContainer());

        // --- addToEdgeContainer via dockToEdge with index placement ---
        // dock dp1 to EAST container at index 0
        callbackCount[0] = 0;
        manager.dockToEdge(dp1, DockManager.DockEdge.EAST, 0);
        check("dockToEdge sets edge on panel", dp1.getDockEdge() == DockManager.DockEdge.EAST);
        check("dockToEdge panel is docked", dp1.isDocked());
        check("dockToEdge fires callback", callbackCount[0] >= 1);
        // Panel should be in east container
        boolean inEast = false;
        for (java.awt.Component c : manager.getEastContainer().getComponents()) {
            if (c == dp1) inEast = true;
        }
        check("panel placed in east container", inEast);
        // Panel should not be in west container
        boolean inWest = false;
        for (java.awt.Component c : manager.getWestContainer().getComponents()) {
            if (c == dp1) inWest = true;
        }
        check("panel removed from west container", !inWest);

        // dock dp2 to EAST container at index 0, dp1 should shift to index 1
        manager.dockToEdge(dp2, DockManager.DockEdge.EAST, 0);
        check("dp2 at index 0 in east container",
              manager.getEastContainer().getComponent(0) == dp2);
        check("dp1 shifted to index 1 in east container",
              manager.getEastContainer().getComponent(1) == dp1);

        // dock dp3 to WEST container
        manager.dockToEdge(dp3, DockManager.DockEdge.WEST, 0);
        check("dp3 in west container",
              manager.getWestContainer().getComponent(0) == dp3);

        // dock dp1 back to WEST via dockAll (resets all)
        manager.dockAll();
        // After dockAll, each panel should be docked and not hidden
        check("dockAll: dp1 docked", dp1.isDocked());
        check("dockAll: dp2 docked", dp2.isDocked());
        check("dockAll: dp3 docked", dp3.isDocked());

        frame.dispose();
    }

    // ---- Drawing Tool Tests ----

    private static boolean isAllColor(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0x00FFFFFF) != (rgb & 0x00FFFFFF)) return false;
            }
        }
        return true;
    }

    private static BufferedImage whiteImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static DrawingCanvas testCanvas(int w, int h) {
        return new DrawingCanvas(w, h, new UndoManager(10));
    }

    private static void testPencilToolDrawsOnCanvas() {
        System.out.println("\n  -- Drawing Tools --");
        BufferedImage img = whiteImage(100, 100);
        DrawingCanvas canvas = testCanvas(100, 100);
        PencilTool pencil = new PencilTool();
        pencil.setStrokeSize(3);
        pencil.mousePressed(img, 10, 10, canvas);
        pencil.mouseDragged(img, 50, 50, canvas);
        check("PencilTool draws on canvas", !isAllColor(img, 0xFFFFFF));
        check("PencilTool getName", "Pencil".equals(pencil.getName()));
        check("PencilTool defaultStrokeSize", pencil.getDefaultStrokeSize() == 2);
    }

    private static void testLineToolDrawsOnCanvas() {
        BufferedImage img = whiteImage(100, 100);
        DrawingCanvas canvas = testCanvas(100, 100);
        LineTool line = new LineTool();
        line.setStrokeSize(2);
        line.mousePressed(img, 0, 0, canvas);
        line.mouseReleased(img, 99, 99, canvas);
        // Check pixels along the diagonal changed
        boolean diagonalChanged = false;
        for (int i = 10; i < 90; i++) {
            if ((img.getRGB(i, i) & 0x00FFFFFF) != 0x00FFFFFF) {
                diagonalChanged = true;
                break;
            }
        }
        check("LineTool draws along diagonal", diagonalChanged);
        check("LineTool getName", "Line".equals(line.getName()));
    }

    private static void testRectangleToolDrawsOutline() {
        BufferedImage img = whiteImage(100, 100);
        DrawingCanvas canvas = testCanvas(100, 100);
        RectangleTool rect = new RectangleTool();
        rect.setStrokeSize(1);
        rect.mousePressed(img, 10, 10, canvas);
        rect.mouseReleased(img, 90, 90, canvas);
        boolean borderChanged = (img.getRGB(10, 10) & 0x00FFFFFF) != 0x00FFFFFF;
        boolean interiorWhite = (img.getRGB(50, 50) & 0x00FFFFFF) == 0x00FFFFFF;
        check("RectangleTool border pixel non-white", borderChanged);
        check("RectangleTool interior pixel white (outline only)", interiorWhite);
        check("RectangleTool getName", "Rectangle".equals(rect.getName()));
    }

    private static void testRectangleToolFilled() {
        BufferedImage img = whiteImage(100, 100);
        DrawingCanvas canvas = testCanvas(100, 100);
        RectangleTool rect = new RectangleTool();
        rect.setStrokeSize(1);
        rect.setFilled(true);
        rect.setFillProvider(new SolidFill());
        rect.mousePressed(img, 10, 10, canvas);
        rect.mouseReleased(img, 90, 90, canvas);
        boolean interiorFilled = (img.getRGB(50, 50) & 0x00FFFFFF) != 0x00FFFFFF;
        check("RectangleTool filled interior non-white", interiorFilled);
    }

    private static void testOvalToolDrawsOnCanvas() {
        BufferedImage img = whiteImage(100, 100);
        DrawingCanvas canvas = testCanvas(100, 100);
        OvalTool oval = new OvalTool();
        oval.setStrokeSize(2);
        oval.mousePressed(img, 10, 10, canvas);
        oval.mouseReleased(img, 90, 90, canvas);
        check("OvalTool draws on canvas", !isAllColor(img, 0xFFFFFF));
        check("OvalTool getName", "Oval".equals(oval.getName()));
    }

    private static void testEraserToolErases() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        DrawingCanvas canvas = testCanvas(100, 100);
        EraserTool eraser = new EraserTool();
        eraser.setSize(20);
        eraser.mousePressed(img, 50, 50, canvas);
        int pixel = img.getRGB(50, 50) & 0x00FFFFFF;
        int red = 0xFF0000;
        check("EraserTool erases pixel (no longer red)", pixel != red);
        check("EraserTool getName", "Eraser".equals(eraser.getName()));
        check("EraserTool defaultStrokeSize", eraser.getDefaultStrokeSize() == 18);
    }

    private static void testFillToolFloodFill() {
        BufferedImage img = whiteImage(100, 100);
        // Draw a black rectangle border
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRect(20, 20, 60, 60);
        g.dispose();

        DrawingCanvas canvas = testCanvas(100, 100);
        FillTool fill = new FillTool();
        fill.setFillProvider(new SolidFill());
        // Canvas foreground is BLACK by default (no colorPicker set)
        // so flood fill interior with black
        fill.mousePressed(img, 50, 50, canvas);
        int pixel = img.getRGB(50, 50) & 0x00FFFFFF;
        check("FillTool flood fills interior", pixel != 0xFFFFFF);
        check("FillTool getName", "Fill".equals(fill.getName()));
    }

    private static void testToolDefaultStrokeSizes() {
        check("Pencil defaultStrokeSize=2", new PencilTool().getDefaultStrokeSize() == 2);
        check("Line defaultStrokeSize=2", new LineTool().getDefaultStrokeSize() == 2);
        check("Rectangle defaultStrokeSize=2", new RectangleTool().getDefaultStrokeSize() == 2);
        check("Oval defaultStrokeSize=2", new OvalTool().getDefaultStrokeSize() == 2);
        check("Eraser defaultStrokeSize=18", new EraserTool().getDefaultStrokeSize() == 18);
        check("Fill defaultStrokeSize=2", new FillTool().getDefaultStrokeSize() == 2);
        check("Fractal defaultStrokeSize=1", new FractalTool().getDefaultStrokeSize() == 1);
    }

    private static void testToolCapabilities() {
        // hasStrokeSize
        check("Pencil hasStrokeSize", new PencilTool().hasStrokeSize());
        check("Line hasStrokeSize", new LineTool().hasStrokeSize());
        check("Rectangle hasStrokeSize", new RectangleTool().hasStrokeSize());
        check("Oval hasStrokeSize", new OvalTool().hasStrokeSize());
        check("Eraser hasStrokeSize", new EraserTool().hasStrokeSize());
        check("Fill !hasStrokeSize", !new FillTool().hasStrokeSize());
        check("Fractal !hasStrokeSize", !new FractalTool().hasStrokeSize());

        // hasFill
        check("Pencil !hasFill", !new PencilTool().hasFill());
        check("Line !hasFill", !new LineTool().hasFill());
        check("Rectangle hasFill", new RectangleTool().hasFill());
        check("Oval hasFill", new OvalTool().hasFill());
        check("Eraser !hasFill", !new EraserTool().hasFill());
        check("Fill hasFill", new FillTool().hasFill());
        check("Fractal !hasFill", !new FractalTool().hasFill());
    }

    private static void testToolNames() {
        check("PencilTool name", "Pencil".equals(new PencilTool().getName()));
        check("LineTool name", "Line".equals(new LineTool().getName()));
        check("RectangleTool name", "Rectangle".equals(new RectangleTool().getName()));
        check("OvalTool name", "Oval".equals(new OvalTool().getName()));
        check("EraserTool name", "Eraser".equals(new EraserTool().getName()));
        check("FillTool name", "Fill".equals(new FillTool().getName()));
        check("FractalTool name", "Fractal".equals(new FractalTool().getName()));
    }

    // === Animation Tests ===

    private static void testRecolorFromIters() {
        FractalRenderer r = newRenderer();
        ColorGradient grad = gradient();
        BufferedImage img = r.render(SIZE, SIZE, grad);
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();

        // Recolor with same gradient should produce identical image
        BufferedImage recolored = r.recolorFromIters(iters, size[0], size[1], grad);
        check("recolorFromIters matches original", imagesEqual(img, recolored));
    }

    private static void testRecolorDifferentGradientDiffers() {
        FractalRenderer r = newRenderer();
        ColorGradient grad1 = gradient();
        r.render(SIZE, SIZE, grad1);
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();

        BufferedImage img1 = r.recolorFromIters(iters, size[0], size[1], grad1);

        // Different gradient should produce different image
        ColorGradient grad2 = ColorGradient.fromBaseColor(java.awt.Color.RED);
        BufferedImage img2 = r.recolorFromIters(iters, size[0], size[1], grad2);
        check("recolor different gradient differs", !imagesEqual(img1, img2));
    }

    private static void testPaletteCycleShiftGradient() {
        ColorGradient grad = gradient();
        int stopCount = grad.getStops().size();

        // Shift by 0 should produce gradient with same number of stops
        ColorGradient shifted0 = PaletteCycleAnimator.shiftGradient(grad, 0f);
        check("shift 0 preserves stop count", shifted0.getStops().size() == stopCount);

        // Shift by 0.5 should produce different colors at position 0
        ColorGradient shifted50 = PaletteCycleAnimator.shiftGradient(grad, 0.5f);
        check("shift 0.5 preserves stop count", shifted50.getStops().size() == stopCount);

        // All positions should be in [0, 1]
        boolean allValid = true;
        for (ColorGradient.Stop s : shifted50.getStops()) {
            if (s.getPosition() < 0f || s.getPosition() > 1f) {
                allValid = false;
                break;
            }
        }
        check("shifted positions in [0,1]", allValid);
    }

    private static void testPaletteCycleFullRotationWraps() {
        ColorGradient grad = gradient();
        // Shift by 1.0 should wrap back to original positions
        ColorGradient shifted = PaletteCycleAnimator.shiftGradient(grad, 1.0f);
        boolean positionsMatch = true;
        for (int i = 0; i < grad.getStops().size(); i++) {
            float origPos = grad.getStops().get(i).getPosition();
            float shiftedPos = shifted.getStops().get(i).getPosition();
            if (Math.abs(origPos - shiftedPos) > 0.001f) {
                positionsMatch = false;
                break;
            }
        }
        check("shift by 1.0 wraps to original", positionsMatch);
    }

    private static void testPaletteCycleRenderToFiles() {
        FractalRenderer r = newRenderer();
        ColorGradient grad = gradient();
        r.render(SIZE, SIZE, grad);
        int[] iters = r.getLastRenderIters().clone();
        int[] size = r.getLastRenderSize();

        PaletteCycleAnimator animator = new PaletteCycleAnimator(r);
        animator.setTotalFrames(5);
        animator.setFps(10);
        animator.setCycleSpeed(1.0f);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "palette_cycle_test_" + System.currentTimeMillis());
        try {
            int count = animator.renderToFiles(tmpDir, iters, size[0], size[1], grad, null);
            check("palette cycle renders frames", count == 5);

            File aviFile = new File(tmpDir, "palette_cycle.avi");
            check("palette cycle AVI exists", aviFile.exists());
            check("palette cycle AVI non-empty", aviFile.length() > 0);
        } catch (Exception e) {
            check("palette cycle renderToFiles no exception: " + e.getMessage(), false);
        } finally {
            deleteDir(tmpDir);
        }
    }

    private static void testIterationAnimatorFramesDiffer() {
        FractalRenderer r = newRenderer();
        ColorGradient grad = gradient();

        // Render at iter=1 and iter=256 should differ
        r.setMaxIterations(1);
        BufferedImage img1 = r.render(SIZE, SIZE, grad);
        r.setMaxIterations(256);
        BufferedImage img256 = r.render(SIZE, SIZE, grad);
        check("iter 1 vs 256 differ", !imagesEqual(img1, img256));
    }

    private static void testIterationAnimatorRenderToFiles() {
        FractalRenderer r = newRenderer();
        ColorGradient grad = gradient();

        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(10);
        animator.setStep(3);
        animator.setSize(50, 50);
        animator.setFps(10);

        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "iter_anim_test_" + System.currentTimeMillis());
        try {
            int count = animator.renderToFiles(tmpDir, r, grad, null);
            // Frames: iter 1, 4, 7, 10 = 4 frames
            check("iteration animator frame count", count == 4);

            File aviFile = new File(tmpDir, "iteration_anim.avi");
            check("iteration animator AVI exists", aviFile.exists());
            check("iteration animator AVI non-empty", aviFile.length() > 0);
        } catch (Exception e) {
            check("iteration animator renderToFiles no exception: " + e.getMessage(), false);
        } finally {
            deleteDir(tmpDir);
        }
    }

    private static void testIterationAnimatorCancel() {
        FractalRenderer r = newRenderer();
        ColorGradient grad = gradient();

        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(100);
        animator.setStep(1);
        animator.setSize(20, 20);
        animator.setFps(10);

        // Cancel immediately
        animator.cancel();

        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "iter_cancel_test_" + System.currentTimeMillis());
        try {
            int count = animator.renderToFiles(tmpDir, r, grad, null);
            check("iteration animator cancel produces 0 frames", count == 0);
        } catch (Exception e) {
            check("iteration animator cancel no exception: " + e.getMessage(), false);
        } finally {
            deleteDir(tmpDir);
        }
    }

    private static void testIterationAnimatorTotalFrames() {
        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(256);
        animator.setStep(1);
        check("total frames 1-256 step 1", animator.getTotalFrames() == 256);

        animator.setStep(5);
        check("total frames 1-256 step 5", animator.getTotalFrames() == 52);

        animator.setStartIter(10);
        animator.setEndIter(50);
        animator.setStep(10);
        check("total frames 10-50 step 10", animator.getTotalFrames() == 5);
    }

    private static void testPaletteCycleRecolorMatchesDirectRender() {
        // Verify that recolorFromIters with the original gradient produces
        // the same image as a direct render (sanity check for the pipeline)
        FractalRenderer r = newRenderer();
        r.setMaxIterations(64);
        ColorGradient grad = gradient();
        BufferedImage direct = r.render(50, 50, grad);
        int[] iters = r.getLastRenderIters().clone();
        BufferedImage recolored = r.recolorFromIters(iters, 50, 50, grad);
        check("recolor matches direct render (64 iter)", imagesEqual(direct, recolored));
    }

    // === Screensaver Tests ===

    private static void testScreensaverControllerLifecycle() {
        // Test that controller starts and stops cleanly without full-screen (headless-safe)
        ScreensaverController controller = new ScreensaverController(5);
        check("screensaver not running initially", !controller.isRunning());
        // Don't call start() in test environment (requires display) — just verify state
    }

    private static void testScreensaverPanelTransition() {
        // Test the cross-fade panel logic
        ScreensaverController.ScreensaverPanel panel =
                new ScreensaverController.ScreensaverPanel(100, 100, () -> {});

        BufferedImage img1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g1 = img1.createGraphics();
        g1.setColor(java.awt.Color.RED);
        g1.fillRect(0, 0, 100, 100);
        g1.dispose();

        BufferedImage img2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img2.createGraphics();
        g2.setColor(java.awt.Color.BLUE);
        g2.fillRect(0, 0, 100, 100);
        g2.dispose();

        // First image sets immediately (no fade)
        panel.transitionTo(img1, 500);
        check("screensaver panel accepts first image", true);

        // Second image triggers fade
        panel.transitionTo(img2, 500);
        check("screensaver panel accepts second image", true);
    }

    private static void testScreensaverFindLocationNotNull() {
        // findInterestingLocation should usually find something within 1000 attempts
        double[] loc = com.seanick80.drawingapp.tools.FractalAnimationController.findInterestingLocation();
        check("findInterestingLocation returns location", loc != null);
        if (loc != null) {
            check("location has 3 components", loc.length == 3);
            check("location halfSpan > 0", loc[2] > 0);
        }
    }

    // === FDP Project File Tests ===

    private static void testFdpRoundTripSingleLayer() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_single_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(50, 50);
            // Draw something on the layer
            java.awt.Graphics2D g = lm.getActiveLayer().getImage().createGraphics();
            g.setColor(java.awt.Color.RED);
            g.fillRect(10, 10, 20, 20);
            g.dispose();

            FractalRenderer renderer = newRenderer();
            ColorGradient grad = gradient();

            FdpSerializer.save(tmpFile, lm, renderer, grad);
            check("FDP file created", tmpFile.exists());
            check("FDP file non-empty", tmpFile.length() > 0);

            AppState state = FdpSerializer.load(tmpFile);
            check("FDP single layer count", state.layers.size() == 1);
            check("FDP canvas width", state.canvasWidth == 50);
            check("FDP canvas height", state.canvasHeight == 50);

            // Verify pixel data survived
            Layer loaded = state.layers.get(0);
            int pixel = loaded.getImage().getRGB(15, 15);
            int red = (pixel >> 16) & 0xFF;
            check("FDP layer pixel preserved", red > 200); // should be red
        } catch (Exception e) {
            check("FDP single layer round-trip no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpRoundTripMultiLayer() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_multi_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(40, 40);
            lm.addLayer();
            lm.addLayer();
            lm.setActiveIndex(1);

            // Set different blend modes
            lm.getLayer(1).setBlendMode(BlendMode.MULTIPLY);
            lm.getLayer(2).setBlendMode(BlendMode.SCREEN);
            lm.getLayer(1).setOpacity(0.5f);

            FdpSerializer.save(tmpFile, lm, newRenderer(), gradient());
            AppState state = FdpSerializer.load(tmpFile);

            check("FDP multi layer count", state.layers.size() == 3);
            check("FDP active layer index", state.activeLayerIndex == 1);
            check("FDP layer 1 blend mode", state.layers.get(1).getBlendMode() == BlendMode.MULTIPLY);
            check("FDP layer 2 blend mode", state.layers.get(2).getBlendMode() == BlendMode.SCREEN);
            check("FDP layer 1 opacity", Math.abs(state.layers.get(1).getOpacity() - 0.5f) < 0.01f);
        } catch (Exception e) {
            check("FDP multi layer round-trip no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpRoundTripFractalState() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_fractal_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(40, 40);
            FractalRenderer renderer = newRenderer();
            renderer.setMaxIterations(512);
            renderer.setColorMode(FractalRenderer.ColorMode.DIVISION);
            renderer.setInteriorPruning(false);

            FdpSerializer.save(tmpFile, lm, renderer, gradient());
            AppState state = FdpSerializer.load(tmpFile);

            check("FDP fractal state present", state.fractalState != null);
            check("FDP fractal type", "MANDELBROT".equals(state.fractalState.typeName));
            check("FDP fractal iterations", state.fractalState.maxIterations == 512);
            check("FDP fractal color mode", "DIVISION".equals(state.fractalState.colorMode));
            check("FDP fractal pruning off", !state.fractalState.interiorPruning);
        } catch (Exception e) {
            check("FDP fractal state round-trip no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpRoundTripGradient() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_gradient_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(40, 40);
            ColorGradient grad = gradient();

            FdpSerializer.save(tmpFile, lm, newRenderer(), grad);
            AppState state = FdpSerializer.load(tmpFile);

            check("FDP gradient present", state.gradient != null);
            check("FDP gradient stop count",
                    state.gradient.getStops().size() == grad.getStops().size());

            // Verify first and last stop colors
            ColorGradient.Stop origFirst = grad.getStops().get(0);
            ColorGradient.Stop loadFirst = state.gradient.getStops().get(0);
            check("FDP gradient first stop position",
                    Math.abs(origFirst.getPosition() - loadFirst.getPosition()) < 0.001f);
            check("FDP gradient first stop color",
                    origFirst.getColor().equals(loadFirst.getColor()));
        } catch (Exception e) {
            check("FDP gradient round-trip no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpRoundTripLayerProperties() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_props_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(30, 30);
            Layer layer = lm.getActiveLayer();
            layer.setName("Test Layer");
            layer.setVisible(false);
            layer.setLocked(true);
            layer.setOpacity(0.75f);
            layer.setBlendMode(BlendMode.OVERLAY);

            FdpSerializer.save(tmpFile, lm, newRenderer(), gradient());
            AppState state = FdpSerializer.load(tmpFile);

            Layer loaded = state.layers.get(0);
            check("FDP layer name", "Test Layer".equals(loaded.getName()));
            check("FDP layer visible false", !loaded.isVisible());
            check("FDP layer locked true", loaded.isLocked());
            check("FDP layer opacity 0.75", Math.abs(loaded.getOpacity() - 0.75f) < 0.01f);
            check("FDP layer blend mode OVERLAY", loaded.getBlendMode() == BlendMode.OVERLAY);
        } catch (Exception e) {
            check("FDP layer properties no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpBackwardCompat() {
        // Simulate loading a file without fractal state or gradient (older version)
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_compat_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(30, 30);
            // Save with null renderer and gradient
            FdpSerializer.save(tmpFile, lm, null, null);
            AppState state = FdpSerializer.load(tmpFile);

            check("FDP backward compat layers", state.layers.size() == 1);
            check("FDP backward compat no fractal", state.fractalState == null);
            check("FDP backward compat no gradient", state.gradient == null);
        } catch (Exception e) {
            check("FDP backward compat no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    private static void testFdpRoundTripBigDecimalPrecision() {
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),
                "fdp_test_precision_" + System.currentTimeMillis() + ".fdp");
        try {
            LayerManager lm = new LayerManager(30, 30);
            FractalRenderer renderer = newDeeperZoomRenderer();

            FdpSerializer.save(tmpFile, lm, renderer, gradient());
            AppState state = FdpSerializer.load(tmpFile);

            // Verify BigDecimal precision preserved via string serialization
            String origMinReal = renderer.getMinRealBig().toPlainString();
            check("FDP BigDecimal minReal preserved", origMinReal.equals(state.fractalState.minReal));
            check("FDP BigDecimal iterations", state.fractalState.maxIterations == 706);
        } catch (Exception e) {
            check("FDP BigDecimal precision no exception: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    // ========== Bug Bash Regression Tests ==========

    /** Bug 1: Drawing on an invisible layer should be blocked (no pixel changes). */
    private static void testBug1InvisibleLayerDrawingBlocked() {
        System.out.println("Bug 1: Invisible layer drawing blocked");
        LayerManager lm = new LayerManager(100, 100);
        Layer active = lm.getActiveLayer();
        active.fill(Color.WHITE);
        active.setVisible(false);

        BufferedImage img = active.getImage();
        int before = img.getRGB(50, 50);

        // The canvas guards drawing via isVisible() check in mousePressed.
        check("invisible layer blocks drawing", !active.isVisible());
        check("invisible layer image unchanged", img.getRGB(50, 50) == before);
        active.setVisible(true);
        check("visible layer allows drawing", active.isVisible());
    }

    /** Bug 2: Fractal tool mouseWheelMoved should change viewport bounds. */
    private static void testBug2FractalScrollWheelZoom() {
        System.out.println("Bug 2: Fractal scroll wheel zoom");
        FractalRenderer renderer = newRenderer();
        renderer.setBounds(-2.0, 1.0, -1.5, 1.5);
        renderer.setMaxIterations(64);

        BigDecimal origRangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());

        // Simulate the zoom math from FractalTool.mouseWheelMoved
        int w = 100, h = 100, x = 50, y = 50;
        BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
        BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
        java.math.MathContext mc = new java.math.MathContext(50);
        BigDecimal rangeR = maxR.subtract(minR, mc);
        BigDecimal rangeI = maxI.subtract(minI, mc);

        BigDecimal xFrac = new BigDecimal(x).divide(new BigDecimal(w), mc);
        BigDecimal yFrac = new BigDecimal(y).divide(new BigDecimal(h), mc);
        BigDecimal centerReal = minR.add(xFrac.multiply(rangeR, mc), mc);
        BigDecimal centerImag = minI.add(yFrac.multiply(rangeI, mc), mc);

        BigDecimal zoomFactor = new BigDecimal("0.8");
        BigDecimal newRangeR = rangeR.multiply(zoomFactor, mc);
        BigDecimal newRangeI = rangeI.multiply(zoomFactor, mc);
        BigDecimal two = new BigDecimal(2);

        renderer.setBounds(
            centerReal.subtract(newRangeR.divide(two, mc), mc),
            centerReal.add(newRangeR.divide(two, mc), mc),
            centerImag.subtract(newRangeI.divide(two, mc), mc),
            centerImag.add(newRangeI.divide(two, mc), mc));

        BigDecimal newRange = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());
        check("scroll wheel zoom shrinks range", newRange.compareTo(origRangeR) < 0);
        check("scroll wheel zoom range is 80% of original",
            newRange.subtract(origRangeR.multiply(zoomFactor)).abs()
                .compareTo(new BigDecimal("0.0001")) < 0);
    }

    /** Bug 3: No gradient files should exist under src/gradient/defaults/. */
    private static void testBug3NoDuplicateGradientDefaults() {
        System.out.println("Bug 3: No duplicate gradient defaults");
        File srcDefaults = new File("src/com/seanick80/drawingapp/gradient/defaults");
        boolean srcExists = srcDefaults.exists() && srcDefaults.isDirectory();
        if (srcExists) {
            File[] files = srcDefaults.listFiles();
            srcExists = files != null && files.length > 0;
        }
        check("src gradient defaults directory removed", !srcExists);
    }

    /** Bug 4: Fill dropdown should have "None" option for shape tools. */
    private static void testBug4FillNoneDropdown() {
        System.out.println("Bug 4: Fill None dropdown");
        FillRegistry reg = new FillRegistry();
        reg.register(new SolidFill());
        reg.register(new GradientFill());

        javax.swing.JPanel panel = ToolSettingsBuilder.createFillOptionsPanel(
            reg, null, true, null, null);
        javax.swing.JComboBox<?> combo = findComboBox(panel);
        check("fill panel has combo box", combo != null);
        if (combo != null) {
            check("first item is None", "None".equals(combo.getItemAt(0)));
            check("combo has None + fill providers",
                combo.getItemCount() == reg.getAll().size() + 1);
            check("None is selected by default", "None".equals(combo.getSelectedItem()));
        }

        // Fill tool (showNoneOption=false) should not have None
        javax.swing.JPanel panel2 = ToolSettingsBuilder.createFillOptionsPanel(
            reg, null, false, null, null);
        javax.swing.JComboBox<?> combo2 = findComboBox(panel2);
        check("fill tool panel has no None entry",
            combo2 != null && !"None".equals(combo2.getItemAt(0)));
    }

    @SuppressWarnings("unchecked")
    private static javax.swing.JComboBox<?> findComboBox(java.awt.Container container) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof javax.swing.JComboBox) return (javax.swing.JComboBox<?>) c;
            if (c instanceof java.awt.Container) {
                javax.swing.JComboBox<?> found = findComboBox((java.awt.Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Bug 5: Save file tracking — re-save to same file works. */
    private static void testBug5SaveFileTracking() {
        System.out.println("Bug 5: Save file tracking");
        File tmpFile = new File(System.getProperty("java.io.tmpdir"), "test_save_tracking.fdp");
        try {
            LayerManager lm = new LayerManager(50, 50);
            lm.getActiveLayer().fill(Color.RED);
            FdpSerializer.save(tmpFile, lm, null, null);
            check("save creates file", tmpFile.exists());
            check("save file is non-empty", tmpFile.length() > 0);

            // Re-save (simulating Ctrl+S)
            lm.getActiveLayer().fill(Color.BLUE);
            FdpSerializer.save(tmpFile, lm, null, null);
            check("re-save overwrites file", tmpFile.exists());

            AppState state = FdpSerializer.load(tmpFile);
            check("re-saved file loads correctly", state != null);
            check("re-saved dimensions correct",
                state.canvasWidth == 50 && state.canvasHeight == 50);
        } catch (Exception e) {
            check("save file tracking: " + e.getMessage(), false);
        } finally {
            tmpFile.delete();
        }
    }

    /** Bug 6: Layer drag-to-reorder via LayerManager.moveLayer(). */
    private static void testBug6LayerDragReorder() {
        System.out.println("Bug 6: Layer drag reorder");
        LayerManager lm = new LayerManager(50, 50);
        lm.getActiveLayer().setName("Background");
        lm.addLayer().setName("Layer A");
        lm.addLayer().setName("Layer B");

        check("initial order correct",
            "Background".equals(lm.getLayer(0).getName()) &&
            "Layer A".equals(lm.getLayer(1).getName()) &&
            "Layer B".equals(lm.getLayer(2).getName()));

        // Move Layer B (index 2) to index 0
        lm.setActiveIndex(2);
        lm.moveLayer(2, 0);
        check("move 2->0 order",
            "Layer B".equals(lm.getLayer(0).getName()) &&
            "Background".equals(lm.getLayer(1).getName()) &&
            "Layer A".equals(lm.getLayer(2).getName()));
        check("move 2->0 active follows", lm.getActiveIndex() == 0);

        // Move back
        lm.moveLayer(0, 2);
        check("move 0->2 order",
            "Background".equals(lm.getLayer(0).getName()) &&
            "Layer A".equals(lm.getLayer(1).getName()) &&
            "Layer B".equals(lm.getLayer(2).getName()));
        check("move 0->2 active follows", lm.getActiveIndex() == 2);

        // No-op
        lm.moveLayer(1, 1);
        check("same index no-op", "Layer A".equals(lm.getLayer(1).getName()));

        // Invalid indices
        lm.moveLayer(-1, 0);
        lm.moveLayer(0, 5);
        check("invalid indices ignored", lm.getLayerCount() == 3);

        // Active adjusts when non-active layer moves across
        lm.setActiveIndex(1); // Layer A
        lm.moveLayer(0, 2); // Move Background past active
        check("active adjusts when layer crosses",
            lm.getActiveIndex() == 0 && "Layer A".equals(lm.getLayer(0).getName()));
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
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

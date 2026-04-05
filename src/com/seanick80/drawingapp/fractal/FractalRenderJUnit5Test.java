package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.gradient.ColorGradient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;

class FractalRenderJUnit5Test {

    private static final int SIZE = TestHelpers.DEFAULT_SIZE;

    @Test @LargeTest @Timeout(10)
    void doubleRenderDeterministic() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img1 = r.render(SIZE, SIZE, TestHelpers.gradient());
        r.getCache().clear();
        BufferedImage img2 = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertTrue(TestHelpers.imagesEqual(img1, img2), "Double render should be deterministic");
    }

    @Test @LargeTest @Timeout(10)
    void bigDecimalMatchesPerturbation() {
        FractalRenderer r = TestHelpers.newDeepZoomRenderer();
        ColorGradient g = TestHelpers.gradient();

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, g);

        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.getCache().clear();
        BufferedImage ptImg = r.render(SIZE, SIZE, g);

        int[] bdPixels = TestHelpers.getPixels(bdImg);
        int[] ptPixels = TestHelpers.getPixels(ptImg);

        int closeMatch = 0;
        for (int i = 0; i < bdPixels.length; i++) {
            if (bdPixels[i] == ptPixels[i] || TestHelpers.colorDistance(bdPixels[i], ptPixels[i]) < 80) {
                closeMatch++;
            }
        }
        double closePct = 100.0 * closeMatch / bdPixels.length;
        assertTrue(closePct > 70.0, "Perturbation should structurally match BigDecimal (got " + String.format("%.1f%%", closePct) + ")");

        int ptBlack = TestHelpers.countColor(ptImg, Color.BLACK.getRGB());
        assertTrue(ptBlack < SIZE * SIZE * 0.95 && TestHelpers.countUniqueColors(ptImg) > 10,
            "Perturbation should produce non-trivial image");

        int bdColors = TestHelpers.countUniqueColors(bdImg);
        int ptColors = TestHelpers.countUniqueColors(ptImg);
        double colorRatio = (double) Math.min(bdColors, ptColors) / Math.max(bdColors, ptColors);
        assertTrue(colorRatio > 0.5, "Should have similar color diversity");
    }

    @Test @LargeTest @Timeout(30)
    void deeperZoomAllModes() {
        FractalRenderer r = TestHelpers.newDeeperZoomRenderer();
        for (FractalRenderer.RenderMode mode : FractalRenderer.RenderMode.values()) {
            r.setRenderMode(mode);
            r.getCache().clear();
            BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
            assertNotNull(img, "deeper zoom " + mode + " should not return null");
            assertTrue(TestHelpers.countUniqueColors(img) > 1, "deeper zoom " + mode + " should have >1 color");
        }
    }

    @Test @LargeTest @Timeout(10)
    void perturbationInteriorPixels() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(
            new BigDecimal("-0.562333343441602469224560589876423039"),
            new BigDecimal("-0.562333343441601581046140889751190703"),
            new BigDecimal("-0.646896540537624584973152168988217449"),
            new BigDecimal("-0.646896540537623696794732468862985113")
        );
        r.setMaxIterations(256);

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, TestHelpers.gradient());

        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.getCache().clear();
        BufferedImage ptImg = r.render(SIZE, SIZE, TestHelpers.gradient());

        int[] bdPx = TestHelpers.getPixels(bdImg);
        int[] ptPx = TestHelpers.getPixels(ptImg);
        int black = Color.BLACK.getRGB();
        int falseEscape = 0;
        for (int i = 0; i < bdPx.length; i++) {
            if (bdPx[i] == black && ptPx[i] != black) falseEscape++;
        }
        assertEquals(0, falseEscape, "Perturbation should have 0 false escapes");
    }

    @Test @LargeTest @Timeout(10)
    void doubleProducesBlockyAtDeepZoom() {
        FractalRenderer r = TestHelpers.newDeepZoomRenderer();
        ColorGradient g = TestHelpers.gradient();

        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage bdImg = r.render(SIZE, SIZE, g);

        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage dblImg = r.render(SIZE, SIZE, g);

        int[] bdPixels = TestHelpers.getPixels(bdImg);
        int[] dblPixels = TestHelpers.getPixels(dblImg);
        int diffCount = 0;
        for (int i = 0; i < bdPixels.length; i++) {
            if (bdPixels[i] != dblPixels[i]) diffCount++;
        }
        double diffPct = 100.0 * diffCount / bdPixels.length;
        assertTrue(diffPct > 5.0, "Double should differ from BigDecimal at deep zoom");
    }

    @Test @LargeTest @Timeout(10)
    void doubleModeShallowZoom() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());

        assertTrue(TestHelpers.countUniqueColors(img) > 20, "Should produce rich image");
        int blackCount = TestHelpers.countColor(img, Color.BLACK.getRGB());
        assertTrue(blackCount > 0 && blackCount < SIZE * SIZE, "Should have interior pixels");
    }

    @Test @LargeTest @Timeout(10)
    void juliaSetRenders() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setType(FractalType.JULIA);
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setJuliaConstant(-0.7, 0.27015);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());

        assertTrue(TestHelpers.countUniqueColors(img) > 15);
        int blackCount = TestHelpers.countColor(img, Color.BLACK.getRGB());
        assertTrue(blackCount > 0 && blackCount < SIZE * SIZE);
    }

    @Test @LargeTest @Timeout(10)
    void iterationCountsPreservedAcrossZoom() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-0.75, -0.74, 0.10, 0.11);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);

        BufferedImage img1 = r.render(SIZE, SIZE, TestHelpers.gradient());
        r.setBounds(-0.748, -0.742, 0.102, 0.108);
        r.render(SIZE, SIZE, TestHelpers.gradient());
        r.setBounds(-0.75, -0.74, 0.10, 0.11);
        BufferedImage img2 = r.render(SIZE, SIZE, TestHelpers.gradient());

        assertTrue(TestHelpers.imagesEqual(img1, img2), "Iteration counts should be stable across zoom cycles");
    }

    @Test @LargeTest @Timeout(10)
    void renderModeSwitch() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(128);
        for (FractalRenderer.RenderMode mode : FractalRenderer.RenderMode.values()) {
            r.setRenderMode(mode);
            r.getCache().clear();
            BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
            assertTrue(TestHelpers.countUniqueColors(img) > 10, mode + " should produce valid image");
        }
    }

    @Test @LargeTest @Timeout(10)
    void mandelbrotDoubleGolden() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 1.0, -1.5, 1.5);
        r.setMaxIterations(256);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertEquals(1682464L, TestHelpers.pixelChecksum(img));
    }

    @Test @LargeTest @Timeout(10)
    void juliaDoubleGolden() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setType(FractalType.JULIA);
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        r.setJuliaConstant(-0.7, 0.27015);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertEquals(1570868L, TestHelpers.pixelChecksum(img));
    }

    @Test @LargeTest @Timeout(10)
    void mandelbrotPerturbationGolden() {
        FractalRenderer r = TestHelpers.newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        r.setPixelGuessing(false);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertEquals(3536663L, TestHelpers.pixelChecksum(img));
    }

    @Test @LargeTest @Timeout(10)
    void mandelbrotBigDecimalGolden() {
        FractalRenderer r = TestHelpers.newDeepZoomRenderer();
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        r.getCache().clear();
        BufferedImage img = r.render(SIZE, SIZE, TestHelpers.gradient());
        assertEquals(3536663L, TestHelpers.pixelChecksum(img));
    }

    @Test @LargeTest @Timeout(10)
    void sevenPointedStarDeepZoom() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(
            new BigDecimal("-0.680148757332831493578851222991943360"),
            new BigDecimal("-0.680148757332831121049821376800537110"),
            new BigDecimal("-0.472196542546057414295193929152650857"),
            new BigDecimal("-0.472196542546057041766164082961244607")
        );
        r.setMaxIterations(506);
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        BufferedImage img = r.render(50, 50, TestHelpers.gradient());
        assertTrue(TestHelpers.countUniqueColors(img) >= 3);
        assertTrue(r.getType() instanceof MandelbrotType);
    }

    @Test @LargeTest @Timeout(30)
    void partialRenderBug() {
        final int W = 100, H = 75;
        final int TOTAL = W * H;
        final int BLACK = Color.BLACK.getRGB();

        BigDecimal minR = new BigDecimal("-0.659657804128263403164649202540923498620");
        BigDecimal maxR = new BigDecimal("-0.659657804128263397293292745606340428924");
        BigDecimal minI = new BigDecimal("-0.450547498432446486714476507308609934980");
        BigDecimal maxI = new BigDecimal("-0.450547498432446480843120050374026865284");
        int maxIter = 706;

        // Ground truth: pure BigDecimal, no pruning
        FractalRenderer rBD = new FractalRenderer();
        rBD.setType(FractalType.MANDELBROT);
        rBD.setBounds(minR, maxR, minI, maxI);
        rBD.setMaxIterations(maxIter);
        rBD.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rBD.setInteriorPruning(false);
        BufferedImage imgBD = rBD.render(W, H, TestHelpers.gradient());
        int[] pxBD = TestHelpers.getPixels(imgBD);

        int blackBD = 0;
        for (int p : pxBD) { if (p == BLACK) blackBD++; }
        assertTrue(100.0 * blackBD / TOTAL < 50.0, "BIGDECIMAL should have <50% black");

        // BigDecimal with pruning
        FractalRenderer rBDPrune = new FractalRenderer();
        rBDPrune.setType(FractalType.MANDELBROT);
        rBDPrune.setBounds(minR, maxR, minI, maxI);
        rBDPrune.setMaxIterations(maxIter);
        rBDPrune.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rBDPrune.setInteriorPruning(true);
        BufferedImage imgBDPrune = rBDPrune.render(W, H, TestHelpers.gradient());
        int[] pxBDPrune = TestHelpers.getPixels(imgBDPrune);

        int pruningDiff = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (pxBD[i] != pxBDPrune[i]) pruningDiff++;
        }
        assertEquals(0, pruningDiff, "BIGDECIMAL pruning should match no-pruning");

        // Perturbation
        FractalRenderer rPT = new FractalRenderer();
        rPT.setType(FractalType.MANDELBROT);
        rPT.setBounds(minR, maxR, minI, maxI);
        rPT.setMaxIterations(maxIter);
        rPT.setRenderMode(FractalRenderer.RenderMode.PERTURBATION);
        rPT.setInteriorPruning(true);
        BufferedImage imgPT = rPT.render(W, H, TestHelpers.gradient());
        int[] pxPT = TestHelpers.getPixels(imgPT);

        int blackPT = 0;
        for (int p : pxPT) { if (p == BLACK) blackPT++; }
        assertTrue(100.0 * blackPT / TOTAL < 50.0, "PERTURBATION should have <50% black");

        int falseBlackPT = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (pxBD[i] != BLACK && pxPT[i] == BLACK) falseBlackPT++;
        }
        assertTrue(falseBlackPT < TOTAL * 0.10, "PERTURBATION false-black < 10%");
    }

    @Test @LargeTest @Timeout(10)
    void prevRenderCacheAtShallowZoom() {
        int W = 100, H = 100;
        ColorGradient g = TestHelpers.gradient();

        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        r.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        r.setMaxIterations(256);
        BufferedImage img1 = r.render(W, H, g);
        assertTrue(TestHelpers.countUniqueColors(img1) > 10);

        r.setBounds(-1.0, 1.0, -1.0, 1.0);
        BufferedImage img2 = r.render(W, H, g);
        assertTrue(TestHelpers.countUniqueColors(img2) > 10);

        FractalRenderer rFresh = new FractalRenderer();
        rFresh.setType(FractalType.MANDELBROT);
        rFresh.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rFresh.setBounds(-1.0, 1.0, -1.0, 1.0);
        rFresh.setMaxIterations(256);
        assertTrue(TestHelpers.imagesEqual(img2, rFresh.render(W, H, g)), "Cached render should match fresh");

        double scaleX = 2.0 / (W - 1);
        double panDist = scaleX * 10;
        r.setBounds(-1.0 + panDist, 1.0 + panDist, -1.0, 1.0);
        BufferedImage img3 = r.render(W, H, g);

        var cache = r.getCache();
        double panHitPct = cache.getLookups() > 0 ? 100.0 * cache.getHits() / cache.getLookups() : 0;
        assertTrue(panHitPct > 30.0, "Shallow pan should have >30% cache hits");

        FractalRenderer rPanFresh = new FractalRenderer();
        rPanFresh.setType(FractalType.MANDELBROT);
        rPanFresh.setRenderMode(FractalRenderer.RenderMode.DOUBLE);
        rPanFresh.setBounds(-1.0 + panDist, 1.0 + panDist, -1.0, 1.0);
        rPanFresh.setMaxIterations(256);
        assertTrue(TestHelpers.imagesEqual(img3, rPanFresh.render(W, H, g)), "Pan cached should match fresh");
    }

    @Test @LargeTest @Timeout(30)
    void prevRenderCacheAtDeepZoom() {
        int W = 50, H = 50;
        ColorGradient g = TestHelpers.gradient();
        MathContext mc = new MathContext(35, java.math.RoundingMode.HALF_UP);

        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        r.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);

        BigDecimal minR1 = new BigDecimal("-0.6596578041282634");
        BigDecimal maxR1 = new BigDecimal("-0.6596578041282433");
        BigDecimal minI1 = new BigDecimal("-0.4505474984324465");
        BigDecimal maxI1 = new BigDecimal("-0.4505474984324264");
        r.setBounds(minR1, maxR1, minI1, maxI1);
        r.setMaxIterations(256);

        r.render(W, H, g);
        assertEquals(0, r.getPrevRenderCacheHits());

        BigDecimal rangeR = maxR1.subtract(minR1);
        BigDecimal rangeI = maxI1.subtract(minI1);
        BigDecimal quarter = new BigDecimal("0.25");
        BigDecimal minR2 = minR1.add(rangeR.multiply(quarter));
        BigDecimal maxR2 = maxR1.subtract(rangeR.multiply(quarter));
        BigDecimal minI2 = minI1.add(rangeI.multiply(quarter));
        BigDecimal maxI2 = maxI1.subtract(rangeI.multiply(quarter));
        r.setBounds(minR2, maxR2, minI2, maxI2);

        BufferedImage img2 = r.render(W, H, g);
        double hitPct = 100.0 * r.getPrevRenderCacheHits() / (W * H);
        assertTrue(hitPct > 20.0, "2x zoom should produce >20% cache hits");

        FractalRenderer rFresh = new FractalRenderer();
        rFresh.setType(FractalType.MANDELBROT);
        rFresh.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rFresh.setBounds(minR2, maxR2, minI2, maxI2);
        rFresh.setMaxIterations(256);
        assertTrue(TestHelpers.imagesEqual(img2, rFresh.render(W, H, g)), "Cached should match fresh");

        BigDecimal panScaleX = maxR2.subtract(minR2).divide(BigDecimal.valueOf(W - 1), mc);
        BigDecimal panShift = panScaleX.multiply(BigDecimal.valueOf(12));
        r.setBounds(minR2.add(panShift), maxR2.add(panShift), minI2, maxI2);

        BufferedImage img3 = r.render(W, H, g);
        double hitPctPan = 100.0 * r.getPrevRenderCacheHits() / (W * H);
        assertTrue(hitPctPan > 60.0, "Pan should produce >60% cache hits");

        FractalRenderer rPanFresh = new FractalRenderer();
        rPanFresh.setType(FractalType.MANDELBROT);
        rPanFresh.setRenderMode(FractalRenderer.RenderMode.BIGDECIMAL);
        rPanFresh.setBounds(minR2.add(panShift), maxR2.add(panShift), minI2, maxI2);
        rPanFresh.setMaxIterations(256);
        assertTrue(TestHelpers.imagesEqual(img3, rPanFresh.render(W, H, g)), "Pan cached should match fresh");
    }

    @Test @LargeTest @Timeout(10)
    void bug2FractalScrollWheelZoom() {
        FractalRenderer renderer = TestHelpers.newRenderer();
        renderer.setBounds(-2.0, 1.0, -1.5, 1.5);
        renderer.setMaxIterations(64);

        BigDecimal origRangeR = renderer.getMaxRealBig().subtract(renderer.getMinRealBig());

        int w = 100, h = 100, x = 50, y = 50;
        MathContext mc = new MathContext(50);
        BigDecimal minR = renderer.getMinRealBig(), maxR = renderer.getMaxRealBig();
        BigDecimal minI = renderer.getMinImagBig(), maxI = renderer.getMaxImagBig();
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
        assertTrue(newRange.compareTo(origRangeR) < 0, "Zoom should shrink range");
        assertTrue(newRange.subtract(origRangeR.multiply(zoomFactor)).abs()
            .compareTo(new BigDecimal("0.0001")) < 0, "Range should be 80% of original");
    }
}

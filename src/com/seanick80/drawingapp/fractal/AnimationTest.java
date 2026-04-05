package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.gradient.ColorGradient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

class AnimationTest {

    private static final int SIZE = TestHelpers.DEFAULT_SIZE;

    @Test @MediumTest
    void recolorFromIters() {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();
        BufferedImage img = r.render(SIZE, SIZE, grad);
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();
        BufferedImage recolored = r.recolorFromIters(iters, size[0], size[1], grad);
        assertTrue(TestHelpers.imagesEqual(img, recolored), "Recolor with same gradient should match");
    }

    @Test @MediumTest
    void recolorDifferentGradientDiffers() {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad1 = TestHelpers.gradient();
        r.render(SIZE, SIZE, grad1);
        int[] iters = r.getLastRenderIters();
        int[] size = r.getLastRenderSize();

        BufferedImage img1 = r.recolorFromIters(iters, size[0], size[1], grad1);
        ColorGradient grad2 = ColorGradient.fromBaseColor(java.awt.Color.RED);
        BufferedImage img2 = r.recolorFromIters(iters, size[0], size[1], grad2);
        assertFalse(TestHelpers.imagesEqual(img1, img2), "Different gradient should differ");
    }

    @Test @MediumTest
    void paletteCycleShiftGradient() {
        ColorGradient grad = TestHelpers.gradient();
        int stopCount = grad.getStops().size();

        ColorGradient shifted0 = PaletteCycleAnimator.shiftGradient(grad, 0f);
        assertEquals(stopCount, shifted0.getStops().size());

        ColorGradient shifted50 = PaletteCycleAnimator.shiftGradient(grad, 0.5f);
        assertEquals(stopCount, shifted50.getStops().size());

        for (ColorGradient.Stop s : shifted50.getStops()) {
            assertTrue(s.getPosition() >= 0f && s.getPosition() <= 1f,
                "Shifted position should be in [0,1]");
        }
    }

    @Test @MediumTest
    void paletteCycleFullRotationWraps() {
        ColorGradient grad = TestHelpers.gradient();
        ColorGradient shifted = PaletteCycleAnimator.shiftGradient(grad, 1.0f);
        for (int i = 0; i < grad.getStops().size(); i++) {
            float origPos = grad.getStops().get(i).getPosition();
            float shiftedPos = shifted.getStops().get(i).getPosition();
            assertEquals(origPos, shiftedPos, 0.001f, "Shift by 1.0 should wrap to original");
        }
    }

    @Test @MediumTest
    void paletteCycleRenderToFiles(@TempDir Path tempDir) throws Exception {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();
        r.render(SIZE, SIZE, grad);
        int[] iters = r.getLastRenderIters().clone();
        int[] size = r.getLastRenderSize();

        PaletteCycleAnimator animator = new PaletteCycleAnimator(r);
        animator.setTotalFrames(5);
        animator.setFps(10);
        animator.setCycleSpeed(1.0f);

        File tmpDir = tempDir.resolve("palette").toFile();
        int count = animator.renderToFiles(tmpDir, iters, size[0], size[1], grad, null);
        assertEquals(5, count);

        File aviFile = new File(tmpDir, "palette_cycle.avi");
        assertTrue(aviFile.exists());
        assertTrue(aviFile.length() > 0);
    }

    @Test @MediumTest
    void iterationAnimatorFramesDiffer() {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();

        r.setMaxIterations(1);
        BufferedImage img1 = r.render(SIZE, SIZE, grad);
        r.setMaxIterations(256);
        BufferedImage img256 = r.render(SIZE, SIZE, grad);
        assertFalse(TestHelpers.imagesEqual(img1, img256), "Iter 1 vs 256 should differ");
    }

    @Test @MediumTest
    void iterationAnimatorRenderToFiles(@TempDir Path tempDir) throws Exception {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();

        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(10);
        animator.setStep(3);
        animator.setSize(50, 50);
        animator.setFps(10);

        File tmpDir = tempDir.resolve("iter").toFile();
        int count = animator.renderToFiles(tmpDir, r, grad, null);
        assertEquals(4, count);

        File aviFile = new File(tmpDir, "iteration_anim.avi");
        assertTrue(aviFile.exists());
        assertTrue(aviFile.length() > 0);
    }

    @Test @MediumTest
    void iterationAnimatorCancel(@TempDir Path tempDir) throws Exception {
        FractalRenderer r = TestHelpers.newRenderer();
        ColorGradient grad = TestHelpers.gradient();

        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(100);
        animator.setStep(1);
        animator.setSize(20, 20);
        animator.setFps(10);
        animator.cancel();

        File tmpDir = tempDir.resolve("cancel").toFile();
        int count = animator.renderToFiles(tmpDir, r, grad, null);
        assertEquals(0, count);
    }

    @Test @MediumTest
    void iterationAnimatorTotalFrames() {
        IterationAnimator animator = new IterationAnimator();
        animator.setStartIter(1);
        animator.setEndIter(256);
        animator.setStep(1);
        assertEquals(256, animator.getTotalFrames());

        animator.setStep(5);
        assertEquals(52, animator.getTotalFrames());

        animator.setStartIter(10);
        animator.setEndIter(50);
        animator.setStep(10);
        assertEquals(5, animator.getTotalFrames());
    }

    @Test @MediumTest
    void paletteCycleRecolorMatchesDirectRender() {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setMaxIterations(64);
        ColorGradient grad = TestHelpers.gradient();
        BufferedImage direct = r.render(50, 50, grad);
        int[] iters = r.getLastRenderIters().clone();
        BufferedImage recolored = r.recolorFromIters(iters, 50, 50, grad);
        assertTrue(TestHelpers.imagesEqual(direct, recolored), "Recolor should match direct render");
    }
}

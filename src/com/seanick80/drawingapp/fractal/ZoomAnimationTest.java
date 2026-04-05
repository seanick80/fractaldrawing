package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

class ZoomAnimationTest {

    @Test @LargeTest @Timeout(10)
    void interpolation() {
        ZoomAnimator.Keyframe from = new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100);
        ZoomAnimator.Keyframe to = new ZoomAnimator.Keyframe(-0.5, 0.5, 100.0, 500);

        ZoomAnimator.Keyframe k0 = ZoomAnimator.interpolate(from, to, 0.0);
        assertTrue(Math.abs(k0.centerReal.doubleValue()) < 0.001);
        assertTrue(Math.abs(k0.zoomLevel.doubleValue() - 1.0) < 0.01);
        assertEquals(100, k0.maxIterations);

        ZoomAnimator.Keyframe k1 = ZoomAnimator.interpolate(from, to, 1.0);
        assertTrue(Math.abs(k1.centerReal.doubleValue() - (-0.5)) < 0.001);
        assertTrue(Math.abs(k1.zoomLevel.doubleValue() - 100.0) < 0.5);
        assertEquals(500, k1.maxIterations);

        ZoomAnimator.Keyframe kMid = ZoomAnimator.interpolate(from, to, 0.5);
        assertTrue(Math.abs(kMid.zoomLevel.doubleValue() - 10.0) < 0.5, "Exponential zoom midpoint ~10");
        assertTrue(Math.abs(kMid.centerReal.doubleValue() - (-0.125)) < 0.001, "Ease-in centerR ~-0.125");
    }

    @Test @LargeTest @Timeout(15)
    void renderFrames(@TempDir Path tempDir) throws Exception {
        FractalRenderer r = TestHelpers.newRenderer();
        r.setBounds(-2.0, 2.0, -2.0, 2.0);
        ZoomAnimator animator = new ZoomAnimator(r, TestHelpers.gradient());
        animator.setSize(50, 50);
        animator.setFramesPerSegment(3);
        animator.setBoomerang(false);
        animator.setInterpolationFrames(0);

        animator.addKeyframe(new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100));
        animator.addKeyframe(new ZoomAnimator.Keyframe(-0.5, 0.0, 10.0, 200));

        assertEquals(4, animator.getRenderedFrameCount());
        assertEquals(4, animator.getTotalFrames());

        File outDir = tempDir.resolve("frames").toFile();
        int[] frameCount = {0};
        int rendered = animator.renderToFiles(outDir, (idx, total, ms) -> frameCount[0]++);
        assertEquals(4, rendered);
        assertEquals(4, frameCount[0]);

        for (int i = 0; i < 4; i++) {
            assertTrue(new File(outDir, String.format("frame_%04d.png", i)).exists());
        }

        // Interpolation frame count
        ZoomAnimator interpAnim = new ZoomAnimator(r, TestHelpers.gradient());
        interpAnim.setSize(50, 50);
        interpAnim.setFramesPerSegment(3);
        interpAnim.setBoomerang(false);
        interpAnim.setInterpolationFrames(3);
        interpAnim.addKeyframe(new ZoomAnimator.Keyframe(0.0, 0.0, 1.0, 100));
        interpAnim.addKeyframe(new ZoomAnimator.Keyframe(-0.5, 0.0, 10.0, 200));
        assertEquals(4, interpAnim.getRenderedFrameCount());
        assertEquals(13, interpAnim.getTotalFrames());

        // zoomCrop
        BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        src.setRGB(5, 5, 0xFF0000);
        BufferedImage cropped = ZoomAnimator.zoomCrop(src, 2.0);
        assertEquals(10, cropped.getWidth());
        assertEquals(10, cropped.getHeight());
        int centerR = (cropped.getRGB(5, 5) >> 16) & 0xFF;
        assertTrue(centerR > 50, "zoomCrop center should have red content");
    }
}

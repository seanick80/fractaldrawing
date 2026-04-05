package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import com.seanick80.drawingapp.tools.FractalAnimationController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;

class ScreensaverTest {

    @Test @MediumTest
    void controllerLifecycle() {
        ScreensaverController controller = new ScreensaverController(5);
        assertFalse(controller.isRunning());
    }

    @Test @MediumTest
    void panelTransition() {
        ScreensaverController.ScreensaverPanel panel =
                new ScreensaverController.ScreensaverPanel(100, 100, () -> {});

        BufferedImage img1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = img1.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 100, 100);
        g1.dispose();

        BufferedImage img2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img2.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 100, 100);
        g2.dispose();

        panel.transitionTo(img1, 500);
        panel.transitionTo(img2, 500);
        // No exception means success
    }

    @Test @MediumTest
    void findLocationNotNull() {
        double[] loc = FractalAnimationController.findInterestingLocation();
        assertNotNull(loc);
        assertEquals(3, loc.length);
        assertTrue(loc[2] > 0, "halfSpan should be > 0");
    }
}

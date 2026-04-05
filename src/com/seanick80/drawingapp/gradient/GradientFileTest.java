package com.seanick80.drawingapp.gradient;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;

class GradientFileTest {

    @Test @MediumTest @Tag("parser")
    void saveLoadGradient(@TempDir Path tempDir) throws Exception {
        ColorGradient original = ColorGradient.fractalDefault();
        File tempFile = tempDir.resolve("test.grd").toFile();

        original.save(tempFile);
        assertTrue(tempFile.exists());

        ColorGradient loaded = ColorGradient.load(tempFile);
        assertEquals(6, loaded.getStops().size());

        for (float t = 0; t <= 1.0f; t += 0.1f) {
            Color orig = original.getColorAt(t);
            Color load = loaded.getColorAt(t);
            assertTrue(Math.abs(orig.getRed() - load.getRed()) <= 1);
            assertTrue(Math.abs(orig.getGreen() - load.getGreen()) <= 1);
            assertTrue(Math.abs(orig.getBlue() - load.getBlue()) <= 1);
        }
    }

    @Test @MediumTest @Tag("parser")
    void loadRejectsBadHeader(@TempDir Path tempDir) throws Exception {
        File badFile = tempDir.resolve("bad.grd").toFile();
        try (PrintWriter pw = new PrintWriter(badFile)) {
            pw.println("NOT_A_GRADIENT");
        }
        assertThrows(java.io.IOException.class, () -> ColorGradient.load(badFile));
    }

    @Test @MediumTest @Tag("parser")
    void gradientDefaultConsistency() {
        ColorGradient g1 = ColorGradient.fractalDefault();
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

        for (int i = 0; i < 256; i++) {
            assertEquals(c1[i].getRGB(), c2[i].getRGB(), "Color mismatch at index " + i);
        }
    }

    @Test @MediumTest @Tag("parser")
    void bug3NoDuplicateGradientDefaults() {
        File srcDefaults = new File("src/com/seanick80/drawingapp/gradient/defaults");
        boolean srcExists = srcDefaults.exists() && srcDefaults.isDirectory();
        if (srcExists) {
            File[] files = srcDefaults.listFiles();
            srcExists = files != null && files.length > 0;
        }
        assertFalse(srcExists, "src gradient defaults directory should be removed");
    }
}

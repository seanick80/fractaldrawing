package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;

class AviWriterTest {

    @Test @MediumTest
    void createsValidFile(@TempDir Path tempDir) throws Exception {
        File tempFile = tempDir.resolve("test.avi").toFile();
        BufferedImage frame = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        AviWriter writer = new AviWriter(tempFile, 10, 10, 30);
        writer.addFrame(frame);
        writer.close();

        assertTrue(tempFile.exists());
        assertTrue(tempFile.length() > 0);

        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
            byte[] riff = new byte[4];
            raf.read(riff);
            assertEquals("RIFF", new String(riff));

            raf.skipBytes(4);
            byte[] avi = new byte[4];
            raf.read(avi);
            assertEquals("AVI ", new String(avi));
        }
    }

    @Test @MediumTest
    void multipleFrames(@TempDir Path tempDir) throws Exception {
        File tempFile = tempDir.resolve("multi.avi").toFile();
        int W = 20, H = 15;
        AviWriter writer = new AviWriter(tempFile, W, H, 24);

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

        int rowStride = ((W * 3 + 3) / 4) * 4;
        int frameSize = rowStride * H;
        long minSize = 5 * (frameSize + 8);
        assertTrue(tempFile.length() > minSize, "AVI file should be large enough for 5 frames");

        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
            raf.seek(48);
            int totalFrames = TestHelpers.readIntLE(raf);
            assertEquals(5, totalFrames, "AVI header should report 5 frames");
        }
    }
}

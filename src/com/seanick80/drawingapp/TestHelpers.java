package com.seanick80.drawingapp;

import com.seanick80.drawingapp.fractal.FractalRenderer;
import com.seanick80.drawingapp.fractal.FractalType;
import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared test helpers extracted from FractalRenderTest.
 * Used by all test classes across the project.
 */
public final class TestHelpers {

    public static final int DEFAULT_SIZE = 100;

    private TestHelpers() {}

    /** Create a default Mandelbrot renderer at standard zoom. */
    public static FractalRenderer newRenderer() {
        FractalRenderer r = new FractalRenderer();
        r.setType(FractalType.MANDELBROT);
        return r;
    }

    /** Renderer at a moderately deep zoom (10^-37 scale). */
    public static FractalRenderer newDeeperZoomRenderer() {
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

    /** Renderer at a deep zoom (10^-33 scale). */
    public static FractalRenderer newDeepZoomRenderer() {
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

    /** Default fractal gradient. */
    public static ColorGradient gradient() {
        return ColorGradient.fractalDefault();
    }

    /** Get all pixels from an image as an int array. */
    public static int[] getPixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    /** Check if two images have identical pixel data. */
    public static boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        int[] pa = getPixels(a);
        int[] pb = getPixels(b);
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) return false;
        }
        return true;
    }

    /** Count the number of distinct ARGB colors in an image. */
    public static int countUniqueColors(BufferedImage img) {
        Set<Integer> colors = new HashSet<>();
        int[] pixels = getPixels(img);
        for (int p : pixels) colors.add(p);
        return colors.size();
    }

    /** Count pixels matching a specific ARGB value. */
    public static int countColor(BufferedImage img, int targetRgb) {
        int count = 0;
        int[] pixels = getPixels(img);
        for (int p : pixels) {
            if (p == targetRgb) count++;
        }
        return count;
    }

    /** Manhattan distance between two RGB colors. */
    public static int colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    /** Create a white ARGB image. */
    public static BufferedImage whiteImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** Create a DrawingCanvas for testing. */
    public static com.seanick80.drawingapp.DrawingCanvas testCanvas(int w, int h) {
        return new com.seanick80.drawingapp.DrawingCanvas(w, h, new UndoManager(10));
    }

    /** Recursively delete a directory and its contents. */
    public static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
    }

    /** Compute a simple pixel checksum (sum of R+G+B for all pixels). */
    public static long pixelChecksum(BufferedImage img) {
        int[] pixels = getPixels(img);
        long sum = 0;
        for (int p : pixels) {
            sum += (p >> 16) & 0xFF;
            sum += (p >> 8) & 0xFF;
            sum += p & 0xFF;
        }
        return sum;
    }

    /** Check if all pixels in an image match the given RGB (ignoring alpha). */
    public static boolean isAllColor(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0x00FFFFFF) != (rgb & 0x00FFFFFF)) return false;
            }
        }
        return true;
    }

    /** Read a little-endian 32-bit integer from a RandomAccessFile. */
    public static int readIntLE(RandomAccessFile raf) throws java.io.IOException {
        int b0 = raf.read(), b1 = raf.read(), b2 = raf.read(), b3 = raf.read();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /** Perceived brightness of a color (0.0 to 1.0). */
    public static float brightness(Color c) {
        return (c.getRed() * 0.299f + c.getGreen() * 0.587f + c.getBlue() * 0.114f) / 255f;
    }
}

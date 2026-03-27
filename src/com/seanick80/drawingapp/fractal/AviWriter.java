package com.seanick80.drawingapp.fractal;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Simple AVI writer using uncompressed RGB (DIB) frames.
 * Universally compatible with all media players — no codec needed.
 * Files are larger than compressed formats but playback is flawless.
 */
public class AviWriter implements AutoCloseable {

    private final RandomAccessFile raf;
    private final File file;
    private final int width;
    private final int height;
    private final int fps;
    private final int rowStride;   // row bytes padded to 4-byte boundary
    private final int frameSize;   // raw frame data size
    private int frameCount = 0;
    private long moviDataStart;
    private int[] frameOffsets = new int[256];

    private static final long RIFF_SIZE_POS = 4;
    private static final long AVIH_TOTAL_FRAMES_POS = 48;
    private static final long STRH_LENGTH_POS = 140;

    private long moviSizePos;

    public AviWriter(File file, int width, int height, int fps) throws IOException {
        this.file = file;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.rowStride = ((width * 3 + 3) / 4) * 4; // 4-byte aligned
        this.frameSize = rowStride * height;
        this.raf = new RandomAccessFile(file, "rw");
        raf.setLength(0);
        writeHeaders();
    }

    public void addFrame(BufferedImage image) throws IOException {
        // Convert to bottom-up BGR row data (BMP/DIB convention)
        byte[] bgrData = new byte[frameSize];
        for (int y = 0; y < height; y++) {
            int srcRow = height - 1 - y; // bottom-up
            int dstOffset = y * rowStride;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, srcRow);
                bgrData[dstOffset + x * 3]     = (byte) (argb & 0xFF);        // B
                bgrData[dstOffset + x * 3 + 1] = (byte) ((argb >> 8) & 0xFF); // G
                bgrData[dstOffset + x * 3 + 2] = (byte) ((argb >> 16) & 0xFF);// R
            }
        }

        // Grow tracking array if needed
        if (frameCount >= frameOffsets.length) {
            int[] newArr = new int[frameOffsets.length * 2];
            System.arraycopy(frameOffsets, 0, newArr, 0, frameCount);
            frameOffsets = newArr;
        }
        frameOffsets[frameCount] = (int) (raf.getFilePointer() - moviDataStart + 4);

        // Write chunk: "00db" + size + raw data
        writeFourCC("00db");
        writeInt(frameSize);
        raf.write(bgrData);

        frameCount++;
    }

    @Override
    public void close() throws IOException {
        long moviEnd = raf.getFilePointer();

        // Write idx1 index
        writeFourCC("idx1");
        writeInt(frameCount * 16);
        for (int i = 0; i < frameCount; i++) {
            writeFourCC("00db");
            writeInt(0x10);              // AVIIF_KEYFRAME
            writeInt(frameOffsets[i]);
            writeInt(frameSize);
        }

        long fileEnd = raf.getFilePointer();

        // Patch headers
        raf.seek(RIFF_SIZE_POS);
        writeInt((int) (fileEnd - 8));

        raf.seek(AVIH_TOTAL_FRAMES_POS);
        writeInt(frameCount);

        raf.seek(STRH_LENGTH_POS);
        writeInt(frameCount);

        raf.seek(moviSizePos);
        writeInt((int) (moviEnd - moviSizePos - 4));

        raf.close();
    }

    private void writeHeaders() throws IOException {
        // RIFF AVI
        writeFourCC("RIFF");
        writeInt(0);
        writeFourCC("AVI ");

        // LIST hdrl
        writeFourCC("LIST");
        long hdrlSizePos = raf.getFilePointer();
        writeInt(0);
        long hdrlContentStart = raf.getFilePointer();
        writeFourCC("hdrl");

        // avih
        writeFourCC("avih");
        writeInt(56);
        writeInt(1_000_000 / fps);      // dwMicroSecPerFrame
        writeInt(frameSize * fps);      // dwMaxBytesPerSec
        writeInt(0);                    // dwPaddingGranularity
        writeInt(0x10);                 // AVIF_HASINDEX
        writeInt(0);                    // dwTotalFrames (patched)
        writeInt(0);                    // dwInitialFrames
        writeInt(1);                    // dwStreams
        writeInt(frameSize);            // dwSuggestedBufferSize
        writeInt(width);
        writeInt(height);
        writeInt(0); writeInt(0);
        writeInt(0); writeInt(0);

        // LIST strl
        writeFourCC("LIST");
        long strlSizePos = raf.getFilePointer();
        writeInt(0);
        long strlContentStart = raf.getFilePointer();
        writeFourCC("strl");

        // strh
        writeFourCC("strh");
        writeInt(56);
        writeFourCC("vids");
        writeInt(0);                    // fccHandler: uncompressed
        writeInt(0);                    // dwFlags
        writeInt(0);                    // wPriority + wLanguage
        writeInt(0);                    // dwInitialFrames
        writeInt(1);                    // dwScale
        writeInt(fps);                  // dwRate
        writeInt(0);                    // dwStart
        writeInt(0);                    // dwLength (patched)
        writeInt(frameSize);            // dwSuggestedBufferSize
        writeInt(-1);                   // dwQuality
        writeInt(0);                    // dwSampleSize
        writeShort(0); writeShort(0);
        writeShort(width); writeShort(height);

        // strf — BITMAPINFOHEADER
        writeFourCC("strf");
        writeInt(40);
        writeInt(40);                   // biSize
        writeInt(width);
        writeInt(height);
        writeShort(1);                  // biPlanes
        writeShort(24);                 // biBitCount
        writeInt(0);                    // biCompression = BI_RGB
        writeInt(frameSize);            // biSizeImage
        writeInt(0);
        writeInt(0);
        writeInt(0);
        writeInt(0);

        // Patch strl size
        long strlEnd = raf.getFilePointer();
        raf.seek(strlSizePos);
        writeInt((int) (strlEnd - strlContentStart));
        raf.seek(strlEnd);

        // Patch hdrl size
        long hdrlEnd = raf.getFilePointer();
        raf.seek(hdrlSizePos);
        writeInt((int) (hdrlEnd - hdrlContentStart));
        raf.seek(hdrlEnd);

        // LIST movi
        writeFourCC("LIST");
        moviSizePos = raf.getFilePointer();
        writeInt(0);
        writeFourCC("movi");
        moviDataStart = raf.getFilePointer();
    }

    private void writeFourCC(String s) throws IOException {
        raf.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 4);
    }

    private void writeInt(int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
        raf.write((v >> 16) & 0xFF);
        raf.write((v >> 24) & 0xFF);
    }

    private void writeShort(int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
    }
}

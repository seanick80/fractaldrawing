package com.seanick80.drawingapp.layers;

import java.awt.*;
import java.awt.image.*;

/**
 * Custom AWT Composite implementing Photoshop-style blend modes.
 * Used during layer compositing to combine layer pixels with the canvas below.
 */
public class BlendComposite implements Composite {

    private final BlendMode mode;
    private final float opacity;

    public BlendComposite(BlendMode mode, float opacity) {
        this.mode = mode;
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    @Override
    public CompositeContext createContext(ColorModel srcCM, ColorModel dstCM, RenderingHints hints) {
        return new BlendContext(mode, opacity);
    }

    private static class BlendContext implements CompositeContext {
        private final BlendMode mode;
        private final float opacity;

        BlendContext(BlendMode mode, float opacity) {
            this.mode = mode;
            this.opacity = opacity;
        }

        @Override
        public void dispose() {}

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), dstIn.getWidth());
            int h = Math.min(src.getHeight(), dstIn.getHeight());

            int[] srcPixel = new int[4];
            int[] dstPixel = new int[4];

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    src.getPixel(x, y, srcPixel);
                    dstIn.getPixel(x, y, dstPixel);

                    float sa = (srcPixel[3] / 255f) * opacity;
                    float da = dstPixel[3] / 255f;

                    if (sa <= 0f) {
                        dstOut.setPixel(x, y, dstPixel);
                        continue;
                    }

                    float sr = srcPixel[0] / 255f;
                    float sg = srcPixel[1] / 255f;
                    float sb = srcPixel[2] / 255f;
                    float dr = dstPixel[0] / 255f;
                    float dg = dstPixel[1] / 255f;
                    float db = dstPixel[2] / 255f;

                    float br = blend(sr, dr);
                    float bg = blend(sg, dg);
                    float bb = blend(sb, db);

                    // Porter-Duff "over" with blended color
                    float outA = sa + da * (1f - sa);
                    float outR, outG, outB;
                    if (outA > 0f) {
                        outR = (br * sa + dr * da * (1f - sa)) / outA;
                        outG = (bg * sa + dg * da * (1f - sa)) / outA;
                        outB = (bb * sa + db * da * (1f - sa)) / outA;
                    } else {
                        outR = outG = outB = 0f;
                    }

                    dstOut.setPixel(x, y, new int[]{
                        clamp(outR), clamp(outG), clamp(outB), clamp(outA)
                    });
                }
            }
        }

        private float blend(float s, float d) {
            return switch (mode) {
                case NORMAL -> s;
                case MULTIPLY -> s * d;
                case SCREEN -> 1f - (1f - s) * (1f - d);
                case OVERLAY -> d < 0.5f ? 2f * s * d : 1f - 2f * (1f - s) * (1f - d);
                case SOFT_LIGHT -> {
                    if (s < 0.5f) yield d - (1f - 2f * s) * d * (1f - d);
                    else yield d + (2f * s - 1f) * ((float) Math.sqrt(d) - d);
                }
                case HARD_LIGHT -> s < 0.5f ? 2f * s * d : 1f - 2f * (1f - s) * (1f - d);
                case DIFFERENCE -> Math.abs(s - d);
                case ADD -> Math.min(1f, s + d);
            };
        }

        private static int clamp(float v) {
            return Math.max(0, Math.min(255, Math.round(v * 255f)));
        }
    }
}

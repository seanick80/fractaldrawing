package com.seanick80.drawingapp.fractal;

public enum FractalType {
    MANDELBROT {
        @Override
        public int iterate(double cx, double cy, int maxIter) {
            double zr = 0, zi = 0;
            for (int i = 0; i < maxIter; i++) {
                double zr2 = zr * zr, zi2 = zi * zi;
                if (zr2 + zi2 > 4.0) return i;
                zi = 2 * zr * zi + cy;
                zr = zr2 - zi2 + cx;
            }
            return maxIter;
        }
    },
    JULIA {
        @Override
        public int iterate(double cx, double cy, int maxIter) {
            // Default Julia constant; overridden via FractalRenderer
            return iterateJulia(cx, cy, -0.7, 0.27015, maxIter);
        }
    };

    public abstract int iterate(double cx, double cy, int maxIter);

    public static int iterateJulia(double zr, double zi, double cr, double ci, int maxIter) {
        for (int i = 0; i < maxIter; i++) {
            double zr2 = zr * zr, zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) return i;
            double newZr = zr2 - zi2 + cr;
            zi = 2 * zr * zi + ci;
            zr = newZr;
        }
        return maxIter;
    }
}

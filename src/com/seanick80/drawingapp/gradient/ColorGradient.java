package com.seanick80.drawingapp.gradient;

import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A color gradient defined by a list of color stops at positions 0.0-1.0.
 * Interpolates linearly between stops.
 */
public class ColorGradient {

    public static class Stop {
        private float position; // 0.0 to 1.0
        private Color color;

        public Stop(float position, Color color) {
            this.position = Math.max(0f, Math.min(1f, position));
            this.color = color;
        }

        public float getPosition() { return position; }
        public void setPosition(float position) { this.position = Math.max(0f, Math.min(1f, position)); }
        public Color getColor() { return color; }
        public void setColor(Color color) { this.color = color; }
    }

    private final List<Stop> stops = new ArrayList<>();

    public ColorGradient() {
        // Default: black to white
        stops.add(new Stop(0f, Color.BLACK));
        stops.add(new Stop(1f, Color.WHITE));
    }

    public ColorGradient(ColorGradient other) {
        for (Stop s : other.stops) {
            stops.add(new Stop(s.position, s.color));
        }
    }

    public List<Stop> getStops() {
        stops.sort(Comparator.comparingDouble(Stop::getPosition));
        return stops;
    }

    public Stop addStop(float position, Color color) {
        Stop s = new Stop(position, color);
        stops.add(s);
        return s;
    }

    public void removeStop(Stop stop) {
        if (stops.size() > 2) { // keep at least 2 stops
            stops.remove(stop);
        }
    }

    /** Interpolate the gradient color at a position 0.0-1.0. */
    public Color getColorAt(float t) {
        t = Math.max(0f, Math.min(1f, t));
        List<Stop> sorted = getStops();
        if (sorted.isEmpty()) return Color.BLACK;
        if (t <= sorted.get(0).position) return sorted.get(0).color;
        if (t >= sorted.get(sorted.size() - 1).position) return sorted.get(sorted.size() - 1).color;

        for (int i = 0; i < sorted.size() - 1; i++) {
            Stop a = sorted.get(i);
            Stop b = sorted.get(i + 1);
            if (t >= a.position && t <= b.position) {
                float range = b.position - a.position;
                float frac = range == 0 ? 0 : (t - a.position) / range;
                return lerpColor(a.color, b.color, frac);
            }
        }
        return sorted.get(sorted.size() - 1).color;
    }

    /** Generate an array of colors sampled evenly across the gradient. */
    public Color[] toColors(int count) {
        Color[] result = new Color[count];
        for (int i = 0; i < count; i++) {
            result[i] = getColorAt((float) i / Math.max(1, count - 1));
        }
        return result;
    }

    private static Color lerpColor(Color a, Color b, float t) {
        return new Color(
            clamp(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t)),
            clamp(Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
            clamp(Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t))
        );
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Save gradient to a file. Format:
     * GRADIENT
     * position R G B
     * ...
     */
    public void save(File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("GRADIENT");
            for (Stop s : getStops()) {
                pw.printf("%.6f %d %d %d%n", s.position,
                    s.color.getRed(), s.color.getGreen(), s.color.getBlue());
            }
        }
    }

    /** Load gradient from a file. Replaces all existing stops. */
    public static ColorGradient load(File file) throws IOException {
        ColorGradient grad = new ColorGradient();
        grad.stops.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null || !header.trim().equals("GRADIENT")) {
                throw new IOException("Not a gradient file");
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                float pos = Float.parseFloat(parts[0]);
                int r = Integer.parseInt(parts[1]);
                int g = Integer.parseInt(parts[2]);
                int b = Integer.parseInt(parts[3]);
                grad.stops.add(new Stop(pos, new Color(clamp(r), clamp(g), clamp(b))));
            }
        }
        if (grad.stops.size() < 2) {
            throw new IOException("Gradient must have at least 2 stops");
        }
        return grad;
    }
}

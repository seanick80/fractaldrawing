package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.gradient.ColorGradient;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Renders an L-System tree using turtle graphics.
 * Parameters are derived from fractal iteration data via {@link PCGController}.
 *
 * Production rules:
 *   Variant 0 (sparse):  F → F[+F]F[-F]F
 *   Variant 1 (bushy):   F → FF-[-F+F+F]+[+F-F-F]
 *
 * Symbols:
 *   F  = draw forward
 *   +  = turn right by angle
 *   -  = turn left by angle
 *   [  = push state (position + heading)
 *   ]  = pop state
 */
public class LSystemRenderer {

    private static final String[] RULES = {
        "F[+F]F[-F]F",          // variant 0: sparse/organic
        "FF-[-F+F+F]+[+F-F-F]"  // variant 1: bushy/symmetric
    };

    private final LSystemParams params;
    private final ColorGradient gradient;

    public LSystemRenderer(LSystemParams params, ColorGradient gradient) {
        this.params = params;
        this.gradient = gradient;
    }

    public LSystemParams getParams() { return params; }

    /**
     * Generate the L-System string by applying production rules.
     */
    String generate() {
        String rule = RULES[Math.min(params.getRuleVariant(), RULES.length - 1)];
        StringBuilder current = new StringBuilder("F");

        Random rng = new Random(
                Float.floatToIntBits(params.getAngle()) ^
                Float.floatToIntBits(params.getLengthDecay()));

        for (int gen = 0; gen < params.getDepth(); gen++) {
            StringBuilder next = new StringBuilder(current.length() * 4);
            for (int i = 0; i < current.length(); i++) {
                char c = current.charAt(i);
                if (c == 'F') {
                    if (rng.nextFloat() < params.getBranchProb()) {
                        next.append(rule);
                    } else {
                        next.append('F');
                    }
                } else {
                    next.append(c);
                }
            }
            current = next;
        }
        return current.toString();
    }

    /**
     * Render the L-System tree to an image of the given dimensions.
     */
    public BufferedImage render(int width, int height) {
        String lsys = generate();

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background
        g.setColor(new Color(10, 10, 20));
        g.fillRect(0, 0, width, height);

        // Compute bounding box first (dry run) to auto-scale
        float[] bounds = computeBounds(lsys);
        float bx0 = bounds[0], by0 = bounds[1], bx1 = bounds[2], by1 = bounds[3];
        float bw = bx1 - bx0;
        float bh = by1 - by0;
        if (bw < 1) bw = 1;
        if (bh < 1) bh = 1;

        float margin = 0.05f;
        float scaleX = width * (1 - 2 * margin) / bw;
        float scaleY = height * (1 - 2 * margin) / bh;
        float scale = Math.min(scaleX, scaleY);
        float offX = width / 2f - (bx0 + bw / 2f) * scale;
        float offY = height - height * margin - by0 * scale;

        // Render turtle path with gradient coloring by depth
        drawTurtle(g, lsys, scale, offX, offY);

        g.dispose();
        return img;
    }

    /**
     * Compute the bounding box of the turtle path without drawing.
     * Returns [minX, minY, maxX, maxY].
     */
    private float[] computeBounds(String lsys) {
        float x = 0, y = 0, heading = (float) Math.toRadians(90); // start pointing up
        float segLen = 1.0f;
        float angleRad = (float) Math.toRadians(params.getAngle());

        float minX = 0, minY = 0, maxX = 0, maxY = 0;

        Deque<float[]> stack = new ArrayDeque<>();

        for (int i = 0; i < lsys.length(); i++) {
            char c = lsys.charAt(i);
            switch (c) {
                case 'F':
                    float nx = x + segLen * (float) Math.cos(heading);
                    float ny = y + segLen * (float) Math.sin(heading);
                    x = nx; y = ny;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    break;
                case '+': heading -= angleRad; break;
                case '-': heading += angleRad; break;
                case '[':
                    stack.push(new float[] { x, y, heading, segLen });
                    segLen *= params.getLengthDecay();
                    break;
                case ']':
                    if (!stack.isEmpty()) {
                        float[] state = stack.pop();
                        x = state[0]; y = state[1]; heading = state[2]; segLen = state[3];
                    }
                    break;
                default: break;
            }
        }
        return new float[] { minX, minY, maxX, maxY };
    }

    /**
     * Draw the turtle path. Colors segments by stack depth using the gradient.
     */
    private void drawTurtle(Graphics2D g, String lsys, float scale, float offX, float offY) {
        float x = 0, y = 0, heading = (float) Math.toRadians(90);
        float segLen = 1.0f;
        float angleRad = (float) Math.toRadians(params.getAngle());
        int depthNow = 0;
        int maxDepth = params.getDepth();
        float baseThickness = Math.max(1f, 4f - params.getDepth() * 0.3f);

        Color[] colors = gradient.toColors(maxDepth + 1);

        Deque<float[]> stack = new ArrayDeque<>();

        for (int i = 0; i < lsys.length(); i++) {
            char c = lsys.charAt(i);
            switch (c) {
                case 'F':
                    float nx = x + segLen * (float) Math.cos(heading);
                    float ny = y + segLen * (float) Math.sin(heading);

                    // Color and thickness by depth
                    int colorIdx = Math.min(depthNow, colors.length - 1);
                    g.setColor(colors[colorIdx]);
                    float thickness = baseThickness * (float) Math.pow(params.getLengthDecay(), depthNow);
                    g.setStroke(new BasicStroke(Math.max(0.5f, thickness * scale * 0.01f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    int sx = (int) (x * scale + offX);
                    int sy = (int) (offY - y * scale);
                    int ex = (int) (nx * scale + offX);
                    int ey = (int) (offY - ny * scale);
                    g.drawLine(sx, sy, ex, ey);

                    x = nx; y = ny;
                    break;
                case '+': heading -= angleRad; break;
                case '-': heading += angleRad; break;
                case '[':
                    stack.push(new float[] { x, y, heading, segLen, depthNow });
                    segLen *= params.getLengthDecay();
                    depthNow++;
                    break;
                case ']':
                    if (!stack.isEmpty()) {
                        float[] state = stack.pop();
                        x = state[0]; y = state[1]; heading = state[2];
                        segLen = state[3]; depthNow = (int) state[4];
                    }
                    break;
                default: break;
            }
        }
    }
}

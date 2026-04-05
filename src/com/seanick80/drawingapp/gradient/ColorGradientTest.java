package com.seanick80.drawingapp.gradient;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ColorGradientTest {

    private float brightness(Color c) {
        return (c.getRed() * 0.299f + c.getGreen() * 0.587f + c.getBlue() * 0.114f) / 255f;
    }

    @Test @SmallTest
    void interpolation_midpointIsGray() {
        ColorGradient g = new ColorGradient(); // black to white
        Color mid = g.getColorAt(0.5f);
        assertTrue(mid.getRed()   >= 125 && mid.getRed()   <= 130, "red=" + mid.getRed());
        assertTrue(mid.getGreen() >= 125 && mid.getGreen() <= 130, "green=" + mid.getGreen());
        assertTrue(mid.getBlue()  >= 125 && mid.getBlue()  <= 130, "blue=" + mid.getBlue());
    }

    @Test @SmallTest
    void interpolation_endpoints() {
        ColorGradient g = new ColorGradient();
        Color start = g.getColorAt(0.0f);
        Color end   = g.getColorAt(1.0f);
        assertEquals(0,   start.getRed());
        assertEquals(0,   start.getGreen());
        assertEquals(0,   start.getBlue());
        assertEquals(255, end.getRed());
        assertEquals(255, end.getGreen());
        assertEquals(255, end.getBlue());
    }

    @Test @SmallTest
    void interpolation_clampsOutOfRange() {
        ColorGradient g = new ColorGradient();
        Color belowRange = g.getColorAt(-0.5f);
        Color atZero     = g.getColorAt(0.0f);
        Color aboveRange = g.getColorAt(1.5f);
        Color atOne      = g.getColorAt(1.0f);
        assertEquals(atZero.getRGB(), belowRange.getRGB());
        assertEquals(atOne.getRGB(),  aboveRange.getRGB());
    }

    @Test @SmallTest
    void addRemoveStops() {
        ColorGradient g = new ColorGradient();
        assertEquals(2, g.getStops().size());

        ColorGradient.Stop red = g.addStop(0.5f, Color.RED);
        assertEquals(3, g.getStops().size());

        Color mid = g.getColorAt(0.5f);
        assertEquals(255, mid.getRed());
        assertEquals(0,   mid.getGreen());
        assertEquals(0,   mid.getBlue());

        g.removeStop(red);
        assertEquals(2, g.getStops().size());

        // Can't remove below 2
        ColorGradient.Stop first = g.getStops().get(0);
        g.removeStop(first);
        assertEquals(2, g.getStops().size());
    }

    @Test @SmallTest
    void stopPositionClamping() {
        ColorGradient.Stop s = new ColorGradient.Stop(1.5f, Color.GREEN);
        assertEquals(1.0f, s.getPosition(), 0.0001f);

        s.setPosition(-0.5f);
        assertEquals(0.0f, s.getPosition(), 0.0001f);
    }

    @Test @SmallTest
    void fromBaseColor_properties() {
        ColorGradient g = ColorGradient.fromBaseColor(Color.RED);
        assertEquals(6, g.getStops().size());

        Color[] colors = g.toColors(256);
        assertEquals(256, colors.length);

        Set<Integer> unique = new HashSet<>();
        for (Color c : colors) unique.add(c.getRGB());
        assertTrue(unique.size() > 50, "expected > 50 unique colors, got " + unique.size());

        Color first = g.getColorAt(0.0f);
        assertTrue(brightness(first) < 0.5f, "first stop not dark: brightness=" + brightness(first));

        Color last = g.getColorAt(1.0f);
        assertTrue(brightness(last) < 0.1f, "last stop not very dark: brightness=" + brightness(last));
    }

    @Test @SmallTest
    void copyConstructor_isIndependent() {
        ColorGradient original = ColorGradient.fractalDefault();
        ColorGradient copy     = new ColorGradient(original);

        assertEquals(original.getStops().size(), copy.getStops().size());

        Color origMid = original.getColorAt(0.5f);
        copy.addStop(0.5f, Color.GREEN);
        assertEquals(original.getStops().size(), copy.getStops().size() - 1);

        Color copyMid = copy.getColorAt(0.5f);
        assertEquals(origMid.getRGB(), original.getColorAt(0.5f).getRGB());
        // The copy has an extra stop, so its midpoint may differ — just verify the original is unchanged
        assertEquals(origMid.getRGB(), original.getColorAt(0.5f).getRGB());
    }

    @Test @SmallTest
    void copyFrom_isIndependent() {
        ColorGradient source = ColorGradient.fractalDefault();
        ColorGradient target = new ColorGradient();
        target.copyFrom(source);

        assertEquals(source.getStops().size(), target.getStops().size());

        // Sample positions produce the same colors
        float[] positions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
        for (float p : positions) {
            assertEquals(source.getColorAt(p).getRGB(), target.getColorAt(p).getRGB(),
                "colors differ at position " + p);
        }

        target.addStop(0.33f, Color.MAGENTA);
        assertEquals(source.getStops().size(), target.getStops().size() - 1,
            "adding stop to target should not affect source");
    }

    @Test @SmallTest
    void sharedGradient_reflectsUpdates() {
        ColorGradient shared = ColorGradient.fractalDefault();
        int savedMid = shared.getColorAt(0.5f).getRGB();

        shared.copyFrom(ColorGradient.fromBaseColor(Color.BLUE));
        int newMid = shared.getColorAt(0.5f).getRGB();
        assertNotEquals(savedMid, newMid, "midpoint should change after copyFrom");

        // A second reference to the same object sees the change
        ColorGradient secondRef = shared;
        assertEquals(newMid, secondRef.getColorAt(0.5f).getRGB());
    }

    @Test @SmallTest
    void colorMapping_isDeterministic() {
        ColorGradient g = ColorGradient.fractalDefault();
        Color[] first  = g.toColors(64);
        Color[] second = g.toColors(64);
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i].getRGB(), second[i].getRGB(), "mismatch at index " + i);
        }
    }

    @Test @SmallTest
    void colorMapping_knownEndpoints() {
        Color[] colors = ColorGradient.fractalDefault().toColors(64);
        Color first = colors[0];
        Color last  = colors[colors.length - 1];
        assertEquals(0,   first.getRed());
        assertEquals(7,   first.getGreen());
        assertEquals(100, first.getBlue());
        assertEquals(0,   last.getRed());
        assertEquals(2,   last.getGreen());
        assertEquals(0,   last.getBlue());
    }
}

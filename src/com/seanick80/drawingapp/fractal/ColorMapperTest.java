package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.SmallTest;
import com.seanick80.drawingapp.TestHelpers;
import com.seanick80.drawingapp.gradient.ColorGradient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;

class ColorMapperTest {

    private FractalColorMapper modMapper() {
        return new FractalColorMapper(TestHelpers.gradient(), 256, FractalRenderer.ColorMode.MOD);
    }

    private FractalColorMapper divisionMapper() {
        return new FractalColorMapper(TestHelpers.gradient(), 256, FractalRenderer.ColorMode.DIVISION);
    }

    @Test @SmallTest
    void modMode_lutSize() {
        FractalColorMapper mapper = modMapper();
        assertEquals(64, mapper.getLut().length);
    }

    @Test @SmallTest
    void modMode_isCyclic() {
        FractalColorMapper mapper = modMapper();
        assertEquals(mapper.colorForIter(0), mapper.colorForIter(64));
    }

    @Test @SmallTest
    void modMode_maxIterIsBlack() {
        FractalColorMapper mapper = modMapper();
        assertEquals(Color.BLACK.getRGB(), mapper.colorForIter(256));
    }

    @Test @SmallTest
    void divisionMode_lutSize() {
        FractalColorMapper mapper = divisionMapper();
        assertEquals(256, mapper.getLut().length);
    }

    @Test @SmallTest
    void divisionMode_maxIterIsBlack() {
        FractalColorMapper mapper = divisionMapper();
        assertEquals(Color.BLACK.getRGB(), mapper.colorForIter(256));
    }

    @Test @SmallTest
    void divisionMode_iter0MatchesModIter0() {
        FractalColorMapper mod = modMapper();
        FractalColorMapper div = divisionMapper();
        assertEquals(mod.colorForIter(0), div.colorForIter(0));
    }
}

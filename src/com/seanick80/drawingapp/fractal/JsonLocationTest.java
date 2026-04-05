package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

class JsonLocationTest {

    @Test @MediumTest @Tag("parser")
    void parseRoundTrip() {
        String json = "{\n  \"type\": \"MANDELBROT\",\n  \"minReal\": \"-2.0\",\n" +
            "  \"maxReal\": \"1.0\",\n  \"maxIterations\": 256\n}";
        Map<String, String> map = FractalJsonUtil.parseJson(json);
        assertEquals(4, map.size());
        assertEquals("MANDELBROT", map.get("type"));
        assertEquals("-2.0", map.get("minReal"));
        assertEquals("256", map.get("maxIterations"));
    }

    @Test @MediumTest @Tag("parser")
    void parseEdgeCases() {
        // Empty JSON
        var empty = FractalJsonUtil.parseJson("{}");
        assertTrue(empty.isEmpty());

        // Extra whitespace
        var ws = FractalJsonUtil.parseJson("{ \"key\" : \"value\" }");
        assertEquals("value", ws.get("key"));

        // Numeric values (unquoted)
        var num = FractalJsonUtil.parseJson("{\"count\": 42}");
        assertEquals("42", num.get("count"));

        // Multiple entries
        var multi = FractalJsonUtil.parseJson(
                "{\"a\": \"1\", \"b\": \"2\", \"c\": \"3\"}");
        assertEquals(3, multi.size());
        assertEquals("1", multi.get("a"));
        assertEquals("3", multi.get("c"));

        // Fragment parsing
        var frag = FractalJsonUtil.parseJsonFragment("\"x\": \"10\", \"y\": \"20\"");
        assertEquals("10", frag.get("x"));
        assertEquals("20", frag.get("y"));
    }

    @Test @MediumTest @Tag("parser")
    void loadMandelbrotNotOverriddenByJuliaConstant() {
        String json = "{\n" +
            "  \"type\": \"MANDELBROT\",\n" +
            "  \"minReal\": \"-2.0\",\n" +
            "  \"maxReal\": \"1.0\",\n" +
            "  \"minImag\": \"-1.5\",\n" +
            "  \"maxImag\": \"1.5\",\n" +
            "  \"maxIterations\": 256,\n" +
            "  \"juliaReal\": \"-0.7\",\n" +
            "  \"juliaImag\": \"0.27015\"\n" +
            "}";
        Map<String, String> data = FractalJsonUtil.parseJson(json);
        String typeName = data.getOrDefault("type", "MANDELBROT");
        FractalType type = FractalType.valueOf(typeName);

        FractalRenderer r = new FractalRenderer();
        r.setType(type);

        if (type instanceof JuliaType && data.containsKey("juliaReal") && data.containsKey("juliaImag")) {
            r.setJuliaConstant(new BigDecimal(data.get("juliaReal")),
                               new BigDecimal(data.get("juliaImag")));
        }

        assertTrue(r.getType() instanceof MandelbrotType);
        assertEquals("MANDELBROT", r.getType().name());
    }
}

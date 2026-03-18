package com.seanick80.drawingapp.fractal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available fractal types. Adding a new type requires
 * implementing FractalType and registering it here.
 */
public class FractalTypeRegistry {

    private static final FractalTypeRegistry DEFAULT = new FractalTypeRegistry();
    static {
        DEFAULT.register(FractalType.MANDELBROT);
        DEFAULT.register(FractalType.JULIA);
    }

    private final Map<String, FractalType> types = new LinkedHashMap<>();

    public static FractalTypeRegistry getDefault() { return DEFAULT; }

    public void register(FractalType type) {
        types.put(type.name(), type);
    }

    public FractalType getByName(String name) {
        return types.get(name);
    }

    public List<String> getNames() {
        return new ArrayList<>(types.keySet());
    }

    public List<FractalType> getAll() {
        return new ArrayList<>(types.values());
    }
}

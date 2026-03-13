package com.seanick80.drawingapp.fills;

import java.util.ArrayList;
import java.util.List;

public class FillRegistry {

    private final List<FillProvider> fills = new ArrayList<>();

    public void register(FillProvider fill) {
        fills.add(fill);
    }

    public List<FillProvider> getAll() {
        return fills;
    }

    public FillProvider getByName(String name) {
        return fills.stream()
            .filter(f -> f.getName().equals(name))
            .findFirst()
            .orElse(fills.isEmpty() ? null : fills.get(0));
    }
}

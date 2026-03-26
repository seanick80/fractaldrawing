package com.seanick80.drawingapp.fractal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared utility for parsing flat JSON objects used by fractal location files.
 */
public class FractalJsonUtil {

    public static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        return parseJsonFragment(json);
    }

    /** Parse key-value pairs from a JSON fragment (no surrounding braces). */
    public static Map<String, String> parseJsonFragment(String fragment) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : fragment.split("[,\n]")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            map.put(key, value);
        }
        return map;
    }
}

# Drawing App

A Java Swing drawing application with an integrated fractal explorer featuring arbitrary-precision deep zoom with perturbation theory optimization.

![Drawing App — Mandelbrot fractal with gradient editor](fractaldrawing.png)

## Features

### Drawing Tools
- **Pencil, Line, Rectangle, Oval, Eraser, Flood Fill** with configurable stroke size
- **Color picker**: 20-color palette + custom color chooser, foreground/background colors
- **Pluggable fill system**: Solid, Gradient, Custom Gradient, Checkerboard, Diagonal Stripes
- **Undo/Redo**: Up to 50 levels (Ctrl+Z / Ctrl+Y)
- **File I/O**: Open and save PNG, JPG, BMP images

### Fractal Explorer
- **5 fractal types**: Mandelbrot, Julia, Burning Ship, Tricorn, and Magnet Type I — selectable from dropdown and menu
- **Arbitrary-precision deep zoom** using BigDecimal arithmetic — no pixelation at any zoom level
- **Perturbation theory**: Computes one reference orbit at full precision, then uses fast double arithmetic for all other pixels. Automatic fallback to BigDecimal for interior pixels where perturbation is invalid.
- **Auto-switching**: Renders in double precision at shallow zoom (fast), automatically switches to perturbation + BigDecimal past ~10^13 zoom
- **Render modes**: AUTO (default), DOUBLE, BIGDECIMAL, PERTURBATION — selectable for benchmarking
- **Color modes**: Mod (cyclic) for consistent color detail at all zoom levels, or Division (linear) for smooth gradients
- **Quadtree cache**: Caches iteration counts in complex-plane coordinates, reused across renders in double mode
- **Custom color gradients**: Full gradient editor with save/load support
- **Save/Load locations**: Export and import fractal coordinates as JSON for bookmarking and sharing
- **Preset locations**: Built-in menu with interesting locations from Seahorse Valley to 10^18 zoom
- **Async rendering**: Non-blocking renders with cancellation support for responsive UI
- **Render progress**: Live percentage, row count, elapsed time, and ETA display during slow renders
- **Extensible type system**: New fractal types auto-populate UI via registry — implement `FractalType`, register, done

## Build & Run

Requires Java 17+.

```bash
# Unix/Git Bash
./build.sh run

# Windows
build.cmd run
```

## Testing

```bash
# Run regression tests (89 assertions covering all render modes and fractal types)
./test.sh       # Unix/Git Bash
test.cmd        # Windows

# Or directly
java -ea -cp out com.seanick80.drawingapp.fractal.FractalRenderTest
```

Tests cover:
- Golden-value pixel checksums for Mandelbrot, Julia (double, perturbation, BigDecimal)
- Perturbation vs BigDecimal correctness (structural match, interior pixel accuracy)
- Deep zoom overflow handling (zoom > 10^17)
- Double precision degradation detection
- All 5 fractal types: iteration properties, BigDecimal/double agreement, rendering validity
- Cross-mode rendering (all types × DOUBLE + BIGDECIMAL)
- Type registry round-trip (name → lookup → match)
- ViewportCalculator aspect-ratio correction, ColorMapper LUT construction
- QuadTree cache contract, JSON parsing, gradient consistency
- Cache stability across zoom cycles, render mode switching

## Benchmarking

```bash
# Performance benchmark — runs all locations, compares modes for deep zoom
java -cp out com.seanick80.drawingapp.fractal.FractalBenchmark benchmarks/

# Perturbation correctness evaluation — pixel-by-pixel comparison vs BigDecimal
java -cp out com.seanick80.drawingapp.fractal.PerturbationEval benchmarks/

# Single location with custom size
java -cp out com.seanick80.drawingapp.fractal.FractalBenchmark benchmarks/bigdecimal_location.json 800 600
```

Benchmark locations included:
- `bigdecimal_location.json` — zoom 4.16e13, 456 iterations
- `deeper_location.json` — zoom 3.41e17, 706 iterations
- `fractal_zoomed_5.json` — zoom 2.73e18, 506 iterations
- `perturbation_error.json` — zoom 4.50e15, 41% interior pixels (perturbation stress test)
- `double_seahorse.json` — Seahorse valley, moderate zoom
- `double_mini_mandelbrot.json` — Mini Mandelbrot at -1.767

## Architecture

```
src/com/seanick80/drawingapp/
├── DrawingApp.java          # Main frame, menus (File, Edit, Fractal)
├── DrawingCanvas.java       # Canvas with mouse/wheel event routing
├── ToolBar.java             # Tool selection and settings panel
├── fills/                   # Pluggable fill providers
├── gradient/                # Color gradient editor and interpolation
├── fractal/
│   ├── FractalType.java         # Interface for fractal iteration
│   ├── FractalTypeRegistry.java # Dynamic type registry (auto-populates UI)
│   ├── MandelbrotType.java      # Mandelbrot: z²+c
│   ├── JuliaType.java           # Julia: z²+c (fixed c)
│   ├── BurningShipType.java     # Burning Ship: (|Re(z)|+i|Im(z)|)²+c
│   ├── TricornType.java         # Tricorn: conj(z)²+c
│   ├── MagnetTypeIType.java     # Magnet I: ((z²+c-1)/(2z+c-2))²
│   ├── PerturbationStrategy.java    # Interface for perturbation theory
│   ├── MandelbrotPerturbation.java  # Mandelbrot perturbation impl
│   ├── JuliaPerturbation.java       # Julia perturbation impl
│   ├── FractalRenderer.java    # Rendering orchestrator: mode selection, async
│   ├── ViewportCalculator.java # Aspect-ratio-corrected viewport math
│   ├── FractalColorMapper.java # Color LUT construction + mapping
│   ├── IterationQuadTree.java  # Spatial cache for iteration counts
│   ├── FractalJsonUtil.java    # Shared JSON parsing
│   ├── FractalBenchmark.java   # CLI performance benchmark
│   ├── PerturbationEval.java   # CLI perturbation correctness evaluation
│   └── FractalRenderTest.java  # 89-assertion regression test suite
└── tools/
    ├── Tool.java            # Tool interface
    ├── FractalTool.java     # Fractal UI: zoom, save/load, async render
    └── ...                  # Pencil, Line, Rectangle, Oval, Eraser, Fill
```

## Adding Custom Fills

1. Create a class implementing `FillProvider` in `src/com/seanick80/drawingapp/fills/`
2. Register it in `DrawingApp.registerDefaultFills()`

```java
public class MyCustomFill implements FillProvider {
    @Override public String getName() { return "My Fill"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int w, int h) {
        return new GradientPaint(x, y, baseColor, x + w, y, Color.WHITE);
    }
}
```

## Adding New Fractal Types

1. Create a class implementing `FractalType` with `iterate()` and `iterateBig()` methods
2. Register it in `FractalTypeRegistry` static initializer — it auto-appears in the UI

```java
public final class MyFractalType implements FractalType {
    @Override public String name() { return "MY_FRACTAL"; }

    @Override
    public int iterate(double cx, double cy, int maxIter) {
        // Your escape-time iteration formula here
    }

    @Override
    public int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc) {
        // Same formula in arbitrary precision
    }
}
```

## Future Work

### Performance

- **Memory hygiene** — Pre-allocate buffers on fractal tool initialization instead of reallocating per render. Remove allocations from the perturbation and BigDecimal hot paths where optimization matters most.

- **Interior pruning improvements** — Current Mariani-Silver boundary sampling is pixel-perfect but expensive. Explore hierarchical quadtree subdivision: if a block's boundary is all interior, skip it; otherwise subdivide and repeat.

- **Pre-calculate pixel coordinates** — Compute the real and imaginary axis values for the current viewport once (two 1D arrays), then pass them into each pixel worker.

- **Perturbation for new types** — Burning Ship, Tricorn, and Magnet currently fall back to pure BigDecimal for deep zoom. Adding perturbation strategies for these would significantly improve deep zoom performance.

- **Custom FixedPrecisionFloat** — Investigate a mutable fixed-width binary float using `long[]` limbs with zero allocation in the inner loop, as a faster alternative to BigDecimal for deep zoom.

### Features

- **More fractal types** — Mandelbulb/Mandelbox (3D), Sierpinski triangle/carpet, Koch snowflake (IFS fractals — would require a separate rendering paradigm)

- **Animations**
  - *Iteration animation* — Render incrementally (add one iteration per frame), save as video
  - *Zoom animation* — Smooth animated zoom into a target location
  - *Palette cycle animation* — Rotate colors through the gradient over time

- **Random location explorer** — Heuristic-based discovery of interesting fractal locations. Pick random coordinates, sample a few points, and filter for locations with varied palette entries.

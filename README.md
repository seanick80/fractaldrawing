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
- **Mandelbrot and Julia sets** with click-to-zoom and scroll wheel navigation
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
# Run regression tests (19 assertions covering all render modes)
./test.sh       # Unix/Git Bash
test.cmd        # Windows

# Or directly
java -ea -cp out com.seanick80.drawingapp.fractal.FractalRenderTest
```

Tests cover:
- Render determinism
- Perturbation vs BigDecimal correctness (structural match, interior pixel accuracy)
- Deep zoom overflow handling (zoom > 10^17)
- Double precision degradation detection
- Julia set rendering
- Cache stability across zoom cycles
- All render mode switching

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
│   ├── FractalType.java     # Mandelbrot/Julia iteration (double + BigDecimal)
│   ├── FractalRenderer.java # Rendering engine: double, perturbation, BigDecimal paths
│   ├── IterationQuadTree.java  # Spatial cache for iteration counts
│   ├── FractalBenchmark.java   # CLI performance benchmark
│   ├── PerturbationEval.java   # CLI perturbation correctness evaluation
│   └── FractalRenderTest.java  # Regression tests
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

## Future Work

### Performance

- **Memory hygiene** — Pre-allocate buffers on fractal tool initialization instead of reallocating per render. Remove allocations from the perturbation and BigDecimal hot paths where optimization matters most.

- **Interior pruning improvements** — Current Mariani-Silver boundary sampling is pixel-perfect but expensive. Explore hierarchical quadtree subdivision: if a block's boundary is all interior, skip it; otherwise subdivide and repeat. This should significantly reduce the number of BigDecimal fallback computations for interior-heavy regions.

- **Pre-calculate pixel coordinates** — Compute the real and imaginary axis values for the current viewport once (two 1D arrays), then pass them into each pixel worker. Saves 2–4 orders of magnitude of coordinate mapping computation per iteration.

- **Progress indicator** — Add percent-complete status for slow renders. Especially useful for deep zoom locations like:
  ```json
  {
    "type": "MANDELBROT",
    "minReal": "-1.25015600357202160012093372643103594",
    "maxReal": "-1.25015600357202069062623195350279806",
    "minImag": "0.00967906445708459917854086823385124937",
    "maxImag": "0.00967906445708550867324264116208916425",
    "maxIterations": 556
  }
  ```

### Features

- **Animations**
  - *Iteration animation* — Render incrementally (add one iteration per frame), save as video
  - *Zoom animation* — Smooth animated zoom into a target location
  - *Palette cycle animation* — Rotate colors through the gradient over time
  - Previous version of this app had these as selectable screen saver animations

- **Random location explorer** — Heuristic-based discovery of interesting fractal locations. Pick random coordinates in [-2, 2] with random zoom levels, sample a few points, and filter for locations with varied palette entries (not all interior). Could leverage AI for smarter location selection.

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
- **Image zoom**: Scroll wheel zooms the rendered image (0.25x–32x) centered on cursor for pixel-level inspection without re-rendering. View resets on next fractal render
- **Click and drag panning**: Drag to pan the viewport — the raster image shifts with the cursor for instant visual feedback, then re-renders on release
- **Fractal zoom**: Ctrl+scroll zooms in/out of the fractal (changes complex-plane viewport and triggers re-render). Left/right click also zooms in/out centered on click position
- **Cross-render cache**: Double-precision quadtree cache for moderate zoom; BigDecimal pixel-mapping cache for deep zoom. On zoom, viewport origin snapping aligns pixel grids for 25% reuse; on pan, 75%+ reuse. Iteration results from the previous render are mapped to new pixel positions via O(width+height) BigDecimal divisions.
- **Custom color gradients**: Full gradient editor with save/load support. Double-click a stop marker in the preview bar to open a color chooser — R/G/B control points auto-update to match.
- **Palette-to-gradient**: Click any color in the palette while the fractal tool is active to instantly generate a gradient favoring that color with triadic complementary hues
- **"I Feel Lucky"**: Button that finds a random interesting Mandelbrot location with varied boundary detail
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
# Run regression tests (114 assertions covering all render modes and fractal types)
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
- Interior pruning correctness: pixel-identical output with pruning on/off at spiky edges
- Load/save correctness: Mandelbrot type preserved when JSON contains Julia fields
- Deep zoom cache safety: no false hits from stale double-precision cache entries
- Previous-render BigDecimal cache: 25% reuse on 2x zoom, 75% on pan, pixel-identical to from-scratch
- Shallow zoom quadtree cache: pan reuse verified with correctness checks

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
- `mandelbrot_cardioid.json` — Shallow cardioid view, interior-heavy
- `mandelbrot_bulb_edge.json` — Period-2 bulb edge, 512 iterations
- `mandelbrot_deep_interior.json` — Cardioid center at zoom 2e14

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
│   └── FractalRenderTest.java  # 114-assertion regression test suite
└── tools/
    ├── Tool.java            # Tool interface
    ├── FractalTool.java     # Fractal UI: zoom, pan, save/load, async render
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

- ~~**Memory hygiene**~~ DONE — Pre-allocated render buffers, promoted BigDecimal constants to static finals, cached perturbation strategies, pre-computed pixel coordinate arrays
- ~~**Interior pruning**~~ DONE — Hierarchical quadtree subdivision with 4-corner quick rejection
- ~~**Pre-calculate pixel coordinates**~~ DONE — One-time computation of real/imaginary axis arrays per render
- ~~**Cross-render cache reuse**~~ DONE — Double-precision quadtree for moderate zoom; BigDecimal pixel-mapping cache for deep zoom with viewport origin snapping (25% reuse on zoom, 75%+ on pan)
- **Perturbation for new types** — Burning Ship, Tricorn, and Magnet currently fall back to pure BigDecimal for deep zoom
- **Custom FixedPrecisionFloat** — Mutable fixed-width binary float using `long[]` limbs as a faster alternative to BigDecimal

### Features

- ~~**Click and drag panning**~~ DONE — Raster image shifts with cursor, re-renders on release
- ~~**Gradient color picker**~~ DONE — Double-click stop markers in gradient preview bar to pick colors visually
- ~~**Palette-to-gradient**~~ DONE — Click palette colors to auto-generate triadic gradients
- ~~**"I Feel Lucky"**~~ DONE — Random interesting Mandelbrot location finder
- **More fractal types** — Mandelbulb/Mandelbox (3D), Sierpinski triangle/carpet, Koch snowflake (IFS fractals)
- **Animations** — Iteration animation, zoom animation, palette cycle animation

# Architecture Review: Drawing App (Java Fractal Explorer)

**Date:** 2026-03-18
**Scope:** All source files in `src/com/seanick80/drawingapp/` and project infrastructure
**Confidence:** High -- every `.java` file was read in full

---

## Executive Summary

A Java Swing drawing application with an integrated fractal explorer. The codebase is approximately 2,800 lines across 23 `.java` files organized into 4 packages. The fractal subsystem is the most sophisticated component, featuring arbitrary-precision deep zoom with perturbation theory optimization. The fill system uses a clean registry/provider pattern. The primary extensibility concern is the `FractalType` enum, which hardcodes iteration logic for Mandelbrot and Julia and is referenced throughout the rendering pipeline in ways that would require careful modification to add new fractal types.

---

## 1. Component Map with Dependencies

### Package Structure

```
com.seanick80.drawingapp/
  DrawingApp.java          -- Main frame, menus, wiring
  DrawingCanvas.java       -- Canvas panel, mouse event routing
  ToolBar.java             -- Tool buttons + settings panel
  ColorPicker.java         -- Palette + fg/bg color selection
  StatusBar.java           -- Coordinate and canvas size display
  UndoManager.java         -- Undo/redo via image snapshot stack

  tools/
    Tool.java              -- Tool interface
    ToolSettingsContext.java -- Interface for sharing toolbar components
    PencilTool.java
    LineTool.java
    RectangleTool.java
    OvalTool.java
    EraserTool.java
    FillTool.java
    FractalTool.java       -- Fractal UI: zoom, save/load, async render

  fills/
    FillProvider.java       -- Fill interface: getName() + createPaint()
    AngledFillProvider.java -- Sub-interface adding angle support
    FillRegistry.java       -- Registry of FillProvider instances
    SolidFill.java
    GradientFill.java
    CustomGradientFill.java
    CheckerboardFill.java
    DiagonalStripeFill.java

  gradient/
    ColorGradient.java       -- Gradient data model (stops, interpolation, save/load)
    GradientEditorPanel.java -- Per-channel R/G/B curve editor widget
    GradientEditorDialog.java -- Modal dialog wrapping the editor

  fractal/
    FractalType.java         -- Enum: MANDELBROT, JULIA with iterate()/iterateBig()
    FractalRenderer.java     -- Rendering engine: double, perturbation, BigDecimal paths
    IterationQuadTree.java   -- Spatial cache for iteration counts in complex-plane coords
    FractalRenderTest.java   -- Regression tests (19 assertions, standalone main())
    FractalBenchmark.java    -- CLI performance benchmark
    PerturbationEval.java    -- CLI perturbation correctness evaluation
```

### Dependency Graph (key relationships)

```
DrawingApp
  --> DrawingCanvas, ToolBar, ColorPicker, StatusBar, UndoManager
  --> FillRegistry (owns, passes to ToolBar)
  --> FractalRenderer, FractalType (menu presets reference these directly)

ToolBar
  --> DrawingCanvas (sets active tool)
  --> FillRegistry (populates fill dropdown)
  --> All Tool implementations (instantiates them)
  --> CustomGradientFill, AngledFillProvider (instanceof checks)
  --> GradientEditorDialog (opens editor for custom gradient)

FractalTool
  --> FractalRenderer (owns instance, delegates rendering)
  --> ColorGradient, GradientEditorDialog (gradient editing)
  --> FractalType (type selection combo hardcodes Mandelbrot/Julia)

FractalRenderer
  --> FractalType (delegates iterate/iterateBig)
  --> IterationQuadTree (caches iteration counts)
  --> ColorGradient (colors the output)

FractalType (enum)
  --> standalone: no dependencies, pure computation
```

---

## 2. Extension Points

### 2a. Fill System (clean, extensible)

The fill system is the best-designed extension point:

1. Implement `FillProvider` (or `AngledFillProvider` for angle support)
2. Register in `DrawingApp.registerDefaultFills()` (line 61-67)
3. Automatically appears in toolbar dropdown

**Evidence:**
- `FillProvider` interface: `src/.../fills/FillProvider.java` L15-18 -- just `getName()` + `createPaint()`
- `FillRegistry.register()`: `src/.../fills/FillRegistry.java` L10-11
- `ToolBar.buildFillCombo()`: `src/.../ToolBar.java` L150-158 -- iterates registry
- ToolBar handles `AngledFillProvider` via instanceof: `src/.../ToolBar.java` L247, L300, L306

### 2b. Tool System (moderately extensible)

The `Tool` interface (`src/.../tools/Tool.java`) defines a clean contract:
- `getName()`, mouse event methods, `drawPreview()`, `onActivated()`, `createSettingsPanel()`
- Adding a new tool: implement `Tool`, add to `ToolBar.addTool()` calls (line 59-66)

**Friction points:**
- `ToolBar.applyStrokeSize()` (L371-378) uses instanceof chains for each tool with stroke size
- `ToolBar.applyFillSettings()` (L380-396) uses instanceof chains for fill-capable tools
- No common interface for "stroke-capable" or "fill-capable" tools

### 2c. Fractal Type System (tight coupling -- main pain point)

`FractalType` is an enum with 2 values: `MANDELBROT` and `JULIA`. This is hardcoded in multiple locations:

**All references to FractalType:**

| Location | File:Line | What it does |
|---|---|---|
| Enum definition | `FractalType.java` L6-79 | Defines MANDELBROT, JULIA with iterate/iterateBig |
| Renderer field | `FractalRenderer.java` L34 | `private FractalType type = FractalType.MANDELBROT` |
| Renderer setType | `FractalRenderer.java` L58-63 | Sets type, clears cache |
| Julia cache clear | `FractalRenderer.java` L112 | Only clears cache for Julia type |
| Double render | `FractalRenderer.java` L262-268 | `if (type == FractalType.JULIA)` dispatches to Julia iterate |
| BigDecimal render | `FractalRenderer.java` L336 | `boolean isJulia = (type == FractalType.JULIA)` |
| Pure BD render | `FractalRenderer.java` L546 | Same pattern |
| Interior pruning | `FractalRenderer.java` L493-506 | Julia vs Mandelbrot dispatch |
| Perturbation | `FractalRenderer.java` L701-738 | `isJulia` flag changes delta computation |
| Reference orbit | `FractalRenderer.java` L338-343 | Separate methods for Mandelbrot vs Julia |
| Menu type selector | `DrawingApp.java` L148-166 | Hardcoded radio buttons: Mandelbrot, Julia |
| Preset locations | `DrawingApp.java` L196-228 | Each preset specifies FractalType |
| FractalTool combo | `FractalTool.java` L71 | `new String[]{"Mandelbrot", "Julia"}` |
| FractalTool combo action | `FractalTool.java` L75-76 | `selectedIndex == 0 ? MANDELBROT : JULIA` |
| Load location | `FractalTool.java` L454 | `FractalType.valueOf(typeName)` |
| Load combo sync | `FractalTool.java` L457 | `type == FractalType.MANDELBROT ? 0 : 1` |
| Save location | `FractalTool.java` L416 | `renderer.getType().name()` |
| Tests | `FractalRenderTest.java` L289 | `r.setType(FractalType.MANDELBROT)` |
| Tests Julia | `FractalRenderTest.java` L226 | `r.setType(FractalType.JULIA)` |
| Benchmark | `FractalBenchmark.java` L64 | `FractalType.valueOf(loc.get("type"))` |
| PerturbationEval | `PerturbationEval.java` L64 | Same |

---

## 3. Tight Coupling and Hardcoded Assumptions

### 3a. FractalType enum bakes in iteration logic AND is a closed set

The enum pattern means adding a new fractal type requires modifying the existing enum file. Worse, the renderer hardcodes `isJulia` branching logic throughout:

- **Perturbation theory assumes z^2+c iteration form.** The perturbation formula in `perturbIterate()` (FractalRenderer L701-738) computes `dz_{n+1} = 2*Z_n*dz_n + dz_n^2 + dc` which is specific to z^2+c (Mandelbrot/Julia). Burning Ship, Tricorn, and Magnet fractals have different perturbation formulas.

- **Reference orbit computation is Mandelbrot/Julia-specific.** `computeReferenceOrbitMandelbrot()` and `computeReferenceOrbitJulia()` are separate private methods in FractalRenderer (L601-686). A new type would need its own reference orbit method AND its own perturbation formula.

- **Julia constant is a special case.** The renderer stores `juliaReal`/`juliaImag` fields and only uses them when `type == JULIA`. This is parameter passing through the renderer rather than the type.

### 3b. Duplicated JSON parsing

`parseJson()` is copy-pasted identically in 3 files:
- `FractalTool.java` L496-522
- `FractalBenchmark.java` L206-224
- `PerturbationEval.java` L230-248

### 3c. Duplicated gradient construction

`createGradient()` (the fractal default gradient) is repeated in 4 places:
- `FractalTool()` constructor L43-52
- `FractalRenderTest.gradient()` L317-327
- `FractalBenchmark.createGradient()` L186-195
- `PerturbationEval.createGradient()` L210-219

### 3d. ToolBar instanceof chains

`ToolBar.applyStrokeSize()` (L371-378) and `applyFillSettings()` (L380-396) use instanceof checks to dispatch to concrete tool types. Adding a new tool with stroke or fill capability requires updating these methods.

### 3e. Menu-to-FractalTool coupling

`DrawingApp.createFractalMenu()` (L143-233) reaches through `FractalTool` to `FractalRenderer` to set type, bounds, iterations. The menu knows about `FractalRenderer`'s API directly via `ft.getRenderer()`.

### 3f. FractalTool type combo is index-based

`FractalTool.java` L71: `new String[]{"Mandelbrot", "Julia"}` -- adding a type requires changing the string array AND updating the index-based dispatch at L75-76 and L457.

---

## 4. Test Coverage

### What is tested

`FractalRenderTest.java` -- 19 assertions across 9 test methods, run via `java -ea`:

| Test | Assertions | What it verifies |
|---|---|---|
| `testDoubleRenderDeterministic` | 1 | Same output for same input |
| `testBigDecimalMatchesPerturbation` | 3 | Structural match, non-trivial output, color diversity |
| `testDeeperZoomAllModes` | 4 | All 4 render modes work at 3.4e17 zoom |
| `testPerturbationInteriorPixels` | 1 | No false escapes at interior-heavy location |
| `testDoubleProducesBlockyAtDeepZoom` | 1 | Double differs from BigDecimal at deep zoom |
| `testDoubleModeShallowZoom` | 2 | Rich output, interior pixels present |
| `testJuliaSetRenders` | 2 | Julia has detail and interior pixels |
| `testIterationCountsPreservedAcrossZoom` | 1 | Cache stability across zoom cycles |
| `testRenderModeSwitch` | 4 | Each render mode produces valid output |

### What is NOT tested

- **No tests for:** Drawing tools (Pencil, Line, Rectangle, Oval, Eraser, Fill)
- **No tests for:** Fill system (FillProvider, FillRegistry, any fill implementation)
- **No tests for:** Gradient system (ColorGradient interpolation, save/load)
- **No tests for:** UI components (ToolBar, ColorPicker, DrawingCanvas)
- **No tests for:** UndoManager
- **No tests for:** FractalTool (save/load location, zoom math, async rendering)
- **No tests for:** IterationQuadTree (insert, lookup, prune)

The test suite is custom (no JUnit, no framework). Tests use `assert`-style `check()` calls with a manual pass/fail counter.

---

## 5. Build System

### Build (no build tool -- raw javac)

- `build.sh` / `build.cmd`: Find all `.java` files, pass to `javac -d out`, class files go to `out/`
- No Maven, Gradle, or Ant. No dependency management.
- Requires Java 17+ (uses pattern matching `instanceof`, switch expressions, text blocks)

### Run

- `run.sh` / `run.cmd`: Passes `--gradient-dir` pointing to `src/.../gradient/defaults/`
- `build.cmd run` does not pass `--gradient-dir` (Windows inconsistency at L22 vs `build.sh` L19-20)

### Test

- `test.sh` / `test.cmd`: Compiles then runs `java -ea -cp out ...FractalRenderTest`
- No test framework -- custom main() with manual assertions

### CLI tools

- `FractalBenchmark`: Performance measurement for all render modes
- `PerturbationEval`: Correctness comparison of perturbation vs BigDecimal

---

## 6. Rendering Pipeline (from user click to pixel output)

### Entry: User clicks canvas with Fractal tool active

```
1. DrawingCanvas.mousePressed()             [DrawingCanvas.java L99-107]
   --> activeTool.mousePressed(image, x, y, canvas)

2. FractalTool.mousePressed()               [FractalTool.java L224-261]
   - Gets current BigDecimal bounds from renderer
   - Calculates MathContext precision based on zoom level
   - Maps click pixel to complex coordinate using BigDecimal
   - Computes new bounds (zoom in on left-click, zoom out on right-click)
   - Calls renderer.setBounds() with new BigDecimal bounds
   - Calls renderAsync()

3. FractalTool.renderAsync()                [FractalTool.java L298-332]
   - Cancels any in-progress render (renderer.cancelRender() + worker.cancel())
   - Creates SwingWorker that calls renderer.render(w, h, gradient)
   - On completion: draws fractal image onto canvas image, calls canvas.repaint()

4. FractalRenderer.render()                 [FractalRenderer.java L150-199]
   - Acquires renderLock (prevents concurrent renders)
   - AUTO mode: checks if range < 1e-13 to decide double vs BigDecimal
   - Dispatches to renderDouble(), renderBigDecimal(), or renderPureBigDecimal()
```

### renderDouble() path (shallow zoom):

```
5. FractalRenderer.renderDouble()           [FractalRenderer.java L215-295]
   - Builds color LUT from gradient
   - Computes aspect-corrected viewport in complex coordinates
   - Parallel row iteration: IntStream.range(0, height).parallel()
     - For each pixel: check quadtree cache first
     - Cache miss: call type.iterate(cx, cy, maxIter) or iterateJulia()
     - Map iteration count to color via LUT
   - Sequential pass: insert cache misses into quadtree
   - Prune quadtree to 3x viewport
   - Returns BufferedImage
```

### renderBigDecimal() path (deep zoom with perturbation):

```
6. FractalRenderer.renderBigDecimal()       [FractalRenderer.java L299-491]
   Phase 1: Interior Pruning (Mariani-Silver)
   - Divides image into 50x100 pixel blocks
   - For each block: iterates all boundary pixels using full BigDecimal
   - If all boundary pixels are interior (maxIterations), fills block black

   Phase 2: Perturbation Theory
   - Computes single reference orbit at viewport center using BigDecimal
   - For each non-interior pixel:
     - Computes delta from center in double precision
     - Runs perturbation iteration (fast double arithmetic)
     - On GLITCH_DETECTED: falls back to full BigDecimal for that pixel
   - Multi-threaded via ExecutorService (one task per row)
   - Returns BufferedImage
```

### Coloring:

```
7. colorForIter()                           [FractalRenderer.java L209-213]
   - maxIterations --> black (interior)
   - MOD mode: lut[iter % 64] (cyclic, consistent across zoom levels)
   - DIVISION mode: lut[iter] (linear mapping, gradient spans full range)

   LUT built from ColorGradient.toColors() which samples the gradient
   at evenly spaced positions.
```

### Scroll wheel zoom:

Same pipeline but with 0.8x/1.25x zoom factor instead of 0.5x/2.0x for click zoom. Maps mouse position to complex coordinate and recenters. (`FractalTool.mouseWheelMoved()` L264-296)

---

## 7. Key Architectural Observations

### Strengths

- **Fill system** uses a proper registry/provider pattern with clean interfaces
- **Tool interface** is well-designed with optional overrides via defaults
- **ToolSettingsContext** allows tools to compose shared UI components
- **Rendering** is properly async with cancellation support
- **BigDecimal precision** auto-scales with zoom level
- **Perturbation theory** with glitch detection and fallback is sophisticated
- **Cache** uses quadtree in complex-plane coordinates (survives partial zooms)
- **Interior pruning** via Mariani-Silver boundary sampling
- **Regression tests** cover the critical rendering correctness invariants

### Weaknesses

- **FractalType enum** is the biggest extensibility bottleneck for new fractal types
- **Perturbation formula is hardcoded** to z^2+c -- new types with different formulas need a new perturbation strategy
- **FractalRenderer** is a 740-line monolith mixing rendering strategy selection, 3 render paths, perturbation math, reference orbit computation, and color mapping
- **No test framework** -- custom assertions without setup/teardown, parameterization, or reporting
- **No build tool** -- raw javac means no dependency management, no incremental compilation
- **Duplicated code** in JSON parsing (3 copies) and gradient construction (4 copies)
- **Index-based type selection** in FractalTool combo box
- **instanceof chains** in ToolBar for stroke/fill dispatch

---

## 8. Relevant to future_work.md

### Escape-time fractals (Burning Ship, Tricorn, Magnet)

These would fit into the existing pipeline IF the FractalType abstraction is reworked. Specifically:
- `iterate()` / `iterateBig()` -- straightforward to add per future_work.md
- Perturbation theory -- each type needs its own perturbation formula, so `perturbIterate()` can't stay as a single hardcoded method
- Reference orbit -- each type's reference orbit computation differs
- UI combo boxes and menus need updating (both FractalTool.java and DrawingApp.java)

### IFS fractals (Sierpinski, Koch)

Completely different rendering paradigm. These don't produce iteration counts per pixel, so they can't use FractalRenderer at all. Would need a separate rendering path.

### 3D fractals (Mandelbulb, Mandelbox)

Would need ray-marching/distance-estimation renderer. Entirely separate pipeline.

### Memory hygiene

The renderer allocates `new int[width*height]` arrays on every render call (L216-217, L301, L515-516). Reference orbit arrays are `new double[maxIterations+1]` (L334-335). ExecutorService is created and destroyed per render (L371, L436, L551). Pre-allocation is feasible but requires lifecycle management.

### Progress indicator

`bigDecimalCompletedRows` and `bigDecimalTotalRows` are already tracked (L52-53) and `getBigDecimalProgress()` exists (L136-139), but nothing in the UI reads this value during rendering. The SwingWorker doesn't publish intermediate progress.

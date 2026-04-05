# Test Framework Refactor Guide

## Current State

All 521 assertions live in a single file: `FractalRenderTest.java` (3700+ lines). It uses a hand-rolled `check(name, boolean)` method, a monolithic `main()`, and reports only PASS/FAIL with no expected-vs-actual output. Tests of layers, fills, tools, gradients, undo, dock panels, FDP serialization, and animations are all mixed in alongside fractal rendering tests.

The benchmark suite (`FractalBenchmark.java`) is separate and CLI-driven, which is fine.

## Goals

1. **Categorize tests by size** — small (unit), medium (integration/UI), large (render/benchmark)
2. **Split into focused test classes** — one per domain, not one monolith
3. **Adopt JUnit 5** — proper assertions, `@Test` annotations, `@BeforeEach`/`@AfterEach`, `@Tag` for filtering
4. **File format parser tests** as their own suite — FDP, JSON location, gradient files
5. **Better failure reporting** — `assertEquals(expected, actual)` instead of `check(name, bool)`
6. **Test runner script** that can run all, or filter by size/tag

---

## Test Sizes

### Small (Unit)
Fast, no I/O, no UI components, no rendering. Test a single class/method in isolation.

**Target runtime**: < 50ms per test, entire small suite < 2 seconds.

| Test Class | What it covers | Current tests to move |
|---|---|---|
| `FillProviderTest` | Each fill provider creates non-null Paint, respects parameters | `testSolidFill`, `testGradientFill`, `testCustomGradientFill`, `testCheckerboardFill`, `testDiagonalStripeFill`, `testCrosshatchFill`, `testDotGridFill`, `testHorizontalStripeFill`, `testNoiseFill`, `testFillRegistry` |
| `StrokeStyleTest` | Enum values, `createStroke()` returns valid Stroke for each style | `testStrokeStyleEnum`, `testStrokeStyleCreateStroke` |
| `ColorGradientTest` | Interpolation, add/remove stops, fromBaseColor, copy, copyFrom | `testColorGradientInterpolation`, `testColorGradientAddRemoveStops`, `testColorGradientFromBaseColor`, `testColorGradientCopyConstructor`, `testColorGradientCopyFrom`, `testSharedGradient` |
| `LayerManagerTest` | Add/remove/move/merge/flatten layers, active index tracking, `moveLayer()`, visibility, opacity, blend mode, reset | `testLayerSystem`, `testBug1InvisibleLayerDrawingBlocked`, `testBug6LayerDragReorder` |
| `UndoManagerTest` | Save/undo/redo, redo cleared on new state, compaction, multi-layer | `testUndoManagerBasic`, `testUndoManagerRedoClearedOnNewState`, `testUndoManagerCompaction`, `testUndoManagerMultiLayer` |
| `FractalTypeTest` | Each type's `iterate()` contract, enum registry, type selection round-trip | `testFractalTypeEnumContract`, `testBurningShipIteration`, `testTricornIteration`, `testMagnetTypeIIteration`, `testTypeSelectionRoundTrip` |
| `ViewportCalculatorTest` | Aspect ratio, coordinate mapping | `testViewportCalculatorAspectRatio` |
| `ColorMapperTest` | LUT generation, deterministic color mapping | `testColorMapperLUT`, `testColorMappingDeterministic` |
| `QuadTreeTest` | Lookup, insert, large-scale operations | `testQuadTreeCacheContract`, `testQuadTreeLookupFull`, `testQuadTreeLargeScale` |
| `BlendCompositeTest` | All blend modes produce valid output | `testBlendCompositeAllModes` |
| `ToolSettingsTest` | Fill dropdown None entry, combo initialization from current fill, tool capabilities, tool names, default stroke sizes | `testBug4FillNoneDropdown`, `testToolDefaultStrokeSizes`, `testToolNames`, `testToolCapabilities` |
| `ZoomMathTest` | Fractal scroll-wheel zoom viewport math (BigDecimal arithmetic only, no rendering) | `testBug2FractalScrollWheelZoom` |

### Medium (Integration / UI)
May instantiate Swing components, do file I/O, or test multiple classes together. No heavy rendering.

**Target runtime**: < 500ms per test, entire medium suite < 15 seconds.

| Test Class | What it covers | Current tests to move |
|---|---|---|
| `DrawingToolTest` | Each tool draws pixels on a BufferedImage via simulated mouse events, stroke style integration | `testPencilToolDrawsOnCanvas`, `testLineToolDrawsOnCanvas`, `testRectangleToolDrawsOutline`, `testRectangleToolFilled`, `testOvalToolDrawsOnCanvas`, `testEraserToolErases`, `testFillToolFloodFill`, `testPencilToolStrokeStyle`, `testLineToolStrokeStyle` |
| `FdpSerializerTest` | Round-trip save/load for single layer, multi-layer, fractal state, gradient, layer properties, backward compat, BigDecimal precision, re-save | `testFdpRoundTripSingleLayer`, `testFdpRoundTripMultiLayer`, `testFdpRoundTripFractalState`, `testFdpRoundTripGradient`, `testFdpRoundTripLayerProperties`, `testFdpBackwardCompat`, `testFdpRoundTripBigDecimalPrecision`, `testBug5SaveFileTracking` |
| `GradientFileTest` | Gradient save/load to `.grd` files, no duplicate defaults in src/ | `testColorGradientSaveLoad`, `testGradientDefaultConsistency`, `testBug3NoDuplicateGradientDefaults` |
| `JsonLocationTest` | JSON parse round-trip, edge cases, load Mandelbrot not overridden by Julia constant | `testJsonParseRoundTrip`, `testJsonParseEdgeCases`, `testLoadMandelbrotNotOverriddenByJuliaConstant` |
| `AviWriterTest` | AVI file creation, multi-frame writing | `testAviWriterCreatesValidFile`, `testAviWriterMultipleFrames` |
| `AnimationTest` | Recolor from iters, palette cycle shift/wrap/match, iteration animator frames/cancel/total, render-to-files | `testRecolorFromIters`, `testRecolorDifferentGradientDiffers`, `testPaletteCycleShiftGradient`, `testPaletteCycleFullRotationWraps`, `testPaletteCycleRenderToFiles`, `testIterationAnimatorFramesDiffer`, `testIterationAnimatorRenderToFiles`, `testIterationAnimatorCancel`, `testIterationAnimatorTotalFrames`, `testPaletteCycleRecolorMatchesDirectRender` |
| `ScreensaverTest` | Controller lifecycle, panel transitions, location finding | `testScreensaverControllerLifecycle`, `testScreensaverPanelTransition`, `testScreensaverFindLocationNotNull` |
| `DockSystemTest` | DockManager, DockablePanel show/hide/dock | `testDockManagerAndDockablePanel` |

### Large (Render / Benchmark)
Full fractal renders, golden-value checksums, performance tests. Slow by nature.

**Target runtime**: < 5s per test, entire large suite < 60 seconds.

| Test Class | What it covers | Current tests to move |
|---|---|---|
| `FractalRenderTest` | Double/BigDecimal/Perturbation determinism, golden checksums, deep zoom, mode switching, Julia rendering, iteration preservation, partial render bug, prev-render cache | `testDoubleRenderDeterministic`, `testBigDecimalMatchesPerturbation`, `testDeeperZoomAllModes`, `testPerturbationInteriorPixels`, `testDoubleProducesBlockyAtDeepZoom`, `testDoubleModeShallowZoom`, `testJuliaSetRenders`, `testIterationCountsPreservedAcrossZoom`, `testRenderModeSwitch`, `testMandelbrotDoubleGolden`, `testJuliaDoubleGolden`, `testMandelbrotPerturbationGolden`, `testMandelbrotBigDecimalGolden`, `testSevenPointedStarDeepZoom`, `testPartialRenderBug`, `testPrevRenderCacheAtShallowZoom`, `testPrevRenderCacheAtDeepZoom` |
| `FractalTypeRenderTest` | All fractal types render valid images in all modes | `testBurningShipRendersValidImage`, `testTricornRendersValidImage`, `testMagnetRendersValidImage`, `testNewTypesInAllRenderModes` |
| `PruningTest` | Interior pruning identical output, spiky edges, interior detection, speedup measurement | `testPruningIdenticalOutput`, `testPruningIdenticalAtSpikyEdges`, `testPruningMandelbrotInterior`, `testPruningSpeedupOnInterior` |
| `PixelGuessingTest` | Near-identical output, on/off toggle, filament region | `testPixelGuessingNearIdentical`, `testPixelGuessingOnOffToggle`, `testPixelGuessingFilamentRegion` |
| `ZoomAnimationRenderTest` | Interpolation correctness, frame rendering | `testZoomAnimatorInterpolation`, `testZoomAnimatorRenderFrames` |
| `TerrainRenderTest` | 3D terrain rendering | `testTerrainRenderer` |

`FractalBenchmark.java` stays as-is — it's a CLI tool, not a test suite.

---

## Directory Layout

```
src/
  com/seanick80/drawingapp/
    ...production code...

test/
  com/seanick80/drawingapp/
    small/
      FillProviderTest.java
      StrokeStyleTest.java
      ColorGradientTest.java
      LayerManagerTest.java
      UndoManagerTest.java
      FractalTypeTest.java
      ViewportCalculatorTest.java
      ColorMapperTest.java
      QuadTreeTest.java
      BlendCompositeTest.java
      ToolSettingsTest.java
      ZoomMathTest.java
    medium/
      DrawingToolTest.java
      FdpSerializerTest.java
      GradientFileTest.java
      JsonLocationTest.java
      AviWriterTest.java
      AnimationTest.java
      ScreensaverTest.java
      DockSystemTest.java
    large/
      FractalRenderTest.java
      FractalTypeRenderTest.java
      PruningTest.java
      PixelGuessingTest.java
      ZoomAnimationRenderTest.java
      TerrainRenderTest.java
    TestHelpers.java          # shared helpers: newRenderer(), gradient(), whiteImage(), etc.

lib/
  protobuf-java-4.29.3.jar
  junit-platform-console-standalone-1.11.4.jar   # single JAR, includes JUnit 5 engine + launcher
```

The `test/` source tree is separate from `src/` — tests are not packaged with the app. The package structure mirrors `src/` with size-based sub-packages.

---

## JUnit 5 Setup

### Dependency

A single JAR: `junit-platform-console-standalone-1.11.4.jar` (download from Maven Central). This bundles the JUnit Jupiter API, engine, and console launcher — no Maven/Gradle needed.

### Test Anatomy

```java
package com.seanick80.drawingapp.small;

import com.seanick80.drawingapp.layers.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("small")
class LayerManagerTest {

    private LayerManager lm;

    @BeforeEach
    void setUp() {
        lm = new LayerManager(100, 100);
    }

    @Test
    void addLayer_increasesCount() {
        assertEquals(1, lm.getLayerCount());
        lm.addLayer();
        assertEquals(2, lm.getLayerCount());
    }

    @Test
    void moveLayer_updatesActiveIndex() {
        lm.addLayer().setName("A");
        lm.addLayer().setName("B");
        lm.setActiveIndex(2);
        lm.moveLayer(2, 0);
        assertEquals(0, lm.getActiveIndex());
        assertEquals("B", lm.getLayer(0).getName());
    }

    @Test
    void removeLayer_keepsAtLeastOne() {
        lm.removeLayer(0);
        assertEquals(1, lm.getLayerCount());
    }
}
```

### Key JUnit 5 Features to Use

- `assertEquals(expected, actual)` / `assertNotEquals` — shows both values on failure
- `assertTrue(condition, "message")` — descriptive failure messages
- `assertThrows(Exception.class, () -> ...)` — exception testing
- `assertArrayEquals` — for pixel arrays
- `@BeforeEach` / `@AfterEach` — setup/teardown per test
- `@Tag("small")` / `@Tag("medium")` / `@Tag("large")` — size filtering
- `@TempDir` — JUnit-managed temp directory for file I/O tests (auto-cleaned)
- `@Timeout(5)` — fail if a test hangs (useful for render tests)
- `@DisplayName("human-readable name")` — optional, for complex test names

---

## Test Runner Script

Replace `test.sh` with a script that supports filtering:

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Build classpath from all JARs in lib/
CP="out"
for jar in lib/*.jar; do
    [ -f "$jar" ] && CP="$CP;$jar"
done

# Compile sources + tests
echo "Compiling..."
javac -d out -cp "$CP" -sourcepath "src;test" \
    $(find src test -name '*.java') 2>&1

# Determine which tags to run
TAG_FILTER=""
case "${1:-all}" in
    small)   TAG_FILTER="--include-tag=small" ;;
    medium)  TAG_FILTER="--include-tag=medium" ;;
    large)   TAG_FILTER="--include-tag=large" ;;
    parser)  TAG_FILTER="--include-tag=parser" ;;
    all)     TAG_FILTER="" ;;
    *)       echo "Usage: $0 [small|medium|large|parser|all]"; exit 1 ;;
esac

echo "Running tests..."
java -ea -jar lib/junit-platform-console-standalone-1.11.4.jar \
    --class-path out \
    --scan-class-path \
    $TAG_FILTER \
    --details=verbose
```

Usage:
```bash
./test.sh           # run everything
./test.sh small     # unit tests only (~2s)
./test.sh medium    # integration tests (~15s)
./test.sh large     # render tests (~60s)
./test.sh parser    # file format tests only
```

---

## Migration Strategy

### Phase 1: Infrastructure
1. Download `junit-platform-console-standalone-1.11.4.jar` to `lib/`
2. Create `test/` directory structure
3. Extract `TestHelpers.java` from the bottom of `FractalRenderTest.java` (shared helper methods)
4. Update `test.sh` to use JUnit console launcher
5. Update `build.sh` to compile `test/` sources alongside `src/`

### Phase 2: Migrate Small Tests
Move the simplest, most isolated tests first. Each migration step:
1. Create the new test class with `@Tag("small")`
2. Convert `check("name", condition)` calls to `assertEquals` / `assertTrue`
3. Remove the test method from `FractalRenderTest.java`
4. Run both old and new suites — verify same pass count

Order: `FillProviderTest` → `StrokeStyleTest` → `ColorGradientTest` → `LayerManagerTest` → `UndoManagerTest` → remaining small tests.

### Phase 3: Migrate Medium Tests
Same process for integration tests. File I/O tests should use `@TempDir`:
```java
@Test
void fdpRoundTrip(@TempDir Path tempDir) throws Exception {
    File file = tempDir.resolve("test.fdp").toFile();
    // ...
}
```

Order: `DrawingToolTest` → `FdpSerializerTest` → `GradientFileTest` → `JsonLocationTest` → remaining medium tests.

### Phase 4: Migrate Large Tests
Render tests last — they're the most complex and slowest. Add `@Timeout` annotations:
```java
@Test
@Timeout(10)
void mandelbrotDoubleGolden() {
    // ...
}
```

### Phase 5: Delete FractalRenderTest.java
Once all tests are migrated, delete the original monolith and update CLAUDE.md.

---

## File Format Parser Tests

Tests tagged `@Tag("parser")` in addition to their size tag, so they can be run independently:

```java
@Tag("medium")
@Tag("parser")
class FdpSerializerTest { ... }

@Tag("medium")
@Tag("parser")
class GradientFileTest { ... }

@Tag("medium")
@Tag("parser")
class JsonLocationTest { ... }
```

This lets `./test.sh parser` run all format-related tests across sizes.

---

## Conventions

- **Test class naming**: `<ClassUnderTest>Test.java`
- **Test method naming**: `methodName_condition_expectedResult` or just descriptive camelCase
- **One assertion concept per test** where practical — but multiple asserts on the same logical check are fine
- **No test interdependence** — each test sets up its own state
- **Temp files via `@TempDir`** — no manual cleanup needed
- **Tags on the class level** for size, method level for specific categories like `parser`

## Counts After Migration

| Size | Test classes | Approx. test methods |
|---|---|---|
| Small | 12 | ~55 |
| Medium | 8 | ~40 |
| Large | 6 | ~25 |
| **Total** | **26** | **~120** |

(Individual `check()` assertions within methods will become multiple `assert*()` calls — the method count may change slightly as some large methods with many checks get split into focused tests.)

# Test Framework Refactor Guide

## Current State

All 521 assertions live in a single file: `FractalRenderTest.java` (3700+ lines). It uses a hand-rolled `check(name, boolean)` method, a monolithic `main()`, and reports only PASS/FAIL with no expected-vs-actual output. Tests of layers, fills, tools, gradients, undo, dock panels, FDP serialization, and animations are all mixed in alongside fractal rendering tests.

The benchmark suite (`FractalBenchmark.java`) is separate and CLI-driven, which is fine.

## Goals

1. **Categorize tests by size** — small (unit), medium (integration/UI), large (render/benchmark)
2. **Split into focused test classes** — one per domain, colocated with the source it tests
3. **Adopt JUnit 5** — proper assertions, `@Test` annotations, `@BeforeEach`/`@AfterEach`, tag-based filtering
4. **Custom `@TestSize` annotation** — type-safe enum for small/medium/large instead of raw `@Tag` strings
5. **File format parser tests** as their own tag — FDP, JSON location, gradient files
6. **Better failure reporting** — `assertEquals(expected, actual)` instead of `check(name, bool)`
7. **Test runner script** that can run all, or filter by size/tag

---

## Custom @TestSize Annotation

Instead of using raw `@Tag("small")` strings (typo-prone, no IDE autocomplete), define a custom annotation backed by an enum:

```java
package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/**
 * Declares the test size for filtering. Applied per-method (or per-class as a default).
 * Maps to JUnit @Tag values so the test runner can filter by size.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestSize {
    Size value();

    enum Size {
        /** Fast, no I/O, no UI, no rendering. < 50ms per test. */
        SMALL,
        /** File I/O, Swing components, multi-class integration. < 500ms per test. */
        MEDIUM,
        /** Full fractal renders, golden checksums, performance. < 5s per test. */
        LARGE
    }
}
```

To make this work with JUnit's `--include-tag` filtering, we also need a small `TestSizeExtension` that registers the size as a JUnit tag at runtime:

```java
package com.seanick80.drawingapp;

import org.junit.jupiter.api.extension.*;
import java.util.*;

/**
 * JUnit 5 extension that converts @TestSize annotations into JUnit tags.
 * Register globally via META-INF/services or per-class with @ExtendWith.
 */
public class TestSizeExtension implements TestInstancePostProcessor {
    // Tags are resolved from @TestSize via a custom AnnotationConsumer —
    // but the simpler approach is a composed annotation (see below).
}
```

Actually, the cleanest JUnit 5 approach is **composed annotations** — each size is its own annotation that includes `@Tag`:

```java
package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("small")
public @interface SmallTest {}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("medium")
public @interface MediumTest {}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("large")
public @interface LargeTest {}
```

**However**, to satisfy the request for a single `@TestSize` annotation with an enum value, we use a hybrid: the `@TestSize` annotation plus a JUnit extension that dynamically adds the corresponding tag. Here's the recommended approach:

### Option A: Composed annotations (simplest, recommended)

Three annotations — `@SmallTest`, `@MediumTest`, `@LargeTest` — each meta-annotated with `@Tag`. No extension needed. JUnit filtering works out of the box.

```java
// Usage
@Test @SmallTest
void addLayer_increasesCount() { ... }

@Test @LargeTest @Timeout(10)
void mandelbrotDoubleGolden() { ... }
```

### Option B: Single @TestSize enum annotation (requires extension)

A single `@TestSize(Size.SMALL)` annotation that requires a custom `Extension` to bridge to JUnit's tag system. More elegant API, slightly more infrastructure.

```java
// Usage
@Test @TestSize(Size.SMALL)
void addLayer_increasesCount() { ... }

@Test @TestSize(Size.LARGE) @Timeout(10)
void mandelbrotDoubleGolden() { ... }
```

**This guide uses Option A** (composed annotations) for simplicity — no extension registration needed, and each annotation is a single concise token. The test runner script filters identically either way.

---

## Test Sizes

Size is selected by annotation on each test method or class. A single test class may contain methods of different sizes.

### @SmallTest (Unit)
Fast, no I/O, no UI components, no rendering. Test a single class/method in isolation.

**Target runtime**: < 50ms per test, entire small suite < 2 seconds.

### @MediumTest (Integration / UI)
May instantiate Swing components, do file I/O, or test multiple classes together. No heavy rendering.

**Target runtime**: < 500ms per test, entire medium suite < 15 seconds.

### @LargeTest (Render / Benchmark)
Full fractal renders, golden-value checksums, performance tests. Slow by nature.

**Target runtime**: < 5s per test, entire large suite < 60 seconds.

---

## Test File Placement

Test classes live alongside the source files they test. The naming convention is `<ClassName>Test.java`:

```
src/com/seanick80/drawingapp/
  SmallTest.java                    # @SmallTest composed annotation
  MediumTest.java                   # @MediumTest composed annotation
  LargeTest.java                    # @LargeTest composed annotation
  TestHelpers.java                  # shared: newRenderer(), gradient(), whiteImage(), etc.
  UndoManager.java
  UndoManagerTest.java
  AviWriter.java
  AviWriterTest.java
  layers/
    LayerManager.java
    LayerManagerTest.java
    BlendComposite.java
    BlendCompositeTest.java
  fills/
    FillRegistry.java
    FillProviderTest.java           # tests all fill providers
  tools/
    StrokeStyle.java
    StrokeStyleTest.java
    ToolSettingsBuilder.java
    ToolSettingsTest.java           # fill dropdown, tool capabilities
    DrawingToolTest.java            # pencil, line, rect, oval, eraser, fill tool
  gradient/
    ColorGradient.java
    ColorGradientTest.java
    GradientFileTest.java           # .grd file save/load
  fractal/
    FractalRenderer.java
    FractalRenderTest.java          # render determinism, golden checksums (large)
    FractalTypeTest.java            # iterate() contracts (small)
    FractalTypeRenderTest.java      # all types in all modes (large)
    ViewportCalculator.java
    ViewportCalculatorTest.java
    FractalColorMapper.java
    ColorMapperTest.java
    QuadTree.java
    QuadTreeTest.java
    PruningTest.java                # interior pruning (large)
    PixelGuessingTest.java          # pixel guessing (large)
    ZoomAnimationTest.java          # interpolation + frames
    AnimationTest.java              # palette cycle, iteration animator
    ScreensaverTest.java
    TerrainRenderTest.java          # 3D terrain (large)
    FractalJsonUtil.java
    JsonLocationTest.java           # JSON parse round-trip
  project/
    FdpSerializer.java
    FdpSerializerTest.java          # FDP round-trip, backward compat
  dock/
    DockManager.java
    DockSystemTest.java
```

---

## Test Class to Source Mapping

| Test Class | Location (next to) | Size | Extra Tags | Current tests to move |
|---|---|---|---|---|
| `FillProviderTest` | `fills/` | small | | `testFillRegistry`, `testSolidFill`, `testGradientFill`, `testCustomGradientFill`, `testCheckerboardFill`, `testDiagonalStripeFill`, `testCrosshatchFill`, `testDotGridFill`, `testHorizontalStripeFill`, `testNoiseFill` |
| `StrokeStyleTest` | `tools/` | small | | `testStrokeStyleEnum`, `testStrokeStyleCreateStroke` |
| `ColorGradientTest` | `gradient/` | small | | `testColorGradientInterpolation`, `testColorGradientAddRemoveStops`, `testColorGradientFromBaseColor`, `testColorGradientCopyConstructor`, `testColorGradientCopyFrom`, `testSharedGradient` |
| `LayerManagerTest` | `layers/` | small | | `testLayerSystem`, `testBug1InvisibleLayerDrawingBlocked`, `testBug6LayerDragReorder` |
| `UndoManagerTest` | root | small | | `testUndoManagerBasic`, `testUndoManagerRedoClearedOnNewState`, `testUndoManagerCompaction`, `testUndoManagerMultiLayer` |
| `FractalTypeTest` | `fractal/` | small | | `testFractalTypeEnumContract`, `testBurningShipIteration`, `testTricornIteration`, `testMagnetTypeIIteration`, `testTypeSelectionRoundTrip` |
| `ViewportCalculatorTest` | `fractal/` | small | | `testViewportCalculatorAspectRatio` |
| `ColorMapperTest` | `fractal/` | small | | `testColorMapperLUT`, `testColorMappingDeterministic` |
| `QuadTreeTest` | `fractal/` | small | | `testQuadTreeCacheContract`, `testQuadTreeLookupFull`, `testQuadTreeLargeScale` |
| `BlendCompositeTest` | `layers/` | small | | `testBlendCompositeAllModes` |
| `ToolSettingsTest` | `tools/` | small | | `testBug4FillNoneDropdown`, `testToolDefaultStrokeSizes`, `testToolNames`, `testToolCapabilities` |
| `DrawingToolTest` | `tools/` | medium | | `testPencilToolDrawsOnCanvas`, `testLineToolDrawsOnCanvas`, `testRectangleToolDrawsOutline`, `testRectangleToolFilled`, `testOvalToolDrawsOnCanvas`, `testEraserToolErases`, `testFillToolFloodFill`, `testPencilToolStrokeStyle`, `testLineToolStrokeStyle` |
| `FdpSerializerTest` | `project/` | medium | `parser` | `testFdpRoundTripSingleLayer`, `testFdpRoundTripMultiLayer`, `testFdpRoundTripFractalState`, `testFdpRoundTripGradient`, `testFdpRoundTripLayerProperties`, `testFdpBackwardCompat`, `testFdpRoundTripBigDecimalPrecision`, `testBug5SaveFileTracking` |
| `GradientFileTest` | `gradient/` | medium | `parser` | `testColorGradientSaveLoad`, `testGradientDefaultConsistency`, `testBug3NoDuplicateGradientDefaults` |
| `JsonLocationTest` | `fractal/` | medium | `parser` | `testJsonParseRoundTrip`, `testJsonParseEdgeCases`, `testLoadMandelbrotNotOverriddenByJuliaConstant` |
| `AviWriterTest` | root | medium | | `testAviWriterCreatesValidFile`, `testAviWriterMultipleFrames` |
| `AnimationTest` | `fractal/` | medium | | `testRecolorFromIters`, `testRecolorDifferentGradientDiffers`, `testPaletteCycleShiftGradient`, `testPaletteCycleFullRotationWraps`, `testPaletteCycleRenderToFiles`, `testIterationAnimatorFramesDiffer`, `testIterationAnimatorRenderToFiles`, `testIterationAnimatorCancel`, `testIterationAnimatorTotalFrames`, `testPaletteCycleRecolorMatchesDirectRender` |
| `ScreensaverTest` | `fractal/` | medium | | `testScreensaverControllerLifecycle`, `testScreensaverPanelTransition`, `testScreensaverFindLocationNotNull` |
| `DockSystemTest` | `dock/` | medium | | `testDockManagerAndDockablePanel` |
| `FractalRenderTest` | `fractal/` | large | | `testDoubleRenderDeterministic`, `testBigDecimalMatchesPerturbation`, `testDeeperZoomAllModes`, `testPerturbationInteriorPixels`, `testDoubleProducesBlockyAtDeepZoom`, `testDoubleModeShallowZoom`, `testJuliaSetRenders`, `testIterationCountsPreservedAcrossZoom`, `testRenderModeSwitch`, `testMandelbrotDoubleGolden`, `testJuliaDoubleGolden`, `testMandelbrotPerturbationGolden`, `testMandelbrotBigDecimalGolden`, `testSevenPointedStarDeepZoom`, `testPartialRenderBug`, `testPrevRenderCacheAtShallowZoom`, `testPrevRenderCacheAtDeepZoom`, `testBug2FractalScrollWheelZoom` |
| `FractalTypeRenderTest` | `fractal/` | large | | `testBurningShipRendersValidImage`, `testTricornRendersValidImage`, `testMagnetRendersValidImage`, `testNewTypesInAllRenderModes` |
| `PruningTest` | `fractal/` | large | | `testPruningIdenticalOutput`, `testPruningIdenticalAtSpikyEdges`, `testPruningMandelbrotInterior`, `testPruningSpeedupOnInterior` |
| `PixelGuessingTest` | `fractal/` | large | | `testPixelGuessingNearIdentical`, `testPixelGuessingOnOffToggle`, `testPixelGuessingFilamentRegion` |
| `ZoomAnimationTest` | `fractal/` | large | | `testZoomAnimatorInterpolation`, `testZoomAnimatorRenderFrames` |
| `TerrainRenderTest` | `fractal/` | large | | `testTerrainRenderer` |

`FractalBenchmark.java` stays as-is — it's a CLI tool, not a test suite.

---

## JUnit 5 Setup

### Dependency

A single JAR: `junit-platform-console-standalone-1.11.4.jar` (download from Maven Central). This bundles the JUnit Jupiter API, engine, and console launcher — no Maven/Gradle needed.

### Composed Annotations

Three files in the root package (`src/com/seanick80/drawingapp/`):

```java
// SmallTest.java
package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/** Unit test — no I/O, no UI, no rendering. < 50ms. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("small")
public @interface SmallTest {}
```

```java
// MediumTest.java — same pattern with @Tag("medium")
/** Integration test — file I/O, Swing, multi-class. < 500ms. */

// LargeTest.java — same pattern with @Tag("large")
/** Render test — full fractal renders, golden checksums. < 5s. */
```

### Test Anatomy

```java
package com.seanick80.drawingapp.layers;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LayerManagerTest {

    private LayerManager lm;

    @BeforeEach
    void setUp() {
        lm = new LayerManager(100, 100);
    }

    @Test @SmallTest
    void addLayer_increasesCount() {
        assertEquals(1, lm.getLayerCount());
        lm.addLayer();
        assertEquals(2, lm.getLayerCount());
    }

    @Test @SmallTest
    void moveLayer_updatesActiveIndex() {
        lm.addLayer().setName("A");
        lm.addLayer().setName("B");
        lm.setActiveIndex(2);
        lm.moveLayer(2, 0);
        assertEquals(0, lm.getActiveIndex());
        assertEquals("B", lm.getLayer(0).getName());
    }

    @Test @SmallTest
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
- `@SmallTest` / `@MediumTest` / `@LargeTest` — size filtering per method
- `@Tag("parser")` — secondary category tag (used alongside a size annotation)
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

# Compile sources (tests live alongside source)
echo "Compiling..."
javac -d out -cp "$CP" -sourcepath src \
    $(find src -name '*.java') 2>&1

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
2. Create `SmallTest.java`, `MediumTest.java`, `LargeTest.java` composed annotations
3. Extract `TestHelpers.java` from the bottom of `FractalRenderTest.java` (shared helper methods)
4. Update `test.sh` to use JUnit console launcher
5. Update `build.sh` to include JUnit JAR in classpath

### Phase 2: Migrate Small Tests
Move the simplest, most isolated tests first. Each migration step:
1. Create the new test class next to the source file it tests
2. Annotate each method with `@Test @SmallTest`
3. Convert `check("name", condition)` calls to `assertEquals` / `assertTrue`
4. Remove the test method from the old `FractalRenderTest.java`
5. Run both old and new suites — verify same pass count

Order: `FillProviderTest` → `StrokeStyleTest` → `ColorGradientTest` → `LayerManagerTest` → `UndoManagerTest` → remaining small tests.

### Phase 3: Migrate Medium Tests
Same process for integration tests. File I/O tests should use `@TempDir`:
```java
@Test @MediumTest @Tag("parser")
void fdpRoundTrip(@TempDir Path tempDir) throws Exception {
    File file = tempDir.resolve("test.fdp").toFile();
    // ...
}
```

Order: `DrawingToolTest` → `FdpSerializerTest` → `GradientFileTest` → `JsonLocationTest` → remaining medium tests.

### Phase 4: Migrate Large Tests
Render tests last — they're the most complex and slowest. Add `@Timeout` annotations:
```java
@Test @LargeTest @Timeout(10)
void mandelbrotDoubleGolden() {
    // ...
}
```

### Phase 5: Delete old FractalRenderTest.java
Once all tests are migrated, delete the original monolith and update CLAUDE.md.

---

## File Format Parser Tests

Tests tagged `@Tag("parser")` in addition to their size annotation, so they can be run independently:

```java
// In project/FdpSerializerTest.java
@Test @MediumTest @Tag("parser")
void roundTripSingleLayer() { ... }

// In gradient/GradientFileTest.java
@Test @MediumTest @Tag("parser")
void saveLoadGradient() { ... }

// In fractal/JsonLocationTest.java
@Test @MediumTest @Tag("parser")
void parseRoundTrip() { ... }
```

This lets `./test.sh parser` run all format-related tests across sizes.

---

## Conventions

- **Test file placement**: next to the source file it tests, in the same package
- **Test class naming**: `<ClassUnderTest>Test.java` or `<Feature>Test.java`
- **Test method naming**: `methodName_condition_expectedResult` or just descriptive camelCase
- **Size annotation on each method** — `@SmallTest`, `@MediumTest`, or `@LargeTest`
- **One assertion concept per test** where practical — but multiple asserts on the same logical check are fine
- **No test interdependence** — each test sets up its own state
- **Temp files via `@TempDir`** — no manual cleanup needed

## Counts After Migration

| Size | Test classes | Approx. test methods |
|---|---|---|
| @SmallTest | 11 | ~50 |
| @MediumTest | 8 | ~45 |
| @LargeTest | 6 | ~25 |
| **Total** | **25** | **~120** |

(Individual `check()` assertions within methods will become multiple `assert*()` calls — the method count may change slightly as some large methods with many checks get split into focused tests.)

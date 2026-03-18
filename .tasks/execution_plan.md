# Execution Plan: Top 5 Improvements

**Date:** 2026-03-18
**Scope:** Prioritized improvements with refactoring, tests, and implementation for top 2

---

## Prioritized Top 5 Improvements

### 1. New Escape-Time Fractal Types (Burning Ship, Tricorn, Magnet I)
**Priority: Highest** — Directly extends the app's core value with minimal new infrastructure. These fractals use the same escape-time paradigm as Mandelbrot/Julia, so they fit the existing rendering pipeline. High user-visible impact.

### 2. Render Progress Indicator
**Priority: High** — Infrastructure already exists (`bigDecimalCompletedRows`, `getBigDecimalProgress()`), just disconnected from UI. Deep zoom renders can take minutes with zero feedback. Low effort, high UX impact.

### 3. Memory Hygiene / Pre-allocation
**Priority: Medium-High** — Performance win across all render modes. Pre-allocate `int[]` buffers, reference orbit arrays, and reuse ExecutorService. Especially impactful for perturbation renders at deep zoom. Required foundation for animation work.

### 4. IFS Fractal Rendering (Sierpinski Triangle/Carpet, Koch Snowflake)
**Priority: Medium** — Compelling new capability but requires a separate rendering paradigm (recursive geometry vs escape-time iteration). Larger architectural investment.

### 5. Animation Support (Iteration, Zoom, Palette Cycle)
**Priority: Medium-Low** — Most ambitious feature. Requires memory hygiene (#3) as prerequisite, plus frame sequencing, video encoding, and UI controls. Best tackled after the foundation is solid.

---

## Core Refactoring Required

### R1. FractalType: Enum → Interface + Registry
**Needed by:** #1 (escape-time types), #4 (IFS types)
**Current problem:** `FractalType` is a closed enum with 2 values. Adding a type requires modifying the enum AND updating 15+ call sites with `isJulia` branching.
**Target design:**
- Extract `FractalType` to an **interface** with:
  - `String getName()`
  - `int iterate(double cx, double cy, int maxIter)` — standard double iteration
  - `int iterateBig(BigDecimal cx, BigDecimal cy, int maxIter, MathContext mc)` — arbitrary precision
  - `default boolean hasJuliaVariant()` → false
  - `default FractalType juliaVariant(double cr, double ci)` → throws
  - `default boolean supportsPerturbation()` → false
  - `default PerturbationStrategy getPerturbationStrategy()` → null
- Create `FractalTypeRegistry` (following the `FillRegistry` pattern) for dynamic registration
- Implement `MandelbrotType`, `JuliaType`, `BurningShipType`, `TricornType`, `MagnetType` as classes
- The Julia constant moves from FractalRenderer into `JuliaType` (owned by the type, not the renderer)

**Files to modify:**
- `FractalType.java` — rewrite as interface
- `FractalRenderer.java` — remove all `isJulia` branching, delegate to type interface
- `FractalTool.java` — populate combo from registry instead of hardcoded array
- `DrawingApp.java` — populate menu from registry instead of hardcoded radio buttons
- `FractalRenderTest.java` — update type construction
- `FractalBenchmark.java` — update type loading
- `PerturbationEval.java` — update type loading

### R2. Extract Perturbation Strategy
**Needed by:** #1 (each escape-time type has its own perturbation formula)
**Current problem:** `perturbIterate()` hardcodes the z²+c perturbation formula. Reference orbit computation is split into two private methods (`computeReferenceOrbitMandelbrot`/`Julia`).
**Target design:**
- New interface `PerturbationStrategy`:
  - `void computeReferenceOrbit(BigDecimal centerR, BigDecimal centerI, int maxIter, MathContext mc, double[] outZr, double[] outZi)` + returns escape iter
  - `int perturbIterate(double[] refZr, double[] refZi, double dcr, double dci, int refEscapeIter, int maxIter)`
- Mandelbrot and Julia each provide their own strategy
- Burning Ship, Tricorn get their own strategies (different formulas)
- Types that don't support perturbation return `supportsPerturbation() = false`, renderer falls back to BigDecimal

**Files to modify:**
- New: `PerturbationStrategy.java` interface
- New: `MandelbrotPerturbation.java`, `JuliaPerturbation.java`
- `FractalRenderer.java` — delegate perturbation to strategy from type

### R3. Decompose FractalRenderer
**Needed by:** #1, #2, #3, #4
**Current problem:** 740-line monolith mixing render mode selection, 3 render paths, perturbation math, reference orbit computation, and color mapping.
**Target design:**
- Extract `ViewportCalculator` — shared viewport/aspect-ratio math (currently duplicated across render methods)
- Extract `FractalColorMapper` — LUT construction + `colorForIter()`
- Keep `FractalRenderer` as the orchestrator (mode selection, async coordination, cache management)
- Perturbation math moves to `PerturbationStrategy` (R2)

**Files to modify:**
- `FractalRenderer.java` — extract classes, simplify to orchestrator
- New: `ViewportCalculator.java`
- New: `FractalColorMapper.java`

### R4. Deduplicate JSON Parsing and Gradient Construction
**Needed by:** All improvements (reduces maintenance burden)
**Current problem:** `parseJson()` copy-pasted in 3 files, `createGradient()` in 4 files.
**Target design:**
- New utility: `FractalJsonUtil.parseJson(String)` in `fractal/` package
- Move default gradient to `ColorGradient.fractalDefault()` static factory
**Files to modify:**
- New: `FractalJsonUtil.java`
- `ColorGradient.java` — add `fractalDefault()`
- `FractalTool.java` — use shared utilities
- `FractalRenderTest.java` — use `ColorGradient.fractalDefault()`
- `FractalBenchmark.java` — use shared utilities
- `PerturbationEval.java` — use shared utilities

### R5. Dynamic UI Population from Registries
**Needed by:** #1, #4
**Current problem:** `FractalTool` combo uses hardcoded `new String[]{"Mandelbrot", "Julia"}` with index-based dispatch. `DrawingApp` menu hardcodes radio buttons.
**Target design:**
- `FractalTool` populates combo from `FractalTypeRegistry.getAll()`
- Selection by name, not index: `registry.getByName(selectedItem)`
- `DrawingApp` fractal menu generates radio buttons from registry
- Adding a new type = implement interface + register → appears in UI automatically

---

## Common Refactoring Themes

Refactorings R1, R2, R3, and R5 all share these patterns:
1. **Interface extraction** — replace concrete/enum with interface + implementations
2. **Registry pattern** — dynamic discovery instead of hardcoded dispatch
3. **Strategy delegation** — renderer delegates to type-owned strategies instead of internal branching

This means they can be sequenced to build on each other and tested with a single consistent approach.

---

## Test Plan

### Existing Test Baseline

The 19 existing assertions in `FractalRenderTest.java` are the **regression safety net**. Every refactoring phase MUST pass all existing tests before proceeding. The custom test harness is adequate for this project's needs — no framework migration needed.

### Phase T1: Pre-Refactoring Integration Tests (run before any changes)

These capture the exact current behavior as golden baselines:

```
T1.1  testMandelbrotDoubleGolden
      — Render Mandelbrot at standard bounds (-2,1,-1.5,1.5), 256 iter, DOUBLE mode
      — Save pixel checksum as golden value
      — Purpose: detect ANY rendering change during refactoring

T1.2  testJuliaDoubleGolden
      — Render Julia at standard bounds, c=-0.7+0.27015i, DOUBLE mode
      — Save pixel checksum
      — Purpose: ensure Julia rendering is unchanged

T1.3  testMandelbrotPerturbationGolden
      — Render at deep zoom location, PERTURBATION mode
      — Save pixel checksum
      — Purpose: ensure perturbation formula refactoring produces identical output

T1.4  testMandelbrotBigDecimalGolden
      — Same deep zoom, BIGDECIMAL mode
      — Save pixel checksum
      — Purpose: ensure BigDecimal path is unchanged

T1.5  testFractalTypeEnumContract
      — For each FractalType value: call iterate() and iterateBig() with known inputs
      — Assert specific expected iteration counts
      — Purpose: pin down the mathematical contract before extracting to interface

T1.6  testColorMappingDeterministic
      — Build LUT from gradient, map known iteration values
      — Assert specific RGB values
      — Purpose: catch color mapping regressions during R3 extraction

T1.7  testQuadTreeCacheContract
      — Insert known values, lookup with tolerance, verify hits/misses
      — Prune and verify surviving entries
      — Purpose: cache behavior must survive R3 decomposition

T1.8  testJsonParseRoundTrip
      — Parse a known JSON string with parseJson()
      — Verify all keys/values match expected
      — Purpose: pin behavior before R4 deduplication

T1.9  testGradientDefaultConsistency
      — Create gradient using the pattern from FractalTool, FractalRenderTest, etc.
      — Verify all 4 copies produce identical Color[] output
      — Purpose: confirm deduplication won't change behavior
```

### Phase T2: Unit Tests for New Abstractions (written alongside refactoring)

```
T2.1  testFractalTypeInterfaceContract
      — Each implementation (Mandelbrot, Julia, BurningShip, Tricorn, Magnet) satisfies:
        - getName() returns non-null, non-empty
        - iterate() returns value in [0, maxIter]
        - iterateBig() returns same value as iterate() for double-representable inputs
        - Known-interior point returns maxIter
        - Known-exterior point returns < maxIter

T2.2  testFractalTypeRegistryOperations
      — register(), getByName(), getAll(), getNames()
      — Duplicate name rejection
      — getByName with unknown name returns null

T2.3  testPerturbationStrategyContract
      — For Mandelbrot: perturbIterate with zero delta returns same as reference escape
      — For Julia: perturbIterate with zero delta returns same as reference escape
      — Known glitch case returns GLITCH_DETECTED
      — Non-supporting types return supportsPerturbation() == false

T2.4  testViewportCalculatorAspectRatio
      — Given bounds and dimensions, verify correct scale and offset
      — Verify aspect ratio correction matches current FractalRenderer behavior

T2.5  testColorMapperLUT
      — MOD mode: verify cyclic wrapping at palette size
      — DIVISION mode: verify linear mapping
      — maxIterations → black

T2.6  testBurningShipIteration
      — Known exterior point (0.5, 0.5): should escape quickly
      — Known interior point: should reach maxIter
      — Verify symmetry properties (asymmetric about real axis, unlike Mandelbrot)

T2.7  testTricornIteration
      — Known points with expected iteration counts
      — Verify conjugation: iterate(x, y) uses conj(z) = (zr, -zi)

T2.8  testMagnetTypeIIteration
      — Known points with expected iteration counts
      — Verify fixed-point convergence (Magnet uses |z-1|² < ε, not |z|² > 4)
```

### Phase T3: Integration Tests for New Fractal Types

```
T3.1  testBurningShipRendersValidImage
      — Full render at standard bounds, verify unique colors > 15, has interior pixels

T3.2  testTricornRendersValidImage
      — Same as above

T3.3  testMagnetRendersValidImage
      — Same as above, but also verify convergence-based coloring works

T3.4  testNewTypesInAllRenderModes
      — For each new type × each RenderMode: render produces valid image without crash
      — Types without perturbation support fall back gracefully

T3.5  testTypeSelectionRoundTrip
      — Set type by name via registry, render, save location JSON, load it back
      — Verify type survives serialization round-trip
```

---

## Execution Plan

### Phase A: Test Foundation & Performance Baseline
**Goal:** Establish regression baselines (correctness AND performance) before any code changes

1. **A.0** Capture performance baseline:
   - Build the project
   - Run `FractalBenchmark benchmarks/ 400 300 1 3`
   - Save output to `benchmarks/baseline_results.txt`
   - Create `benchmarks/regression_log.md` with baseline table
   - Commit baseline results

2. **A.1** Add golden-value tests to `FractalRenderTest.java`:
   - T1.1 through T1.4 (rendering checksums)
   - T1.5 (iteration count pinning)
   - T1.6 (color mapping)
   - T1.7 (quadtree cache contract)

3. **A.2** Add utility tests:
   - T1.8 (JSON parse round-trip)
   - T1.9 (gradient default consistency)

4. **A.3** Run full test suite, verify all pass. Commit as baseline.

**Deliverable:** All T1 tests green + performance baseline recorded. This is the regression safety net.

---

### Phase B: Refactoring for Improvement #1 (New Escape-Time Types)

**B.1 — Deduplicate utilities (R4)**
1. Create `FractalJsonUtil.java` with shared `parseJson()`
2. Add `ColorGradient.fractalDefault()` static factory
3. Update `FractalTool`, `FractalRenderTest`, `FractalBenchmark`, `PerturbationEval` to use shared versions
4. Run all tests → must pass identically
5. Run benchmark → append to regression_log.md (expect 0% delta)
6. Commit

**B.2 — Extract FractalType interface (R1)**
1. Create `FractalType.java` as interface (rename old enum file)
2. Create `MandelbrotType.java` implementing `FractalType`
3. Create `JuliaType.java` implementing `FractalType` — owns juliaReal/juliaImag
4. Create `FractalTypeRegistry.java`
5. Update `FractalRenderer`:
   - Remove `juliaReal`/`juliaImag` fields (Julia type owns them)
   - Remove all `isJulia` branching in `renderDouble()` — call `type.iterate()` uniformly
   - Remove all `isJulia` branching in BigDecimal paths — call `type.iterateBig()` uniformly
6. Add T2.1, T2.2 tests
7. Run all tests (T1 + T2) → must pass
8. Run benchmark → append to regression_log.md (≤5% acceptable; document recovery if >0%)
9. Commit

**B.3 — Extract PerturbationStrategy (R2)**
1. Create `PerturbationStrategy.java` interface
2. Create `MandelbrotPerturbation.java` — extract from current `perturbIterate()` + `computeReferenceOrbitMandelbrot()`
3. Create `JuliaPerturbation.java` — extract from current Julia-specific perturbation + `computeReferenceOrbitJulia()`
4. Update `FractalRenderer.renderBigDecimal()`:
   - Get strategy from `type.getPerturbationStrategy()`
   - If null, skip perturbation, use pure BigDecimal
   - Otherwise delegate reference orbit + perturbation to strategy
5. Add T2.3 tests
6. Run all tests → must pass (especially T1.3 perturbation golden)
7. Run benchmark (PERTURBATION + BIGDECIMAL modes) → append to regression_log.md (≤5%; this is the highest-risk phase for perf)
8. If regression >5%: STOP. Profile, identify cause, apply recovery before committing.
9. Commit

**B.4 — Extract ViewportCalculator and ColorMapper (R3 partial)**
1. Create `ViewportCalculator.java` — shared viewport math
2. Create `FractalColorMapper.java` — LUT construction + colorForIter
3. Simplify `FractalRenderer` to use extracted classes
4. Add T2.4, T2.5 tests
5. Run all tests → must pass
6. Run benchmark → append to regression_log.md (expect 0% delta; these are per-render, not per-pixel)
7. Also: if B.2 or B.3 logged a recovery plan targeting this phase, execute the recovery now and verify improvement
8. Commit

**B.5 — Dynamic UI population (R5)**
1. Update `FractalTool.createSettingsPanel()`:
   - Populate combo from `FractalTypeRegistry.getNames()`
   - Selection by name: `registry.getByName(combo.getSelectedItem())`
2. Update `DrawingApp.createFractalMenu()`:
   - Generate radio buttons from registry
3. Update save/load in `FractalTool`:
   - Save type name string
   - Load type by name from registry
4. Run all tests → must pass
5. Commit

---

### Phase C: Implement Improvement #1 (New Escape-Time Types)

**C.1 — Burning Ship**
1. Create `BurningShipType.java`:
   - `iterate()`: z_{n+1} = (|Re(z)| + i|Im(z)|)² + c
   - `iterateBig()`: same with BigDecimal
   - `supportsPerturbation()` → false (initially; perturbation formula is complex)
2. Register in app startup
3. Add T2.6, T3.1 tests
4. Run all tests → commit

**C.2 — Tricorn**
1. Create `TricornType.java`:
   - `iterate()`: z_{n+1} = conj(z)² + c
   - `iterateBig()`: same with BigDecimal
   - `supportsPerturbation()` → false initially
2. Register in app startup
3. Add T2.7, T3.2 tests
4. Run all tests → commit

**C.3 — Magnet Type I**
1. Create `MagnetTypeIType.java`:
   - `iterate()`: z_{n+1} = ((z² + c - 1) / (2z + c - 2))²
   - Escape condition: |z - 1|² < ε (convergence) OR |z|² > bailout
   - `iterateBig()`: same with BigDecimal
   - `supportsPerturbation()` → false
2. Register in app startup
3. Add T2.8, T3.3 tests
4. Run all tests → commit

**C.4 — Integration verification**
1. Run T3.4 (all types × all modes)
2. Run T3.5 (serialization round-trip)
3. Run benchmark on Mandelbrot + Julia locations ONLY → must show 0% regression vs baseline (new types must not pollute existing type performance via polymorphic dispatch)
4. If JIT polymorphic dispatch regression detected: switch to type-specific render path selection at render start (see Risk Mitigation #4)
5. Manual smoke test: launch app, verify dropdown shows all 5 types
6. Commit

---

### Phase D: Implement Improvement #2 (Render Progress Indicator)

**D.1 — Wire progress to SwingWorker**
1. In `FractalTool.renderAsync()`:
   - SwingWorker.process() polls `renderer.getBigDecimalProgress()` periodically
   - Updates `progressLabel` with "Rendering: 45% (row 225/500)"
2. Add a `javax.swing.Timer` that fires every 200ms during BigDecimal renders to poll progress
3. Show elapsed time in progressLabel alongside percentage

**D.2 — Add progress for double-precision renders**
1. Add `doubleCompletedRows` tracking to `FractalRenderer.renderDouble()`
2. Wire to same progress display
3. Test: verify progress updates during render (may need a slow render at high resolution)

**D.3 — Cancel feedback**
1. When render is cancelled (new zoom), show "Cancelled" briefly in progressLabel
2. Clear after 1 second or when new render starts

**D.4 — Commit**

---

## File Change Summary

### New files
| File | Phase | Purpose |
|------|-------|---------|
| `fractal/FractalJsonUtil.java` | B.1 | Shared JSON parsing |
| `fractal/MandelbrotType.java` | B.2 | Mandelbrot implementation |
| `fractal/JuliaType.java` | B.2 | Julia implementation (owns constants) |
| `fractal/FractalTypeRegistry.java` | B.2 | Dynamic type registry |
| `fractal/PerturbationStrategy.java` | B.3 | Perturbation interface |
| `fractal/MandelbrotPerturbation.java` | B.3 | Mandelbrot perturbation |
| `fractal/JuliaPerturbation.java` | B.3 | Julia perturbation |
| `fractal/ViewportCalculator.java` | B.4 | Shared viewport math |
| `fractal/FractalColorMapper.java` | B.4 | Color LUT + mapping |
| `fractal/BurningShipType.java` | C.1 | Burning Ship fractal |
| `fractal/TricornType.java` | C.2 | Tricorn fractal |
| `fractal/MagnetTypeIType.java` | C.3 | Magnet Type I fractal |

### Modified files
| File | Phases | Changes |
|------|--------|---------|
| `fractal/FractalType.java` | B.2 | Rewrite: enum → interface |
| `fractal/FractalRenderer.java` | B.2, B.3, B.4, D | Remove isJulia branching, delegate to strategies, extract utilities |
| `fractal/FractalRenderTest.java` | A, B, C | Add T1/T2/T3 tests, update type construction |
| `fractal/FractalBenchmark.java` | B.1, B.2 | Use shared utilities, update type loading |
| `fractal/PerturbationEval.java` | B.1, B.2 | Use shared utilities, update type loading |
| `tools/FractalTool.java` | B.1, B.5, D | Use shared utilities, registry-based combo, progress display |
| `DrawingApp.java` | B.5 | Registry-based fractal menu |
| `gradient/ColorGradient.java` | B.1 | Add fractalDefault() factory |

### Deleted code (no new files)
- `FractalType.iterateJulia()` static methods (moved to JuliaType)
- `FractalType.iterateJuliaBig()` static methods (moved to JuliaType)
- `FractalRenderer.juliaReal/juliaImag` fields (moved to JuliaType)
- `FractalRenderer.perturbIterate()` (moved to PerturbationStrategy implementations)
- `FractalRenderer.computeReferenceOrbitMandelbrot/Julia()` (moved to PerturbationStrategy)
- Duplicated `parseJson()` in FractalTool, FractalBenchmark, PerturbationEval
- Duplicated `createGradient()` in FractalTool, FractalRenderTest, FractalBenchmark, PerturbationEval

---

## Performance Regression Policy

**KEY REQUIREMENT:** No refactoring phase may introduce a permanent performance regression. Any temporary regression must be documented with a concrete path to recovery.

### Benchmark Protocol

The existing `FractalBenchmark` CLI tool and the 6 benchmark locations in `benchmarks/` are the performance authority. The benchmark covers 3 render modes (DOUBLE, PERTURBATION, BIGDECIMAL) across shallow and deep zoom locations.

**Baseline capture (Phase A, step A.0 — before any code changes):**

1. Run `FractalBenchmark` against all locations in `benchmarks/` directory at 400x300, 1 warmup, 3 runs
2. Record results to `benchmarks/baseline_results.txt` with:
   - Per-location, per-mode: min/max/avg ms, px/s
   - System info (Java version, CPU core count)
   - Git commit hash (pre-refactoring baseline)
3. This file is committed and becomes the reference for all subsequent phases

**Per-phase benchmark gates:**

| Phase | What to benchmark | Acceptable regression | Recovery path required if exceeded |
|-------|-------------------|----------------------|-----------------------------------|
| B.1 (dedup utilities) | Full suite | 0% — pure code motion, no hot-path changes | N/A — should be zero-cost |
| B.2 (FractalType interface) | Full suite | ≤5% — interface dispatch vs enum may add virtual call overhead | Document: can recover with `final` classes or profile-guided inlining; JIT typically eliminates within warmup |
| B.3 (PerturbationStrategy) | PERTURBATION + BIGDECIMAL modes | ≤5% — strategy delegation adds one indirection in the inner loop | Document: if >5%, inline the strategy call or use a method handle. The perturbation inner loop is called O(maxIter × pixels) times so even small overhead compounds |
| B.4 (ViewportCalculator/ColorMapper) | Full suite | 0% — extracted code runs once per render, not per pixel | N/A — should be zero-cost |
| B.5 (dynamic UI) | N/A — no render path changes | N/A | N/A |
| C.1-C.3 (new types) | Mandelbrot + Julia only (existing types must not regress) | 0% — new types don't touch existing iteration code | N/A |
| D (progress indicator) | Full suite | ≤2% — progress polling adds atomic increment per row | Document: if >2%, reduce polling frequency or use volatile counter instead of AtomicInteger |

**How to run benchmarks at each gate:**

```bash
# From project root, after build:
java -cp out com.seanick80.drawingapp.fractal.FractalBenchmark benchmarks/ 400 300 1 3
```

**What to record at each gate:**

After each phase's benchmark run, append results to `benchmarks/regression_log.md`:

```markdown
## Phase B.2 — FractalType Interface Extraction
**Commit:** <hash>
**Date:** <date>

| Location | Mode | Baseline avg | Current avg | Delta | Status |
|----------|------|-------------|-------------|-------|--------|
| bigdecimal_location | PERTURBATION | 4200ms | 4350ms | +3.6% | ACCEPTABLE |
| ... | ... | ... | ... | ... | ... |

**Regression notes:** (if any delta > 0%)
- Cause: virtual dispatch on iterate() adds ~150ns per call, visible at 456 iterations × 120000 pixels
- Recovery plan: Mark MandelbrotType as final. If insufficient, add @ForceInline hint or extract hot loop to avoid interface dispatch.
- Recovery deadline: Phase B.4 (viewport extraction is a natural point to also optimize iteration dispatch)
```

**Hard stop rule:** If any phase exceeds its acceptable regression threshold AND the implementer cannot articulate a specific, actionable recovery plan with a target phase, that phase must be reworked before proceeding. Do not accumulate unplanned regressions.

### Performance-Sensitive Hot Paths

These are the code paths where abstraction overhead matters most, ordered by call frequency:

1. **`FractalType.iterate()`** — called `width × height × (1 - cacheHitRate)` times per render. At 400×300 with 50% cache hits, that's 60,000 calls. Each call runs `maxIterations` loop iterations internally. Interface dispatch overhead here is dwarfed by the loop body, but measure to confirm.

2. **`perturbIterate()`** — called once per non-interior pixel in PERTURBATION mode. Inner loop runs up to `refEscapeIter` iterations. This is the single most performance-critical method. Strategy pattern adds one level of indirection at the call site, not inside the loop — acceptable.

3. **`colorForIter()`** — called once per pixel. LUT lookup is O(1). Extraction to `FractalColorMapper` should be zero-cost if the LUT array reference is passed directly.

4. **Viewport math** — called once per render (not per pixel). Extraction is zero-cost.

---

## Risk Mitigation

1. **Perturbation correctness is the highest-risk refactoring.** The T1.3 golden test pins the exact pixel output. If B.3 changes even one pixel, we investigate before proceeding.
2. **Julia constant ownership change** (from renderer to type) affects save/load JSON format. B.5 must handle backward compatibility: loading old JSON that has `juliaReal`/`juliaImag` at the top level.
3. **Magnet Type I** has a different escape condition (convergence to fixed point, not divergence). The renderer's `colorForIter()` assumes higher iteration = closer to set interior. This works for Magnet too (convergence = interior), but the bailout radius check in perturbation would need adjustment. Since Magnet won't have perturbation initially, this is deferred.
4. **Performance regression from abstraction** — mitigated by the benchmark gate protocol above. The JIT compiler typically eliminates interface dispatch overhead for monomorphic call sites (only one implementation loaded). The risk increases in Phase C when multiple FractalType implementations exist, potentially making call sites polymorphic. If benchmarks show this, the recovery is to use type-specific render paths selected once at render start rather than per-pixel dispatch.

---

## Language-Level Performance Ceiling: Java vs Native

The current implementation is in Java, which was chosen early on for development velocity, Swing UI integration, and cross-platform ease. However, fractal rendering is fundamentally an allocation-heavy, tight-loop numerical workload where Java's runtime characteristics create friction:

- **Per-iteration object allocation in BigDecimal paths.** Each `BigDecimal.multiply()`, `.add()`, `.subtract()` allocates a new `BigDecimal` object. At deep zoom with 500+ iterations × 120,000 pixels, this generates hundreds of millions of short-lived objects per render. The GC handles this, but the allocation + collection overhead is nonzero and unpredictable (GC pauses can spike individual frame times).
- **No value types (yet).** Complex number pairs `(re, im)` must be heap-allocated objects or decomposed into parallel `double[]` arrays. C/C++ structs or Rust tuples live on the stack with zero allocation cost. Project Valhalla (Java value types) would help, but it's not yet production-ready.
- **No SIMD intrinsics.** The inner escape-time loop is a textbook candidate for AVX2/AVX-512 vectorization (process 4-8 pixels simultaneously). Java's auto-vectorization via the JIT is inconsistent and opaque. C/C++ with intrinsics or compiler hints gives direct control.
- **BigDecimal is not designed for numerical hot loops.** Its internal representation (unscaled `BigInteger` + scale) carries overhead for arbitrary-precision decimal semantics we don't need — we need arbitrary-precision *binary* floating-point. A purpose-built arbitrary-precision library (GMP/MPFR via JNI, or a pure-C implementation) would be significantly faster.

**Decision point: reevaluate after Phase C.**

After all refactoring and new type implementation is complete, run the full benchmark suite and compare against theoretical throughput limits. Specifically:

1. **Profile GC pressure** during deep-zoom BigDecimal renders. If GC time exceeds 10% of render time, the allocation pattern is a fundamental bottleneck that Java workarounds (object pooling, `MutableBigDecimal` hacks) can only partially address.
2. **Profile the perturbation inner loop** — this is pure `double` arithmetic and should JIT-compile to near-native speed. If it does, the Java overhead is limited to the BigDecimal paths and the native evaluation only needs to cover arbitrary-precision math.
3. **Evaluate a hybrid approach:** keep the Java Swing UI and tool framework, but call into a native library (via JNI or Panama FFI) for the compute-intensive iteration kernels. This preserves the existing UI investment while unlocking:
   - Stack-allocated complex number types
   - SIMD vectorization of the escape-time loop
   - GMP/MPFR for arbitrary-precision arithmetic (~10-50x faster than `java.math.BigDecimal` for multiplication-heavy workloads)
   - Manual memory control for reference orbit arrays
4. **If the hybrid approach is warranted**, the refactoring in Phases B.2 (FractalType interface) and B.3 (PerturbationStrategy) directly enables it — the native code implements the same interface contracts, and the renderer doesn't need to know whether iteration is happening in Java or native code.

### Custom Arbitrary-Precision Floating Point Library

`java.math.BigDecimal` is the single largest contributor to deep-zoom render time. It's used in three hot paths:

1. **Pure BigDecimal pixel iteration** (`renderPureBigDecimal`, L553-571) — every pixel × every iteration step calls `multiply()`, `add()`, `subtract()`, each allocating a new `BigDecimal`. At 400×300 × 500 iterations, that's ~180M `BigDecimal` allocations per render.
2. **Reference orbit computation** (`computeReferenceOrbitMandelbrot/Julia`, L601-686) — one orbit per render, but up to `maxIterations` steps of BigDecimal arithmetic. Each step: 2 multiplies + 1 add + 1 subtract + 1 compare = ~5 allocations × 500 iterations = ~2,500 objects. This is tolerable.
3. **Interior pruning boundary pixels** (L305-363) — BigDecimal iteration for border pixels of each 50×100 block. Volume depends on block count and boundary length.

**Why BigDecimal is slow for this workload:**

| Property | BigDecimal behavior | What we actually need |
|----------|-------------------|----------------------|
| Number system | Base-10 decimal | Base-2 binary (we're doing z²+c, not currency) |
| Internal representation | `BigInteger` (immutable, arbitrary length) + `int scale` | Fixed-width limb array (e.g., 4×64-bit for ~240-bit precision) |
| Allocation | Every arithmetic op allocates a new object + new `BigInteger` + new `int[]` for magnitude | Mutate in place, zero allocation in the inner loop |
| Precision tracking | Dynamic per-operation via `MathContext` | Fixed for entire render (set once based on zoom level) |
| Normalization | Strips trailing zeros, adjusts scale | Unnecessary — we don't need exact decimal representation |

**What a custom `FixedPrecisionFloat` could look like:**

```java
/**
 * Mutable fixed-precision binary floating point for fractal iteration.
 * Backed by a fixed-size long[] limb array, no heap allocation after construction.
 */
public final class FixedPrecisionFloat {
    private final long[] limbs;  // e.g., 4 longs = 256-bit mantissa
    private int exponent;
    private int sign;

    // Mutating arithmetic — no allocation
    public FixedPrecisionFloat multiply(FixedPrecisionFloat other, FixedPrecisionFloat result) { ... }
    public FixedPrecisionFloat add(FixedPrecisionFloat other, FixedPrecisionFloat result) { ... }
    public FixedPrecisionFloat subtract(FixedPrecisionFloat other, FixedPrecisionFloat result) { ... }
    public int compareTo(FixedPrecisionFloat other) { ... }
    public double doubleValue() { ... }

    // Pre-allocate a pool of temporaries for the iteration loop
    public static FixedPrecisionFloat[] allocatePool(int count, int limbCount) { ... }
}
```

**Key design choices:**

1. **Fixed limb count per render.** The zoom level determines precision requirements once at render start. A 10^-30 zoom needs ~100 bits; 10^-60 needs ~200 bits. We pick the limb count once and every `FixedPrecisionFloat` in that render uses it. No dynamic sizing.
2. **Mutable with output parameter.** `a.multiply(b, result)` writes into `result` — zero allocation. The iteration loop pre-allocates `zr`, `zi`, `zr2`, `zi2`, `temp` as a fixed pool of 6-8 values.
3. **Base-2 representation.** Multiplication of two N-limb numbers uses schoolbook or Karatsuba (for N≥4) on `long` words with carry propagation. This is what GMP does internally — we'd be a simplified version targeting the specific precisions fractal rendering needs (64-512 bits, not thousands).
4. **No decimal conversion overhead.** Values stay in binary throughout iteration. Only convert to/from `BigDecimal` at the boundaries (reading JSON, displaying coordinates).

**Estimated speedup over BigDecimal:**

- **Allocation elimination alone:** At 180M allocations per render, even at 10ns/allocation (optimistic for GC-tracked objects), that's ~1.8 seconds of pure allocation overhead. A mutable approach drops this to zero.
- **Binary vs decimal multiplication:** BigDecimal internally converts to binary for multiplication anyway, then converts back. We skip both conversions.
- **Conservative estimate:** 3-10x speedup for pure BigDecimal paths. This is consistent with benchmarks of GMP vs `java.math.BigInteger` at similar precisions.

**What it would take to build:**

| Component | Effort | Complexity |
|-----------|--------|------------|
| Limb-array add/subtract with carry | Small | Low — standard bignum operation |
| Limb-array multiply (schoolbook) | Medium | Medium — carry propagation across limbs needs care |
| Karatsuba multiply (for ≥4 limbs) | Medium | Medium — recursive divide-and-conquer, but well-documented |
| Normalization / exponent tracking | Small | Low |
| Comparison | Small | Low |
| `doubleValue()` conversion | Small | Low |
| `BigDecimal` ↔ `FixedPrecisionFloat` conversion | Small | Low — only at boundaries |
| Correctness tests against BigDecimal | Medium | Critical — must produce bit-identical iteration counts |
| Integration with FractalType interface | Small | The `iterateBig()` signature would gain a `FixedPrecisionFloat` overload or the library implements a common interface |

**Total estimate:** ~2-4 days for a working implementation, ~1-2 more days for thorough testing and integration. The schoolbook multiply loop is the core complexity; everything else is straightforward.

**Alternative: JNI bridge to GMP/MPFR.** Instead of writing our own, we could call GMP through JNI or Panama FFI. GMP's `mpz_mul` is hand-tuned assembly for each CPU architecture. However, JNI call overhead (~50-100ns per call) at 180M calls/render would add ~9-18 seconds — worse than BigDecimal. The JNI approach only wins if we push the entire iteration loop into native code (see hybrid approach above). A pure-Java `FixedPrecisionFloat` avoids this boundary-crossing overhead entirely.

**Recommendation:** Build the Java `FixedPrecisionFloat` as a post-Phase-C optimization if BigDecimal profiling confirms >30% of render time is allocation/GC. If that's still not enough, the next step is the native iteration kernel which uses GMP internally with zero JNI boundary crossings per iteration.

This is NOT a blocker for the current plan. The refactoring and new type work should proceed in Java. But if benchmark results after Phase C show that we're spending >30% of render time on overhead that Java cannot eliminate (GC, boxing, missing SIMD), then a native compute kernel becomes the logical next step rather than further Java micro-optimization.

---

## Phase E: Codebase Cleanup

**Goal:** Remove stale investigation artifacts, organize persistent test data, and add .gitignore rules to prevent re-accumulation.

### Current state of stale files

**Root directory** — 6 untracked PNGs from regression investigation (Mar 16):
- `diag_bigdecimal.png`, `diag_diff.png`, `diag_perturbation.png` — diagnostic comparison renders
- `err_bigdecimal.png`, `err_diff.png`, `err_perturbation.png` — error investigation renders

`fractaldrawing.png` is tracked (README screenshot) — keep.

**`benchmarks/`** — 18 untracked PNGs from benchmark/eval runs:
- `*_eval_bd.png`, `*_eval_pt.png`, `*_eval_diff.png` — PerturbationEval output (6 locations × 3 modes = 18 files)
- `*_bigdecimal.png`, `*_perturbation.png`, `*_double.png` — FractalBenchmark output (3 files)

The 7 tracked `.json` files are the actual benchmark locations — these are persistent test data and must stay.

**`interest/`** — 3 untracked `.json` files (saved locations of visual interest). These are user-curated and worth keeping, but should be tracked.

### Cleanup steps

**E.1 — Delete stale investigation artifacts**
1. Delete root-level `diag_*.png` and `err_*.png` (6 files, ~160KB)
2. Delete `benchmarks/*_eval_*.png` (9 files, ~580KB)
3. Delete `benchmarks/*_bigdecimal.png`, `*_perturbation.png`, `*_double.png` (6 files, ~340KB)
4. Verify no tracked files were deleted: `git status` should show no deletions

**E.2 — Add .gitignore rules for generated output**
Add to `.gitignore`:
```
# Benchmark and eval output images (regenerated by tools)
benchmarks/*.png
# Diagnostic/investigation renders
diag_*.png
err_*.png
```

**E.3 — Track persistent data that should be versioned**
1. `git add interest/` — curated locations of visual interest
2. Verify `benchmarks/*.json` are already tracked (they are)

**E.4 — Commit cleanup**

### Timing

Phase E can run at any point — it has no dependencies on Phases A-D and doesn't touch source code. Recommend running it first as a quick win to reduce noise in `git status`.

---

## Sequencing Summary

```
Phase E (cleanup)   ██░░░░░░░░░░░░░░░░░░  — Remove stale files, gitignore rules (independent)
Phase A (tests)     ░░████░░░░░░░░░░░░░░  — Regression baselines
Phase B (refactor)  ░░░░░░████████░░░░░░  — 5 sub-phases, each tested + committed
Phase C (implement) ░░░░░░░░░░░░░░████░░  — 3 new types + integration tests
Phase D (progress)  ░░░░░░░░░░░░░░░░░░██  — Wire existing infra to UI
```

Each phase boundary is a commit point with all tests green.

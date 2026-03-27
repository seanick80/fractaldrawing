
# Future work

## ~~Memory hygiene~~ DONE (2026-03-20)
Pre-allocated render buffers (rgb, iters, cacheHit, refOrbit arrays) with lazy resize. Promoted BigDecimal constants (FOUR, TWO) to static finals in all fractal types and perturbation strategies. Cached PerturbationStrategy instances (singleton for Mandelbrot, per-instance for Julia). Pre-computed BigDecimal pixel coordinate arrays to eliminate per-pixel `new BigDecimal()` allocations in boundary checks and glitch fallback.

## ~~Quadtree based render job pruning~~ DONE (2026-03-20)
Replaced flat 50×100 block interior pruning with hierarchical subdivision. Each 50×50 initial block is checked: 4-corner quick rejection skips blocks with no interior pixels, then full boundary check with recursive subdivision down to 8×8 minimum. Blocks with all-interior boundaries are filled black and skipped by the perturbation phase. Mixed-boundary blocks are subdivided into quadrants for finer-grained pruning. No regression on exterior-heavy renders; should significantly help interior-heavy renders (e.g., Mandelbrot cardioid).

## ~~Quadtree cache cross-render reuse~~ DONE (2026-03-20, enhanced 2026-03-21)
Cache is no longer cleared on zoom/pan (`setBounds()` now prunes instead of clearing). Double-precision quadtree cache is wired into both BigDecimal render paths for moderate zoom. Cache entries store final z values (finalZr, finalZi) alongside iteration counts for future smooth coloring and period detection.

**Previous-render BigDecimal cache (2026-03-21):** At deep zoom where double-precision cache keys can't distinguish pixels, a separate pixel-mapping cache stores the full BigDecimal coordinate arrays and iteration results from each render. On the next render, it maps new pixel positions to old pixel positions via O(width+height) BigDecimal divisions. Viewport origin snapping aligns the new pixel grid with the old grid (sub-pixel shift) so every other pixel in each dimension lands exactly on an old pixel position. Gives 25% reuse on 2x zoom, 75% on pan. Tight tolerance (0.01 × pixel spacing) ensures only exact coordinate matches are reused — no blockiness.

## ~~Load/Save type bug~~ FIXED (2026-03-20)
Loading a Mandelbrot JSON that contained juliaReal/juliaImag fields would switch the renderer to Julia mode because `setJuliaConstant()` unconditionally overwrites the type. Fixed to only apply Julia constant when loaded type is JuliaType.

## ~~Precalculating individual pixel lat, long values~~ DONE (2026-03-20)
Pre-computed `pixelCx[]` and `pixelCy[]` BigDecimal arrays once per render, passed to all worker threads. Eliminates per-pixel `BigDecimal.add(scaleX.multiply(...))` in boundary checks, pruning, and glitch fallback. Implemented as part of the memory hygiene refactor.

## ~~Add Percent complete status back~~ DONE (2026-03-18)
Implemented via SwingTimer polling every 200ms. Shows percentage, row count, elapsed time, and ETA.


## ~~Pan (click and drag)~~ DONE (2026-03-21)
Left-click drag pans the viewport. The raster image shifts with the cursor for instant visual feedback (black fill behind exposed edges), then re-renders on mouse release. Click without drag still zooms (left=in, right=out). Leverages the quadtree cache for pixel reuse across pans.

## ~~Gradient editor: color picker for control points~~ DONE (2026-03-21)
Double-click a stop marker in the gradient preview bar to open JColorChooser. Selected color auto-updates the R, G, B control point values for that stop. Click in the preview bar to select stops, Shift+click to add, right-click to delete.

## ~~Auto-generate gradient from palette click~~ DONE (2026-03-21)
When the fractal tool is active, clicking a color in the palette generates a 6-stop triadic gradient favoring that color (dark base → full color → pastel → triadic +120° → triadic +240° → near-black). Gradient applies immediately and triggers a re-render.

## ~~Random location explorer ("I Feel Lucky")~~ DONE (2026-03-21)
Button in fractal tool settings panel. Samples random Mandelbrot locations (zoom 10^-2 to 10^-8), tests 8 points for varied iteration counts (≥4 distinct, mix of interior and escape), and navigates to the first interesting location found.

## Poster mode (high-resolution export)
Render the current fractal view at print-quality resolution (e.g., 4000×3000 or larger) with smooth linear (DIVISION) coloring, and export to a file suitable for printing as a wall poster.

## ~~Image zoom~~ DONE (2026-03-21)
Scroll wheel zooms the rendered image view (0.25x–32x) centered on cursor — no re-render. Ctrl+scroll does fractal zoom (changes the complex-plane viewport). View zoom resets automatically when a new fractal render completes. Mouse coordinates are transformed to image space so click-to-zoom and pan work correctly at any view zoom level.

## ~~Zoom animation~~ DONE (2026-03-27)
Zoom movie export with auto-discovery of visually interesting boundary points. Scans a low-res iteration grid, scores pixels by local iteration variance × depth bonus, and presents top candidates with preview thumbnails. Users can pick an auto-discovered target or use their current viewport location. Renders smooth exponential zoom as numbered PNGs + uncompressed AVI video (via `AviWriter`). Boomerang mode reuses forward frames in reverse for seamless looping without re-rendering. `ZoomAnimator` handles keyframe interpolation (exponential zoom, t² ease-in for position, linear iteration count). `AviWriter` writes RIFF AVI with uncompressed RGB/DIB frames — universal playback, no codec needed.

## Features I’d like to explore when we have more time:
### Animations
*   Iteration animation - add one iteration, display it, add another, etc - up to the point where not many iterations are present anymore. Save the resulting iteration slides as a video which can be played back.
*   Palette cycle animation

My previous version of this app had these features as selectable animations, as a screen saver.


## ~~Different fractal types~~ DONE (2026-03-18, escape-time types)
Burning Ship, Tricorn, and Magnet Type I are implemented and auto-populate UI via FractalTypeRegistry. Types without perturbation support fall back to pure BigDecimal for deep zoom.

### Remaining fractal types (not yet implemented)

### 3D fractals (would require a new rendering pipeline)
- **Mandelbulb** — 3D extension of the Mandelbrot set using spherical coordinates and a power parameter
- **Mandelbox** — 3D fractal using box-fold and ball-fold transformations; highly configurable scale parameter

### Iterated function system (IFS) fractals
These don't use escape-time iteration — they build geometry via recursive subdivision or transformation rules, so they'd need a separate rendering path:
- **Sierpinski Triangle** — recursively removes the central triangle from subdivided equilateral triangles
- **Sierpinski Carpet** — same idea but with squares; removes the central square at each level
- **Koch Snowflake** — recursively replaces each line segment's middle third with an equilateral triangle bump

### Implementation notes
- Escape-time types now use the `FractalType` interface + `FractalTypeRegistry` pattern — implement interface, register, auto-appears in UI
- Perturbation strategies for Burning Ship/Tricorn are a future optimization (currently fall back to BigDecimal at deep zoom)
- Magnet Type II would follow the same pattern as Type I
- IFS fractals need a different renderer since they don't map pixels to escape counts — probably a recursive geometry builder that draws to the canvas directly
- 3D fractals (Mandelbulb, Mandelbox) would need ray-marching or distance-estimation rendering, which is a larger undertaking

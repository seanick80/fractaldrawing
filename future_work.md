
# Future work

## ~~Memory hygiene~~ DONE (2026-03-20)
Pre-allocated render buffers (rgb, iters, cacheHit, refOrbit arrays) with lazy resize. Promoted BigDecimal constants (FOUR, TWO) to static finals in all fractal types and perturbation strategies. Cached PerturbationStrategy instances (singleton for Mandelbrot, per-instance for Julia). Pre-computed BigDecimal pixel coordinate arrays to eliminate per-pixel `new BigDecimal()` allocations in boundary checks and glitch fallback.

## ~~Quadtree based render job pruning~~ DONE (2026-03-20)
Replaced flat 50×100 block interior pruning with hierarchical subdivision. Each 50×50 initial block is checked: 4-corner quick rejection skips blocks with no interior pixels, then full boundary check with recursive subdivision down to 8×8 minimum. Blocks with all-interior boundaries are filled black and skipped by the perturbation phase. Mixed-boundary blocks are subdivided into quadrants for finer-grained pruning. No regression on exterior-heavy renders; should significantly help interior-heavy renders (e.g., Mandelbrot cardioid).

## Precalculating individual pixel lat, long values.
 One big chunk of time spent per iteration is to map a pixel to it’s real and imaginary coordinates.If you calculate the real axis and the imaginary axis of the current viewport once, then pass those values in to each cell worker, you’re saving 2-4 orders of magnitude of calculation on just that one value.

## ~~Add Percent complete status back~~ DONE (2026-03-18)
Implemented via SwingTimer polling every 200ms. Shows percentage, row count, elapsed time, and ETA.


## Features I’d like to explore when we have more time:
### Animations
*   Iteration animation - add one iteration, display it, add another, etc - up to the point where not many iterations are present anymore. Save the resulting iteration slides as a video which can be played back.
*   Zoom animation
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


##  Random location explorer
I had calculated a random location explorer heuristic, basically selecting random numbers between -2, 2 in both imaginary and real dimensions plus a random zoom level between -4 and say -8, then rendering 8 points and seeing if there were at least 4 different palette entries among those 8 images and they aren’t all fill - I set a timer to try to calculate a new random interesting point and tried up to 10000 times. I almost never saw a failure to find an interesting point. Now that we have claude, I am certain we could find a way more interesting heuristic to select random interesting locations to render.

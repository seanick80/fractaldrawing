
# Future work

## Memory hygiene
We could pre-allocate and not reallocating any necessary buffers. Please apply static allocations on fractal tool initialization, and then try to remove any need to do any further allocations, especially in the perturbation and bigdecimal cases as that is where we need to do the most optimization


## Quadtree based render job pruning
Perturbation is really slow because it falls back in a lot of fill cases. 
Can we explore a quadtree based evaluation at a much lower resolution, and only render quadtrees that are not completely surrounded by fill (which is how I refer to Max iteration count)? Then if all lower level quadtree items are also surrounded by fill, just assume all pixels represented by those quadtrees below are fills. We’ll see how much of a performance improvement we can get by detecting blocks full of fill first.

## Precalculating individual pixel lat, long values.
 One big chunk of time spent per iteration is to map a pixel to it’s real and imaginary coordinates.If you calculate the real axis and the imaginary axis of the current viewport once, then pass those values in to each cell worker, you’re saving 2-4 orders of magnitude of calculation on just that one value.

## Add Percent complete status back
This particular location is really quite slow. Would love to know what it’s doing.
{
  "type": "MANDELBROT",
  "minReal": "-1.25015600357202160012093372643103594",
  "maxReal": "-1.25015600357202069062623195350279806",
  "minImag": "0.00967906445708459917854086823385124937",
  "maxImag": "0.00967906445708550867324264116208916425",
  "maxIterations": 556,
  "juliaReal": "-0.7",
  "juliaImag": "0.27015"
}


## Features I’d like to explore when we have more time:
### Animations
*   Iteration animation - add one iteration, display it, add another, etc - up to the point where not many iterations are present anymore. Save the resulting iteration slides as a video which can be played back.
*   Zoom animation
*   Palette cycle animation

My previous version of this app had these features as selectable animations, as a screen saver.


## Different fractal types
Currently the dropdown only offers Mandelbrot and Julia. We'd like to add more fractal types as selectable options:

### Escape-time fractals (complex plane)
- **Burning Ship** — uses `abs(zr)` and `abs(zi)` before squaring, producing an asymmetric ship-like shape
- **Magnet (Type I & II)** — derived from magnetic renormalization-group equations; rational iteration formulas with both z² and z terms
- **Tricorn (Mandelbar)** — uses the conjugate of z (negated imaginary part) each iteration

### 3D fractals (would require a new rendering pipeline)
- **Mandelbulb** — 3D extension of the Mandelbrot set using spherical coordinates and a power parameter
- **Mandelbox** — 3D fractal using box-fold and ball-fold transformations; highly configurable scale parameter

### Iterated function system (IFS) fractals
These don't use escape-time iteration — they build geometry via recursive subdivision or transformation rules, so they'd need a separate rendering path:
- **Sierpinski Triangle** — recursively removes the central triangle from subdivided equilateral triangles
- **Sierpinski Carpet** — same idea but with squares; removes the central square at each level
- **Koch Snowflake** — recursively replaces each line segment's middle third with an equilateral triangle bump

### Implementation notes
- Escape-time types (Burning Ship, Magnet, Tricorn) fit naturally into the existing `FractalType` enum — just add new `iterate()` / `iterateBig()` implementations
- IFS fractals need a different renderer since they don't map pixels to escape counts — probably a recursive geometry builder that draws to the canvas directly
- 3D fractals (Mandelbulb, Mandelbox) would need ray-marching or distance-estimation rendering, which is a larger undertaking
- All types should appear in the fractal type dropdown so the user can select them


##  Random location explorer
I had calculated a random location explorer heuristic, basically selecting random numbers between -2, 2 in both imaginary and real dimensions plus a random zoom level between -4 and say -8, then rendering 8 points and seeing if there were at least 4 different palette entries among those 8 images and they aren’t all fill - I set a timer to try to calculate a new random interesting point and tried up to 10000 times. I almost never saw a failure to find an interesting point. Now that we have claude, I am certain we could find a way more interesting heuristic to select random interesting locations to render.

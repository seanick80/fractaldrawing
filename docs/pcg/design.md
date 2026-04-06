# Procedural Content Generation from Fractal Iteration Data

## Core Idea

The fractal renderer produces an iteration cache (`int[]`) after each render.
Each element records how many iterations a point took before escaping (or
`maxIterations` for interior points). This 2D scalar field has unique
properties that make it a rich seed for procedural content generation:

- **Smooth gradients** in escape regions
- **Sharp boundaries** at the fractal edge
- **Self-similar structure** at different scales
- **Natural regions** — interior (bounded) vs exterior (escaping)
- **Maximum complexity at boundaries** where iteration counts change rapidly

## Implemented Models

### 1. Iteration Terrain Flyover

Uses the iteration cache directly as a heightmap for the existing Comanche-
style voxel terrain renderer. The fractal gradient provides the colormap.

**How it works:**
1. Copy `renderer.getLastRenderIters()` into a float heightmap (normalized 0..1)
2. Resample to power-of-2+1 size required by `TerrainRenderer`
3. Build colormap from the fractal's `ColorGradient`
4. Open `TerrainViewer` — same controls as existing 3D Flyover

**What you see:** The Mandelbrot set boundary becomes a mountain ridge.
Interior points (max iterations) form plateaus. Escape gradients become
smooth valleys and slopes. Zoomed-in regions produce more dramatic terrain
because iteration counts span a wider range.

### 2. L-System Tree

Uses iteration data to parameterize a stochastic L-System that generates
2D tree structures via turtle graphics.

**How it works:**
1. Sample the iteration field at the root position and surrounding area
2. Derive L-System parameters from local iteration statistics:
   - **Branch angle** — from normalized iteration count at root
   - **Length decay** — from local iteration variance (high variance = shorter branches)
   - **Recursion depth** — from max iteration count in sample region
   - **Branching factor** — from iteration gradient magnitude
3. Run L-System production rules for N generations
4. Interpret the result string with a turtle graphics renderer
5. Display in a viewer window with the fractal gradient coloring branches by depth

**L-System grammar:**
- Axiom: `F`
- Rule: `F → FF-[-F+F+F]+[+F-F-F]`  (variant selected by iteration stats)
- Symbols: `F`=forward, `+`=right, `-`=left, `[`=push, `]`=pop

## Future Directions

- **Graftal growth** — Use iteration gradient (∇iter) as a flow field for
  particle-based growth. Seed at the fractal boundary, grow outward.
- **Voxel tree from radial slices** — Sweep 2D iteration field around an axis
  to produce a 3D volume; threshold at the fractal boundary to create bark.
- **Terrain + vegetation** — Place L-System trees on the iteration terrain
  at positions where local iteration variance is high (boundary detail).
- **SpeedTree-style parameterization** — Export iteration statistics as a
  parameter vector for external tree generators.

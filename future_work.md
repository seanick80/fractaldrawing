
# Future work

## Poster mode (high-resolution export)
Render the current fractal view at print-quality resolution (e.g., 4000x3000 or larger) with smooth linear (DIVISION) coloring, and export to a file suitable for printing as a wall poster.

## Selection tool
Two selection modes: **rectangle select** (click-drag a bounding box) and **lasso select** (freehand draw an arbitrary closed region). Selected area shown with marching ants outline. Operations on selection:
- **Move** — drag the selected region to reposition it on the canvas (leaves transparent/background behind)
- **Cut** (Ctrl+X) — remove selected pixels to clipboard, fill vacated area with background/transparent
- **Copy** (Ctrl+C) — copy selected pixels to clipboard
- **Paste** (Ctrl+V) — paste clipboard contents as a floating selection that can be positioned before committing
- **Save selection to file** — export the selected region as a standalone image file (PNG)

Should respect the active layer. Lasso selection needs to close the path automatically (connect last point to first on mouse release).

## Drawing tool improvements (backlog)

### Text tool
Text editor with font selection, size, style (bold/italic/underline), color, and alignment options. Click-to-place with optional bounding box for wrapping. Should support common system fonts.

### Paintbrush tool
Brush tool with selectable brush tips (round, square, calligraphy/angled, airbrush/soft), configurable size, opacity, and hardness. Smooth stroke interpolation between mouse samples to avoid gaps at fast movement.

### Object model with non-linear delete (shape selection)
Move from pure raster to a hybrid model where each drawing operation (rectangle, ellipse, line, text, etc.) is recorded as a vector object with its parameters (type, bounds, color, stroke, fill). The canvas composites all objects on repaint. A selection tool allows clicking individual shapes — hit-testing against stored geometry — to select, move, resize, or delete them out of order. This is distinct from linear undo; any shape can be removed at any time and the remaining shapes re-composite. Pairs naturally with the layer system (objects live on layers). Raster operations like pencil/brush strokes and fractals would remain as flat bitmap objects that can be reordered/deleted but not reshaped.

### Line configurability
Enhanced line/stroke settings for all shape and line tools:
- **Width**: Configurable stroke width with live preview (already partially implemented via stroke spinner)
- **Transparency**: Per-stroke alpha/opacity slider (0-100%) so individual strokes can be semi-transparent
- **Dash patterns**: Solid, dashed, dotted, dash-dot with configurable dash lengths
- **Cap/Join styles**: Round, square, butt cap; miter, round, bevel join — exposed in the tool settings panel
- **Arrow heads**: Optional start/end arrow heads for the line tool
- **Stroke textures**: Natural media effects (chalk, charcoal, marker, watercolor) applied along the stroke path. Implemented as BufferedImage-based brush tips stamped at intervals along the path with jitter for a natural look.

### Textures and patterns
Texture system for both strokes and fills:
- **Stroke textures** — natural media brush tips (chalk, charcoal, marker, crayon, watercolor) that replace the solid stroke for pencil, line, rectangle, oval, and brush tools. Tip images stamped along the path with configurable spacing, opacity variance, and rotation jitter.
- **Pattern fills** — tileable patterns (brick, wood, stone, fabric, crosshatch, dots) usable as fill providers for shapes and the fill tool. Registered alongside existing fills (solid, gradient, checkerboard, etc.) via FillRegistry. Users can also load custom pattern images from file.
- Both stroke textures and pattern fills should support colorization (tint the texture with the current foreground color).

## UI fit and finish (backlog)

### FDP project file format
Custom project file format (.fdp) for lossless save/restore of the full application state. Use a proven serialization library (e.g., Google Protobuf) to minimize custom parsing logic and ensure forward compatibility as new features are added.

**Contents:**
- Canvas dimensions
- All layers (pixel data, name, opacity, visibility, lock state, blend mode, order)
- Undo history (up to N states)
- Active tool and tool settings
- Fractal state (type, bounds, iterations, gradient, Julia constant if applicable)
- Selection state (if selection tool is active)

**UI integration:**
- Save/Open dialogs show named file type filters (PNG, JPEG, BMP, FDP) instead of "All Files"
- Selecting FDP preserves full project state; selecting an image format flattens all layers on export
- File > Open auto-detects FDP vs image formats

**Testing:**
- Round-trip serialization/parsing tests for every supported field (layers, undo, fractal settings, gradients, etc.)
- When a new feature adds state to the project (e.g., new tool settings, selection data), a corresponding FDP serialization test case must be added
- Backward compatibility tests: older FDP files missing new fields should load with sensible defaults

### View menu with zoom controls
Add a View menu with: Zoom In, Zoom Out, Zoom to Fit, Reset Zoom (1:1), and Zoom Rectangle (drag a rect on canvas to zoom into that region). Provides keyboard/menu alternatives to mouse wheel zoom.

### Screensaver mode
Full-screen mode (via View menu) that cycles through random interesting Mandelbrot locations. Renders each location at screen resolution, displays for a configurable timer (e.g. 10-30 seconds), then smoothly transitions to the next. Exits on any key press or mouse movement. Reuses the "I Feel Lucky" location finder.

## Animations
*   Iteration animation - add one iteration, display it, add another, etc - up to the point where not many iterations are present anymore. Save the resulting iteration slides as a video which can be played back.
*   Palette cycle animation

My previous version of this app had these features as selectable animations, as a screen saver.

## Fractal types (not yet implemented)

### 3D fractals (would require a new rendering pipeline)
- **Mandelbulb** — 3D extension of the Mandelbrot set using spherical coordinates and a power parameter
- **Mandelbox** — 3D fractal using box-fold and ball-fold transformations; highly configurable scale parameter

### Iterated function system (IFS) fractals
These don't use escape-time iteration — they build geometry via recursive subdivision or transformation rules, so they'd need a separate rendering path:
- **Sierpinski Triangle** — recursively removes the central triangle from subdivided equilateral triangles
- **Sierpinski Carpet** — same idea but with squares; removes the central square at each level
- **Koch Snowflake** — recursively replaces each line segment's middle third with an equilateral triangle bump

### Implementation notes
- Escape-time types use the `FractalType` interface + `FractalTypeRegistry` pattern — implement interface, register, auto-appears in UI
- Perturbation strategies for Burning Ship/Tricorn are a future optimization (currently fall back to BigDecimal at deep zoom)
- Magnet Type II would follow the same pattern as Type I
- IFS fractals need a different renderer since they don't map pixels to escape counts — probably a recursive geometry builder that draws to the canvas directly
- 3D fractals (Mandelbulb, Mandelbox) would need ray-marching or distance-estimation rendering, which is a larger undertaking

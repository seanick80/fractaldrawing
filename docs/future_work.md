
# Future work

## Poster mode (high-resolution export)
Render the current fractal view at print-quality resolution (e.g., 4000x3000 or larger) with smooth linear (DIVISION) coloring, and export to a file suitable for printing as a wall poster.

## Selection, clipboard, and color tools

This group of features is tightly related — selection provides the region, the edit menu provides clipboard operations, and the color tools provide better ways to pick and manage colors.

### Selection tool ✅ DONE
Three selection tools implemented with marching ants, drag-to-move, and floating content:

- **Rectangle select** (SelectionTool) — click-drag a bounding box. Move by dragging inside selection. Floating content auto-commits when starting a new selection.
- **Magic Wand** (MagicWandTool) — flood-fill based selection with configurable tolerance (0-128). Uses bitmap mask with merged-run boundary tracing for marching ants. Drag-to-move support.
- **Lasso** (LassoTool) — freehand draw an arbitrary closed region. Path auto-closes on release. Area-based selection with drag-to-move.

### Edit menu enhancements ✅ DONE
- **Cut** (Ctrl+X), **Copy** (Ctrl+C), **Paste** (Ctrl+V) — system clipboard integration
- **Select All** (Ctrl+A), **Deselect** (Ctrl+D)
- **Save Selection to Image...** — exports as PNG with transparency
- Works with all three selection tools

### Eyedropper tool ✅ DONE
- Left-click → foreground, right-click → background
- Samples from composite (all visible layers)

### Paintbrush tool ✅ DONE
- Soft radial brush with configurable size, opacity, hardness
- Brush shapes: Round, Square, Diamond
- Brush textures: Smooth, Speckle, Chalk, Scatter
- Interpolated stamps along drag path

### Still TODO

#### Eyedropper enhancements
- Settings panel with sampled color swatch, hex/RGB values, sample size toggle
- Alt+click shortcut for temporary eyedropper from any tool

#### Color picker upgrade
Replace the current fixed 20-color palette with a richer color selection system:

**HSB color panel** — a square hue/saturation field (256×256) with a vertical brightness slider beside it. Click or drag in the field to select hue (x-axis) and saturation (y-axis). The brightness slider adjusts value. This provides intuitive visual color selection without opening a dialog.

**Hex/RGB input** — small text field below the HSB panel showing the current color as `#RRGGBB`. Editable — type a hex code and press Enter to set the color. Expandable to show individual R, G, B numeric spinners.

**Swatch palette** — keep a grid of color swatches below the HSB panel:
- **Default swatches**: the current 20 colors (or a better curated set)
- **Recent colors row**: auto-populated with the last 8–12 colors used (drawn or sampled)
- **Custom swatches**: right-click a swatch slot → "Set to current color" to save frequently used colors. Persist across sessions via a simple config file.

**Foreground/background swatches** — keep the current overlapping fg/bg swatch design, but:
- **Single-click** a swatch → open the HSB panel focused on that color (inline, not a dialog)
- **Double-click** a swatch → open the full `JColorChooser` dialog (current behavior, kept as fallback for advanced use)
- **Swap button** (↔ or press X) — swap foreground and background colors
- **Reset button** (small icon) — reset to default black foreground / white background (press D)

#### Text tool
Click on the canvas to place a text cursor. Settings: font family, size, style (bold/italic/underline), alignment. Rasterized onto active layer on commit.

#### Selection resize handles
Drag corner/edge handles to scale the selected region.

### Object model with non-linear delete (shape selection)
Move from pure raster to a hybrid model where each drawing operation (rectangle, ellipse, line, text, etc.) is recorded as a vector object with its parameters (type, bounds, color, stroke, fill). The canvas composites all objects on repaint. A selection tool allows clicking individual shapes — hit-testing against stored geometry — to select, move, resize, or delete them out of order. This is distinct from linear undo; any shape can be removed at any time and the remaining shapes re-composite. Pairs naturally with the layer system (objects live on layers). Raster operations like pencil/brush strokes and fractals would remain as flat bitmap objects that can be reordered/deleted but not reshaped.

### Line configurability
Enhanced line/stroke settings beyond what's currently implemented:
- **Transparency**: Per-stroke alpha/opacity slider (0-100%) so individual strokes can be semi-transparent
- **Cap/Join styles**: Round, square, butt cap; miter, round, bevel join — exposed in the tool settings panel
- **Arrow heads**: Optional start/end arrow heads for the line tool
- **Stroke textures**: Natural media effects (chalk, charcoal, marker, watercolor) applied along the stroke path. Implemented as BufferedImage-based brush tips stamped at intervals along the path with jitter for a natural look.

### Textures and patterns
**Phase 1 (done):** Pattern fills (crosshatch, dot grid, horizontal stripes, noise) and stroke styles (solid, dashed, dotted, dash-dot, rough/sketchy) for all drawing tools (pencil, line, rectangle, oval).

**Phase 2 (done):** Paintbrush tool with shape options (Round/Square/Diamond) and texture options (Smooth/Speckle/Chalk/Scatter) using deterministic pixel hashing for consistent texture effects.

**Phase 3 (future):** Natural media brush tips (charcoal, marker, crayon, watercolor) stamped along the path with configurable spacing, opacity variance, and rotation jitter. Custom pattern image loading from file. Colorization support.

## UI fit and finish (backlog)

### View menu with zoom controls
Add a View menu with: Zoom In, Zoom Out, Zoom to Fit, Reset Zoom (1:1), and Zoom Rectangle (drag a rect on canvas to zoom into that region). Provides keyboard/menu alternatives to mouse wheel zoom.

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

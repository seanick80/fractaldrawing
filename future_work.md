
# Future work

## Poster mode (high-resolution export)
Render the current fractal view at print-quality resolution (e.g., 4000x3000 or larger) with smooth linear (DIVISION) coloring, and export to a file suitable for printing as a wall poster.

## Selection, clipboard, and color tools

This group of features is tightly related — selection provides the region, the edit menu provides clipboard operations, and the color tools provide better ways to pick and manage colors.

### Selection tool
New tool in the toolbar. Two selection modes toggled via the tool settings panel:

**Rectangle select** — click-drag a bounding box. Hold Shift to constrain to square.

**Lasso select** — freehand draw an arbitrary closed region. Path auto-closes on mouse release (connects last point to first). Interior determined by even-odd fill rule.

Selected region shown with **marching ants** outline (animated dashed border via a Timer that cycles the dash offset). The selection is a `Shape` (Rectangle2D or GeneralPath) stored on the tool.

Operations when a selection exists:
- **Move** — drag inside the selection to reposition the selected pixels (leaves transparent behind on the active layer)
- **Resize** — drag corner/edge handles to scale the selected region
- **Delete** — press Delete key to clear selected pixels to transparent
- **Deselect** — press Escape or click outside the selection
- **Select All** (Ctrl+A) — select the entire canvas

Should operate on the active layer only. Selection state is cleared on tool switch.

### Edit menu enhancements
Extend the current Edit menu (Undo/Redo/Clear) with clipboard and selection operations:

- **Cut** (Ctrl+X) — copy selected pixels to an internal clipboard BufferedImage, then clear the selected region on the active layer to transparent
- **Copy** (Ctrl+C) — copy selected pixels to the internal clipboard (also copy to system clipboard if possible via `Toolkit.getDefaultToolkit().getSystemClipboard()`)
- **Paste** (Ctrl+V) — create a floating selection from the clipboard contents, centered on the canvas. The floating selection can be dragged to position before committing (click outside or press Enter to stamp it onto the active layer). Should also accept paste from system clipboard (images copied from other apps)
- **Paste as New Layer** — paste clipboard as a new layer instead of floating selection
- **Select All** (Ctrl+A) — activates the selection tool if not active, selects entire canvas
- **Save Selection...** — export just the selected region as a standalone PNG file

Implementation: `SelectionClipboard` class holds the `BufferedImage` snippet and its position. The `SelectionTool` manages the marching ants, move/resize handles, and floating paste state.

### Eyedropper tool
New tool in the toolbar. Click anywhere on the canvas to sample the pixel color under the cursor:
- **Left-click** → set foreground color
- **Right-click** → set background color

The sampled color should be from the **composite image** (all visible layers flattened), not just the active layer — this matches user expectation of "pick the color I see."

Settings panel shows:
- The sampled color swatch
- Hex value (e.g., `#FF6B2C`)
- RGB values
- Optional: sample size toggle (1×1 pixel or 3×3/5×5 average)

Should update the color picker's foreground/background via `ColorPicker.setForegroundColor()` / `setBackgroundColor()`. Also fire the property change event so any listeners update.

**Keyboard shortcut**: Hold Alt while using any other tool to temporarily switch to eyedropper mode (sample on click, then return to the previous tool). This is the standard behavior in Photoshop/GIMP.

### Color picker upgrade
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

**Implementation notes**: The HSB panel is a custom `JPanel` that renders a `BufferedImage` of the hue-saturation gradient, repaints when brightness changes. The brightness slider is a standard `JSlider` with a custom gradient-painted track. This replaces the simple grid in `ColorPicker.java` but keeps the same external API (`getForegroundColor()`, `setForegroundColor()`, property change events).

### Text tool
Click on the canvas to place a text cursor. A text input area appears (either an overlay `JTextArea` positioned on the canvas, or a dialog). Settings panel provides:
- **Font family** — combo box listing `GraphicsEnvironment.getAvailableFontFamilyNames()`
- **Font size** — spinner (8–200pt)
- **Style** — toggle buttons for Bold, Italic, Underline
- **Color** — uses the current foreground color
- **Alignment** — Left / Center / Right (for multi-line text)

On commit (press Escape or click away), the text is rasterized onto the active layer using `Graphics2D.drawString()` with the configured `Font`. Anti-aliasing via `RenderingHints.KEY_TEXT_ANTIALIASING`. The text is not editable after commit (it becomes pixels) — this matches the raster model.

Optional: click-drag to define a bounding box first, then text wraps within it using `LineBreakMeasurer` and `TextLayout`.

### Paintbrush tool
Brush tool with selectable brush tips (round, square, calligraphy/angled, airbrush/soft), configurable size, opacity, and hardness. Smooth stroke interpolation between mouse samples to avoid gaps at fast movement.

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

**Phase 2 (future):** Natural media brush tips (chalk, charcoal, marker, crayon, watercolor) stamped along the path with configurable spacing, opacity variance, and rotation jitter. Custom pattern image loading from file. Colorization support.

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

# Proposal: Layer System & Object Model (Historical)

> **Status**: Phase 1 (layers, compositing, blend modes, layer panel) is fully implemented. Option B was chosen. Phases 2-4 (object records, selection tool, operation-based undo) remain as future work — see `future_work.md`.

## Problem

The app uses a single `BufferedImage` as the entire canvas. Every tool writes pixels directly to this image. There's no way to select, move, or delete an individual shape after it's drawn. Undo is a stack of full-image snapshots — linear only, memory-heavy, and can't remove operations out of order.

## Goals

1. **Layers** — multiple images composited in order, each with visibility, opacity, blend mode, and lock
2. **Non-linear delete** — select and remove any individual shape/operation, regardless of draw order
3. **Shape selection** — click to select drawn objects; move, resize, delete
4. **Layer properties** — per-layer transparency, blend modes (Normal, Multiply, Screen, Overlay, etc.)
5. **Layer panel** — sidebar UI for reordering, adding, deleting, merging, duplicating layers
6. **Operation-aware undo** — replaces full-image snapshots

---

## Architecture Options

### Option A: Pure Vector Model

Every drawing operation becomes a vector object stored as data. The canvas renders all objects on every repaint by replaying them in order.

**How it works:**
- `DrawOp` hierarchy: `RectOp`, `EllipseOp`, `LineOp`, `PencilStrokeOp` (list of points), `FillOp` (mask + paint), `FractalOp` (rendered image), `EraserOp` (mask)
- Each op stores: geometry, color, stroke, fill, layer ID, z-order
- Canvas iterates all ops per layer per frame, calls `op.paint(Graphics2D)`
- Selection = hit-test against stored geometry
- Delete = remove op from list, repaint
- Move/resize = mutate op geometry, repaint

**Pros:**
- True non-linear editing — any op can be modified or removed at any time
- Infinite undo by replaying subsets of ops
- Small memory footprint (ops are lightweight data)
- Natural serialization (save/load as structured data)

**Cons:**
- **Performance cliff**: Canvas must replay *every* operation on every repaint. A complex drawing with thousands of pencil strokes (each storing hundreds of points) becomes very slow.
- **Raster fidelity**: Pencil strokes, eraser, and flood fill are fundamentally raster operations. Storing them as vector data (point lists, masks) is awkward and loses the natural "paint on pixels" model.
- **Fractal renders**: A fractal is a 1M+ pixel computation. Storing it as a vector op that re-renders on every repaint is not viable; it must cache a raster result.
- **Complexity**: Every tool must be rewritten to produce ops instead of drawing pixels.

**Verdict:** Not a good fit. Too many operations are inherently raster. Performance degrades with drawing complexity.

---

### Option B: Object-Oriented Raster Layers (Recommended)

Each layer holds both a `BufferedImage` (the raster content) and an ordered list of `DrawingObject` metadata records. Shape tools create objects that can be selected and manipulated. Raster tools (pencil, eraser, fill) write directly to the layer's image and create a lightweight "raster region" record for undo purposes.

**How it works:**

```
Layer
├── BufferedImage image          // the actual pixels
├── List<DrawingObject> objects  // metadata for selection/delete
├── float opacity                // 0.0–1.0
├── BlendMode blendMode          // Normal, Multiply, Screen, etc.
├── boolean visible
├── boolean locked
└── String name
```

```
DrawingObject (interface)
├── ShapeObject          // rect, ellipse, line — stores geometry + style
├── RasterRegion         // pencil stroke, eraser, fill — stores bounding rect + pixel snapshot
└── ImageObject          // fractal render, pasted image — stores full BufferedImage
```

**Drawing flow:**
1. Tool draws to the active layer's `BufferedImage` (same as today)
2. Tool also registers a `DrawingObject` with the layer
3. Canvas composites all visible layers in order on repaint

**Selection & delete flow:**
1. Selection tool iterates objects on the active layer in reverse z-order
2. For `ShapeObject`: hit-test against stored geometry (point-in-rect, point-on-line, etc.)
3. For `RasterRegion`: hit-test against bounding box, then check alpha of stored pixels
4. **Delete**: Remove object from list. Rebuild layer image by replaying remaining objects.
5. **Move**: For shapes, update geometry and redraw. For raster regions, erase old position, blit pixels at new position.

**Layer compositing:**
```java
void paintComponent(Graphics g) {
    BufferedImage composite = new BufferedImage(w, h, ARGB);
    Graphics2D cg = composite.createGraphics();
    for (Layer layer : layers) {
        if (!layer.isVisible()) continue;
        cg.setComposite(layer.getAlphaComposite());  // opacity + blend mode
        cg.drawImage(layer.getImage(), 0, 0, null);
    }
    cg.dispose();
    g.drawImage(composite, ...);  // with view zoom/pan
}
```

**Pros:**
- Raster tools work exactly as they do today — just targeting a layer image instead of the canvas image
- Shape objects are selectable/movable/deletable with true vector metadata
- Raster regions support delete (replay remaining objects) and move (blit pixels)
- Compositing is fast (one `drawImage` per visible layer per frame)
- Memory is proportional to layer count, not operation count
- Incremental migration — existing tools need minimal changes

**Cons:**
- Deleting a raster region requires replaying all prior objects on that layer to rebuild pixels (can be slow for complex layers, but only happens on delete, not on every frame)
- Moving raster content requires pixel-level manipulation (erase + blit)
- More complex undo than pure snapshots (but more capable)

---

### Option C: Hybrid Vector + Cached Raster

Like Option B, but shape objects are *not* immediately rasterized — they remain as live vector data, rendered on each paint, and only rasterized when the layer is "flattened" or merged. Raster tools still write to the layer image directly.

**How it works:**
- A layer has two sublayers: a `BufferedImage` for raster content, and a `List<VectorShape>` for live shapes
- On paint: draw the raster image first, then paint vector shapes on top
- Shapes remain editable (resize, recolor, move) without any replay cost
- Rasterize shapes into the image on flatten/merge/export

**Pros:**
- Shape editing is truly non-destructive — change stroke color, fill, size at any time
- No replay cost for shape manipulation
- Clean separation of raster and vector concerns

**Cons:**
- **Z-order complexity**: Raster strokes and vector shapes can interleave. If you draw a rectangle, then a pencil stroke over it, then another rectangle, the z-ordering requires splitting the raster content around vector shapes. This gets messy fast.
- **Two rendering paths**: Canvas must handle both raster compositing and vector rendering
- **Selection ambiguity**: Clicking might hit a vector shape or raster content underneath — more complex hit-testing
- **Export**: Must flatten correctly to produce final image

**Verdict:** Elegant for shape-only workflows, but the z-order interleaving with raster content creates real complexity. Could work if we constrain each layer to be *either* raster or vector, not both.

---

## Recommendation: Option B (Object-Oriented Raster Layers)

Option B strikes the best balance for this app:

1. **Minimal tool disruption** — existing tools keep drawing to a `BufferedImage`; we just redirect which image they target
2. **Full layer support** — opacity, blend modes, visibility, reordering
3. **Non-linear delete** — works for both shapes and raster operations via object records + replay
4. **Practical performance** — compositing is fast; replay only happens on delete
5. **Natural upgrade path** — can later add Option C's live vector shapes as an enhancement

---

## Blend Modes

Java's `AlphaComposite` covers basic transparency but not Photoshop-style blend modes. We'd implement custom `Composite`/`CompositeContext` classes for:

| Mode | Effect |
|------|--------|
| Normal | Standard alpha compositing (built-in) |
| Multiply | Darkens — `result = src × dst` |
| Screen | Lightens — `result = 1 - (1-src)(1-dst)` |
| Overlay | Multiply dark, screen light |
| Soft Light | Gentle contrast adjustment |
| Hard Light | Intense contrast |
| Difference | `result = |src - dst|` |
| Add (Linear Dodge) | Brighten by addition, clamped |

Each is ~20 lines of pixel math in a `CompositeContext.compose()` override.

---

## Undo System

Replace full-image snapshots with an **operation log**:

```
UndoOp (interface)
├── AddObjectOp      // shape/raster added to layer
├── RemoveObjectOp   // object deleted
├── MoveObjectOp     // object moved (stores old + new position)
├── ModifyObjectOp   // property changed (stores old + new values)
├── LayerOp          // layer added/removed/reordered/merged
├── LayerPropertyOp  // opacity/blend/visibility changed
```

Each `UndoOp` has `undo()` and `redo()` methods. The undo manager holds a list of ops with a cursor.

For raster operations (pencil, eraser), the `AddObjectOp` stores the `RasterRegion` which contains the pixel snapshot of the affected area. Undo = remove the region and restore the underlying pixels. This is more memory-efficient than full-image snapshots because it only stores the changed rectangle, not the entire canvas.

---

## Layer Panel UI

Sidebar panel (below or replacing part of the current tool settings area):

```
┌─ Layers ──────────────┐
│ [+] [-] [⬆] [⬇] [M]  │  ← add, delete, move up/down, merge
│                        │
│ 👁 🔒 ▓▓▓ Layer 3     │  ← visibility, lock, thumbnail, name
│ 👁    ▓▓▓ Layer 2     │     (selected = highlighted)
│ 👁    ▓▓▓ Layer 1     │
│                        │
│ Opacity: [====●===] 80%│
│ Blend:  [Normal    ▼] │
└────────────────────────┘
```

- Click layer row to select active layer
- Double-click name to rename
- Drag rows to reorder (or use up/down buttons)
- Eye icon toggles visibility
- Lock icon prevents edits
- Small thumbnail preview per layer
- Opacity slider and blend mode dropdown for selected layer

---

## Implementation Phases

### Phase 1: Layer Infrastructure (foundation)
- `Layer` class with BufferedImage, opacity, blend mode, visibility, lock, name
- `LayerManager` to hold ordered list, active layer, add/remove/reorder
- Modify `DrawingCanvas` to composite layers instead of painting single image
- Modify all tools to draw to `layerManager.getActiveLayer().getImage()` instead of `canvas.getImage()`
- Layer panel UI (basic: list, add, delete, select, visibility toggle)
- Opacity slider and blend mode dropdown
- Custom `BlendComposite` for Multiply, Screen, Overlay, etc.
- **Test**: All existing tools work identically on default single layer

### Phase 2: Object Records & Selection Tool
- `DrawingObject` interface with `getBounds()`, `contains(x, y)`, `paint(Graphics2D)`
- `ShapeObject` for rect, ellipse, line — stores geometry + style
- `RasterRegion` for pencil, eraser, fill — stores bounding box + pixel snapshot
- `ImageObject` for fractal renders, pasted images
- Modify shape tools to register `ShapeObject` with layer after drawing
- Modify raster tools to register `RasterRegion` with layer after drawing
- New `SelectionTool` — click to select, show handles, delete key removes
- Hit-testing: iterate objects in reverse z-order
- **Test**: Can draw shapes, select them, delete them independently

### Phase 3: Operation-Based Undo
- `UndoOp` interface with `undo()` / `redo()`
- Concrete ops: AddObject, RemoveObject, MoveObject, LayerProperty, LayerAdd/Remove
- Replace `UndoManager` image-snapshot system with op-log system
- Wire all tools and layer operations to emit undo ops
- **Test**: Undo/redo works correctly across layers and object operations

### Phase 4: Selection Tool Enhancements
- Move selected objects (drag)
- Resize handles (corner/edge drag)
- Multi-select (Shift+click, drag box)
- Copy/paste objects (within layer or across layers)
- Properties panel for selected object (change color, stroke, fill)

---

## Migration Risk

**Low risk for Phase 1** — the key insight is that a single-layer app is just a layer manager with one layer. All existing tools work unchanged if we redirect `canvas.getImage()` → `layerManager.getActiveLayer().getImage()`. We can ship Phase 1 with zero visible behavior change (single default layer) and add multi-layer UI on top.

**Medium risk for Phase 2** — shape tools need to emit `DrawingObject` records alongside their normal pixel drawing. This is additive, not a rewrite. The `SelectionTool` is new code.

**Low risk for Phase 3** — undo refactor is isolated to `UndoManager` and the call sites that invoke it. The old system can remain as fallback during development.

---

## Decisions (2026-03-28)

1. **Layer limit**: 20 layers max.
2. **Undo scope**: Global undo (Photoshop-style), not per-layer.
3. **Raster tools**: Modify the active layer's raster only. Not individually selectable — baked into the layer image.
4. **Save formats**:
   - **PNG/JPEG/BMP**: Flatten all layers to single image on save.
   - **FDP (Fractal Drawing Project)**: New custom format preserving all layers, object records, and up to 10 undo states.
5. **Undo compaction**: After 80 undo ops accumulate, checkpoint to disk and compact down to 50. Prevents unbounded memory growth during long editing sessions.

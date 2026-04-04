# Refactoring Options: Drawing App

## What We Should Have Done First

Before writing any code, we should have defined **three interface contracts** and stuck to them:

1. **Tool capability interface** — Tools declare what they support (stroke size, fill, gradient) instead of the toolbar discovering capabilities via `instanceof`
2. **Settings panel ownership** — Each tool builds and owns its entire settings UI. The toolbar just hosts it. No shared mutable components.
3. **Event bus over direct wiring** — Components communicate through typed events, not by holding references to each other and calling methods directly

The absence of these contracts led to a codebase where ToolBar knows about every tool type, FractalTool is 660 lines of tangled UI/rendering/IO, and adding a new tool requires editing 3+ files.

---

## Proposal 1: Tool Capability Interface

**Problem**: ToolBar has 5 `instanceof` checks in `applyStrokeSize()` and 3 in `applyFillSettings()` to dispatch settings to tools. Every new tool that supports stroke size or fills requires editing ToolBar. The fill options panel in ToolBar contains 130 lines of UI code for gradient preview, angle dial, and custom gradient detection — none of which belongs there.

**Current code (ToolBar.java:402-427)**:
```java
private void applyStrokeSize() {
    int size = (int) strokeSpinner.getValue();
    if (activeTool instanceof PencilTool pt) pt.setStrokeSize(size);
    if (activeTool instanceof LineTool lt) lt.setStrokeSize(size);
    if (activeTool instanceof RectangleTool rt) rt.setStrokeSize(size);
    if (activeTool instanceof OvalTool ot) ot.setStrokeSize(size);
    if (activeTool instanceof EraserTool et) et.setSize(size);
}

private void applyFillSettings() {
    if (activeTool instanceof RectangleTool rt) { rt.setFilled(...); rt.setFillProvider(...); }
    if (activeTool instanceof OvalTool ot) { ot.setFilled(...); ot.setFillProvider(...); }
    if (activeTool instanceof FillTool ft) { ft.setFillProvider(...); }
}
```

**Proposed change**: Add capability methods to the `Tool` interface:

```java
public interface Tool {
    // Existing methods...

    /** Tools that support stroke size return true and implement setStrokeSize. */
    default boolean hasStrokeSize() { return false; }
    default void setStrokeSize(int size) {}
    default int getDefaultStrokeSize() { return 2; }

    /** Tools that support fill return true and implement setFilled/setFillProvider. */
    default boolean hasFill() { return false; }
    default void setFilled(boolean filled) {}
    default void setFillProvider(FillProvider provider) {}
}
```

Then ToolBar becomes:
```java
private void applyStrokeSize() {
    int size = (int) strokeSpinner.getValue();
    if (activeTool.hasStrokeSize()) activeTool.setStrokeSize(size);
}

private void applyFillSettings() {
    if (activeTool.hasFill()) {
        activeTool.setFilled(filledCheck.isSelected());
        activeTool.setFillProvider(fillRegistry.getByName(...));
    }
}
```

**Files changed**: `Tool.java`, `PencilTool.java`, `LineTool.java`, `RectangleTool.java`, `OvalTool.java`, `EraserTool.java`, `FillTool.java`, `ToolBar.java`

**Instanceof checks eliminated**: 8 (all of `applyStrokeSize` + `applyFillSettings`)

**Risk**: Low. Each tool adds 2-3 override methods. ToolBar loses ~20 lines of dispatch code.

**Impact**: Adding a new tool with stroke/fill support requires zero ToolBar changes.

---

## Proposal 2: Tool-Owned Settings Panels (Dissolve the ToolBar God Class)

**Problem**: ToolBar (430 lines) builds and manages shared UI components (stroke spinner, stroke preview, fill checkbox, fill combo, gradient preview, angle dial) and swaps them into a container when tools change. This creates two problems:

1. **Shared mutable UI state** — The stroke spinner, fill combo, and gradient preview are single instances reused across tools. Switching tools means saving/restoring spinner values, syncing combo selections, and toggling visibility of sub-components. This is the source of bugs like "fill tool overwrites fractal gradient" and "gradient preview doesn't update."

2. **ToolBar knows too much** — It imports every tool class, every fill class, and the gradient toolbar. The `buildFillOptionsPanel()` method alone is 130 lines because it handles CustomGradientFill detection, AngledFillProvider angle dials, and gradient preview painting.

**Proposed change**: Each tool builds its **complete** settings panel. ToolBar just hosts it.

```java
// Tool interface — already has this, but make it the ONLY way settings work
public interface Tool {
    /** Build the complete settings panel for this tool. Called once on activation. */
    JPanel createSettingsPanel();
}

// PencilTool builds its own stroke size spinner
public class PencilTool implements Tool {
    public JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        // stroke spinner, preview — self-contained
        return panel;
    }
}

// RectangleTool builds stroke + fill + gradient controls
public class RectangleTool implements Tool {
    public JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        // stroke spinner, fill checkbox, fill combo, angle dial — all self-contained
        return panel;
    }
}
```

**ToolBar becomes ~100 lines**: tool buttons, tool registry, and a container that calls `activeTool.createSettingsPanel()`.

**Shared UI helpers** (extracted, not shared instances):
```java
public class ToolSettingsBuilder {
    public static JSpinner createStrokeSpinner(int defaultSize, Consumer<Integer> onChange) { ... }
    public static JPanel createStrokePreview(Supplier<Integer> sizeSupplier) { ... }
    public static JPanel createFillOptions(FillRegistry registry, ...) { ... }
}
```

**Files changed**: `ToolBar.java` (major reduction), all tool files (gain self-contained settings), new `ToolSettingsBuilder.java` utility

**Lines eliminated from ToolBar**: ~250 (buildStrokeSpinner, buildStrokePreview, buildStrokeSizePanel, buildFillCombo, buildFillOptionsPanel, applyStrokeSize, applyFillSettings, syncGradientToolbar)

**Risk**: Medium. Larger refactor, but each tool becomes self-contained and testable in isolation. The shared-mutable-spinner bugs disappear entirely because each tool has its own spinner instance.

**Impact**: Adding a new tool requires editing exactly one file: the new tool class. ToolBar never changes.

---

## Proposal 3: Split FractalTool into Rendering Core vs. UI Controller

**Problem**: FractalTool is 660 lines doing six distinct jobs:

| Responsibility | Lines | Should Be |
|---|---|---|
| Settings panel UI | ~200 | FractalSettingsPanel |
| Fractal menu construction | ~130 | FractalMenuBuilder |
| Mouse interaction (zoom/pan) | ~90 | FractalTool (keep) |
| Async render orchestration | ~70 | FractalRenderController |
| Color listener / gradient sync | ~40 | Part of render controller |
| Location/animation delegation | ~30 | Already delegated (good) |

The settings panel method `createSettingsPanel()` alone builds 12 buttons, 2 spinners, 2 labels, a gradient preview, and wires 8 action listeners. The menu builder creates a 50-item menu with presets, coloring modes, and saved location scanning. These are interleaved with rendering state (`lastImage`, `lastCanvas`, `currentWorker`).

**Consequence**: You can't test fractal rendering without constructing Swing components. You can't modify the settings panel without risk of breaking the render pipeline. The gradient sync (`onGradientChanged`, color listener) is tangled with UI callbacks.

**Proposed change**: Three classes instead of one:

```
FractalTool (mouse interaction + tool lifecycle, ~120 lines)
├── FractalRenderController (async rendering + gradient sync, ~150 lines)
├── FractalSettingsPanel (UI construction, ~200 lines)
└── FractalMenuBuilder (menu construction, ~130 lines)
```

**FractalTool** keeps: `mousePressed`, `mouseDragged`, `mouseReleased`, `mouseWheelMoved`, `onActivated`, `onDeactivated`, `getName`, `getMenu` (delegates to builder).

**FractalRenderController** gets: `renderAsync`, `triggerRender`, `setRenderButtonCancel/Idle`, `currentWorker`, `lastImage`, `lastCanvas`, color listener registration, gradient change callback. Exposes `render()` and `cancel()`.

**FractalSettingsPanel** gets: all of `createSettingsPanel()` internals. Takes a `FractalRenderController` reference for button wiring. Returns a `JPanel`.

**FractalMenuBuilder** gets: `buildFractalMenu()`, `addPreset()`, `formatTypeName()`, location scanning. Takes a `FractalRenderer` + `FractalRenderController` for menu action wiring.

**Files changed**: `FractalTool.java` (major reduction), new `FractalRenderController.java`, new `FractalSettingsPanel.java`, new `FractalMenuBuilder.java`

**Risk**: Medium. The four inner helper classes (FractalAnimationController, FractalLocationManager, FractalInfoPanel) already exist as extractions — this continues that pattern. The key challenge is threading the render controller through settings and menu without recreating tight coupling.

**Impact**: FractalTool drops from 660 to ~120 lines. Rendering becomes testable without Swing. Settings panel changes don't risk render bugs.

---

## Comparison

| | Proposal 1: Capability Interface | Proposal 2: Tool-Owned Panels | Proposal 3: Split FractalTool |
|---|---|---|---|
| **Effort** | Small (1-2 hours) | Medium (half day) | Medium (half day) |
| **instanceof eliminated** | 8 | 8 + all fill-specific checks | 0 (different problem) |
| **Lines reduced** | ~20 from ToolBar | ~250 from ToolBar | ~540 from FractalTool |
| **Files touched** | 8 | 10+ | 5 |
| **Bug class eliminated** | Tool dispatch errors | Shared-mutable-state bugs | Render/UI entanglement |
| **Prerequisite** | None | Proposal 1 | None |
| **New tool effort after** | 0 ToolBar edits | 0 ToolBar edits | N/A (fractal-specific) |

**Recommended order**: 1 → 2 → 3. Proposal 1 is low-risk and unblocks Proposal 2. Proposal 3 is independent and can happen in parallel.

# Drawing App

A simple MS Paint-style drawing application built with Java Swing and Graphics2D.

## Features

- **Drawing tools**: Pencil, Line, Rectangle, Oval, Eraser, Flood Fill
- **Color picker**: 20-color palette + custom color chooser, foreground/background colors
- **Pluggable fill system**: Solid, Gradient, Checkerboard, Diagonal Stripes (extensible)
- **Undo/Redo**: Up to 50 levels (Ctrl+Z / Ctrl+Y)
- **File I/O**: Open and save PNG, JPG, BMP images

## Build & Run

Requires Java 17+.

```bash
# Unix/Git Bash
./build.sh run

# Windows
build.cmd run
```

## Adding Custom Fills

1. Create a class implementing `FillProvider` in `src/com/seanick80/drawingapp/fills/`
2. Register it in `DrawingApp.registerDefaultFills()`

Example:
```java
public class MyCustomFill implements FillProvider {
    @Override public String getName() { return "My Fill"; }

    @Override
    public Paint createPaint(Color baseColor, int x, int y, int w, int h) {
        // Return any java.awt.Paint — Color, GradientPaint, TexturePaint, etc.
        return new GradientPaint(x, y, baseColor, x + w, y, Color.WHITE);
    }
}
```

#!/bin/bash
# Build and run fractal regression tests
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASEDIR/src"
OUT="$BASEDIR/out"

mkdir -p "$OUT"

echo "Compiling..."
find "$SRC" -name "*.java" | sed 's|^/\([a-zA-Z]\)/|\1:/|' > "$OUT/sources.txt"
javac -d "$OUT" @"$OUT/sources.txt"

echo "Running tests..."
java -ea -cp "$OUT" com.seanick80.drawingapp.fractal.FractalRenderTest

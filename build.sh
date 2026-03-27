#!/bin/bash
# Build and optionally run the Drawing App
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASEDIR/src"
OUT="$BASEDIR/out"

mkdir -p "$OUT"

echo "Compiling..."
find "$SRC" -name "*.java" | sed 's|^/\([a-zA-Z]\)/|\1:/|' > "$OUT/sources.txt"
javac -d "$OUT" @"$OUT/sources.txt"

echo "Build successful."

if [ "$1" = "run" ]; then
    echo "Starting Drawing App..."
    java -cp "$OUT" com.seanick80.drawingapp.DrawingApp \
        --gradient-dir "$BASEDIR/data/gradients" \
        --location-dir "$BASEDIR/data/locations"
fi

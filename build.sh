#!/bin/bash
# Build and optionally run the Drawing App
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASEDIR/src"
OUT="$BASEDIR/out"

mkdir -p "$OUT"

echo "Compiling..."
javac -d "$OUT" $(ls "$SRC"/com/seanick80/drawingapp/*.java "$SRC"/com/seanick80/drawingapp/tools/*.java "$SRC"/com/seanick80/drawingapp/fills/*.java)

echo "Build successful."

if [ "$1" = "run" ]; then
    echo "Starting Drawing App..."
    java -cp "$OUT" com.seanick80.drawingapp.DrawingApp
fi

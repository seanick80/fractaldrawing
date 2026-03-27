#!/bin/bash
# Run the Drawing App (build first with build.sh if needed)
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$BASEDIR/out"

if [ ! -d "$OUT/com" ]; then
    echo "No build found, building first..."
    "$BASEDIR/build.sh"
fi

java -cp "$OUT" com.seanick80.drawingapp.DrawingApp \
    --gradient-dir "$BASEDIR/data/gradients" \
    --location-dir "$BASEDIR/data/locations"

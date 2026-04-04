#!/bin/bash
# Run the Drawing App (build first with build.sh if needed)
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$BASEDIR/out"
LIB="$BASEDIR/lib"

if [ ! -d "$OUT/com" ]; then
    echo "No build found, building first..."
    "$BASEDIR/build.sh"
fi

# Build classpath from all JARs in lib/
CP=""
for jar in "$LIB"/*.jar; do
    [ -f "$jar" ] || continue
    if [ -n "$CP" ]; then CP="$CP;$jar"; else CP="$jar"; fi
done

java -cp "$OUT;$CP" com.seanick80.drawingapp.DrawingApp \
    --gradient-dir "$BASEDIR/data/gradients" \
    --location-dir "$BASEDIR/data/locations"

#!/bin/bash
# Build and optionally run the Drawing App
set -e

# Convert /c/path to C:/path for Java on Windows
winpath() { echo "$1" | sed 's|^/\([a-zA-Z]\)/|\1:/|'; }

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASEDIR/src"
OUT="$(winpath "$BASEDIR/out")"
LIB="$BASEDIR/lib"

# Build classpath from all JARs in lib/
CP=""
for jar in "$LIB"/*.jar; do
    [ -f "$jar" ] || continue
    jar_win="$(winpath "$jar")"
    if [ -n "$CP" ]; then CP="$CP;$jar_win"; else CP="$jar_win"; fi
done

mkdir -p "$OUT"

echo "Compiling..."
find "$SRC" -name "*.java" | sed 's|^/\([a-zA-Z]\)/|\1:/|' > "$OUT/sources.txt"
if [ -n "$CP" ]; then
    javac -d "$OUT" -cp "$CP" @"$OUT/sources.txt"
else
    javac -d "$OUT" @"$OUT/sources.txt"
fi

echo "Build successful."

if [ "$1" = "run" ]; then
    echo "Starting Drawing App..."
    java -cp "$OUT;$CP" com.seanick80.drawingapp.DrawingApp \
        --gradient-dir "$(winpath "$BASEDIR/data/gradients")" \
        --location-dir "$(winpath "$BASEDIR/data/locations")"
fi

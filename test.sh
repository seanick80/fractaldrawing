#!/bin/bash
# Build and run fractal regression tests
set -e

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASEDIR/src"
OUT="$BASEDIR/out"
LIB="$BASEDIR/lib"

# Build classpath from all JARs in lib/
CP=""
for jar in "$LIB"/*.jar; do
    [ -f "$jar" ] || continue
    if [ -n "$CP" ]; then CP="$CP;$jar"; else CP="$jar"; fi
done

mkdir -p "$OUT"

echo "Compiling..."
find "$SRC" -name "*.java" | sed 's|^/\([a-zA-Z]\)/|\1:/|' > "$OUT/sources.txt"
if [ -n "$CP" ]; then
    javac -d "$OUT" -cp "$CP" @"$OUT/sources.txt"
else
    javac -d "$OUT" @"$OUT/sources.txt"
fi

echo "Running tests..."
java -ea -cp "$OUT;$CP" com.seanick80.drawingapp.fractal.FractalRenderTest

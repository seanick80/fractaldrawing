#!/bin/bash
# Build and run tests
# Usage: ./test.sh [small|medium|large|parser|all]
set -euo pipefail

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

MODE="${1:-all}"

# JUnit 5 console launcher
JUNIT_JAR="$(winpath "$LIB/junit-platform-console-standalone-1.11.4.jar")"
if [ ! -f "$LIB/junit-platform-console-standalone-1.11.4.jar" ]; then
    echo "ERROR: JUnit JAR not found."
    exit 1
fi

TAG_FILTER=""
case "$MODE" in
    small)   TAG_FILTER="--include-tag=small" ;;
    medium)  TAG_FILTER="--include-tag=medium" ;;
    large)   TAG_FILTER="--include-tag=large" ;;
    parser)  TAG_FILTER="--include-tag=parser" ;;
    all)     TAG_FILTER="" ;;
    *)       echo "Usage: $0 [small|medium|large|parser|all]"; exit 1 ;;
esac

echo "Running JUnit tests${TAG_FILTER:+ (filter: $TAG_FILTER)}..."
java -ea -jar "$JUNIT_JAR" execute \
    --class-path "$OUT;$CP" \
    --scan-class-path \
    $TAG_FILTER \
    --details=verbose

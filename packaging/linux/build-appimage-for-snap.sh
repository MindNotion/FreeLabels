#!/bin/bash
# Prepares build/appimage/FreeLabels/ - a self-contained folder with a
# bundled JRE - for snapcraft to package as a .snap. Run this BEFORE
# `snapcraft` (or `snapcraft pack`).
#
# Requirements: JDK 17+ (for mvn package and jpackage), on Linux.

set -euo pipefail
cd "$(dirname "$0")/../.."   # repo root

MAIN_JAR="freelabels.jar"
MAIN_CLASS="com.freelabels.LabelApp"
ICON="packaging/icons/icon.png"
JAR_PATH="target/${MAIN_JAR}"
INPUT_DIR="build/appimage-input"
OUT_DIR="build/appimage"

if [ ! -f "$JAR_PATH" ]; then
  echo "Building $JAR_PATH first..."
  mvn package
fi

rm -rf "$INPUT_DIR" "$OUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$JAR_PATH" "$INPUT_DIR/"

jpackage \
  --type app-image \
  --input "$INPUT_DIR" \
  --dest "$OUT_DIR" \
  --name FreeLabels \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --app-version 1.0.0 \
  --vendor "MindNotion" \
  --icon "$ICON"

echo
echo "Built: $OUT_DIR/FreeLabels/  (bundled-JRE app image, ready for snapcraft)"
echo "Next: snapcraft   # reads snap/snapcraft.yaml, stages this folder"

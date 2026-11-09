#!/bin/bash
# Builds a native Linux installer for FreeLabels (.deb by default, or .rpm).
#
# Requirements on the build machine:
#   - JDK 17+ (jpackage ships with the JDK since Java 14)
#   - For .deb: dpkg-deb, fakeroot  (Debian/Ubuntu: apt install fakeroot)
#   - For .rpm: rpmbuild            (Fedora/RHEL, or Debian/Ubuntu: apt install rpm)
#
# Usage:
#   cd freelabels-swing
#   mvn package                          # produces target/freelabels.jar
#   ./packaging/linux/build-installer.sh [deb|rpm]   # default: deb
#
# This exact command (with --type deb) was run and verified in development:
# it produces an installable package containing a bundled private JRE
# (via jlink), a native launcher binary, a desktop menu entry, and the
# app icon — end users do NOT need Java installed separately.

set -euo pipefail
cd "$(dirname "$0")/../.."   # repo root

TYPE="${1:-deb}"
APP_NAME="FreeLabels"
APP_VERSION="2.4.0"
VENDOR="MindNotion"
MAIN_JAR="freelabels.jar"
MAIN_CLASS="com.freelabels.LabelApp"
ICON="packaging/icons/icon.png"
JAR_PATH="target/${MAIN_JAR}"
INPUT_DIR="target/jpackage-input"
OUT_DIR="target/installer"

if [ ! -f "$JAR_PATH" ]; then
  echo "Error: $JAR_PATH not found. Run 'mvn package' first." >&2
  exit 1
fi

rm -rf "$INPUT_DIR" "$OUT_DIR"
mkdir -p "$INPUT_DIR" "$OUT_DIR"
cp "$JAR_PATH" "$INPUT_DIR/"

jpackage \
  --type "$TYPE" \
  --input "$INPUT_DIR" \
  --dest "$OUT_DIR" \
  --name "$APP_NAME" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --description "FreeLabels - Free Label Creation Platform" \
  --icon "$ICON" \
  --linux-shortcut \
  --linux-menu-group "Office"

echo
echo "Built: $(find "$OUT_DIR" -maxdepth 1 -type f)"

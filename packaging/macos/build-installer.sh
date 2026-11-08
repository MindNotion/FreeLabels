#!/bin/bash
# Builds a native macOS installer for FreeLabels (.dmg by default, or .pkg).
#
# Requirements on the build machine (must be run ON macOS - jpackage
# cannot cross-compile installers for a different OS):
#   - JDK 17+ (jpackage ships with the JDK since Java 14)
#   - Xcode command line tools (`xcode-select --install`) for the native
#     packaging tools jpackage shells out to (pkgbuild/productbuild, hdiutil).
#
# This produces an UNSIGNED app. Gatekeeper will show an "unidentified
# developer" warning on first launch (right-click -> Open bypasses it).
# To distribute without that warning you'd add --mac-sign plus a Developer
# ID certificate/keychain (Apple Developer Program membership required) -
# not included here since it requires your own Apple credentials.
#
# Usage:
#   cd freelabels-swing
#   mvn package
#   ./packaging/macos/build-installer.sh [dmg|pkg]

set -euo pipefail
cd "$(dirname "$0")/../.."

TYPE="${1:-dmg}"
APP_NAME="FreeLabels"
APP_VERSION="2.0.0"
VENDOR="MindNotion"
MAIN_JAR="freelabels.jar"
MAIN_CLASS="com.freelabels.LabelApp"
ICON="packaging/icons/icon.icns"
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
  --mac-package-name "$APP_NAME"

echo
echo "Built: $(find "$OUT_DIR" -maxdepth 1 -type f)"

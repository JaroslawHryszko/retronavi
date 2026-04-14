#!/bin/bash
#
# Builds APK and creates a GitHub release.
# Usage: ./release.sh v1.2 "Release notes here"
#
# The APK is uploaded as "retronavi.apk" so the in-app updater
# (RetroNaviUpdater.java) can find it at:
# https://github.com/JaroslawHryszko/retronavi/releases/latest/download/retronavi.apk

set -e

VERSION="$1"
NOTES="$2"

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version-tag> [release-notes]"
    echo "Example: $0 v1.2 \"Bug fixes and performance improvements\""
    exit 1
fi

if [ -z "$NOTES" ]; then
    NOTES="RetroNavi $VERSION"
fi

echo "Building APK..."
cd "$(dirname "$0")/navit"
./gradlew assembleDebug

APK="android/build/outputs/apk/debug/android-debug.apk"
if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi

echo "APK built: $(du -h "$APK" | cut -f1)"

# Copy with the name the updater expects
cp "$APK" /tmp/retronavi.apk

echo "Creating GitHub release $VERSION..."
cd "$(dirname "$0")/.."
gh release create "$VERSION" /tmp/retronavi.apk \
    --title "RetroNavi $VERSION" \
    --notes "$NOTES"

rm /tmp/retronavi.apk

echo "Done. Release: https://github.com/JaroslawHryszko/retronavi/releases/tag/$VERSION"

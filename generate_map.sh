#!/bin/bash
# generate_map.sh - generate RetroNavi map from OpenStreetMap data
#
# Usage:
#   ./generate_map.sh poland          # generate Poland map
#   ./generate_map.sh germany         # generate Germany map
#   ./generate_map.sh europe          # generate Europe map (warning: huge)
#
# Downloads OSM PBF from Geofabrik, converts to Navit binfile format.
# Result: maps/<region>.bin
#
# Prerequisites:
#   - build maptool first: cd build-maptool && make -j$(nproc)
#   - osmconvert (sudo apt install osmctools)

set -e

MAPTOOL="$(dirname "$0")/build-maptool/maptool"
WORKDIR="/tmp/retronavi-mapgen"
OUTDIR="$(dirname "$0")/maps"

if [ ! -x "$MAPTOOL" ]; then
    echo "maptool not found. Build it first:"
    echo "  cd build-maptool && make -j\$(nproc)"
    exit 1
fi

REGION="${1:-poland}"

# Geofabrik download URLs
case "$REGION" in
    poland)     URL="https://download.geofabrik.de/europe/poland-latest.osm.pbf" ;;
    germany)    URL="https://download.geofabrik.de/europe/germany-latest.osm.pbf" ;;
    france)     URL="https://download.geofabrik.de/europe/france-latest.osm.pbf" ;;
    spain)      URL="https://download.geofabrik.de/europe/spain-latest.osm.pbf" ;;
    italy)      URL="https://download.geofabrik.de/europe/italy-latest.osm.pbf" ;;
    czech)      URL="https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf" ;;
    slovakia)   URL="https://download.geofabrik.de/europe/slovakia-latest.osm.pbf" ;;
    austria)    URL="https://download.geofabrik.de/europe/austria-latest.osm.pbf" ;;
    europe)     URL="https://download.geofabrik.de/europe-latest.osm.pbf" ;;
    *)
        echo "Unknown region: $REGION"
        echo "Supported: poland, germany, france, spain, italy, czech, slovakia, austria, europe"
        echo "Or provide a direct Geofabrik URL as second argument:"
        echo "  ./generate_map.sh custom https://download.geofabrik.de/path/to/region-latest.osm.pbf"
        exit 1
        ;;
esac

# Allow custom URL as second argument
[ -n "$2" ] && URL="$2"

mkdir -p "$WORKDIR" "$OUTDIR"
PBF="$WORKDIR/${REGION}.osm.pbf"
OSM="$WORKDIR/${REGION}.osm"
BIN="$OUTDIR/${REGION}.bin"

echo "=== RetroNavi Map Generator ==="
echo "Region: $REGION"
echo "Source: $URL"
echo "Output: $BIN"
echo ""

# Download PBF if not cached
if [ ! -f "$PBF" ]; then
    echo "Downloading PBF..."
    wget -c -O "$PBF" "$URL"
else
    echo "Using cached PBF: $PBF"
fi

echo ""
echo "Converting PBF to OSM XML..."
echo "(this may take a while for large regions)"

# Check if osmconvert is available for PBF->XML conversion
if command -v osmconvert &>/dev/null; then
    # Convert PBF to OSM XML via pipe to maptool
    echo "Running maptool..."
    cd "$WORKDIR"
    osmconvert "$PBF" | "$MAPTOOL" -6 -j8 "$BIN"
else
    echo "osmconvert not found. Trying direct PBF input..."
    cd "$WORKDIR"
    "$MAPTOOL" -6 -j8 -i "$PBF" "$BIN"
fi

echo ""
echo "=== Done ==="
ls -lh "$BIN"
echo ""
echo "To install on device:"
echo "  adb push $BIN /sdcard/retronavi/maps/navitmap_001.bin"
echo ""
echo "Or copy to maps/ and update maps_cat.txt:"
echo "  echo 'navitmap_001.bin:${REGION}.bin' >> /sdcard/retronavi/maps_cat.txt"

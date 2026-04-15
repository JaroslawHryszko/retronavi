#!/bin/bash
# Generowanie mapy: dolnoslaskie + przylegle Czechy
# Uruchom na osobnej konsoli: ./generate_dolnoslaskie.sh
#
# Bounding box:
#   zachod 14.8  (Zgorzelec/Goerlitz)
#   wschod 17.5  (okolice Wroclawia)
#   polnoc 51.85 (polnocna granica dolnoslaskiego + troche opolskiego)
#   poludnie 49.7 (Hradec Kralove, Liberec, Nachod, Klodzko)

set -e

MAPTOOL="$(dirname "$(readlink -f "$0")")/build-maptool/maptool"
WORKDIR="/tmp/retronavi-dolnoslaskie"
OUTDIR="$(dirname "$(readlink -f "$0")")/maps"

BBOX="14.8,49.7,17.5,51.85"

echo "=========================================="
echo "  RetroNavi - mapa dolnoslaskie + Czechy"
echo "=========================================="
echo ""
echo "Bounding box: $BBOX"
echo "Katalog roboczy: $WORKDIR"
echo ""

mkdir -p "$WORKDIR" "$OUTDIR"
cd "$WORKDIR"

# 1. Pobierz PBF z Geofabrik
echo "[1/4] Pobieranie danych OSM..."
echo ""

if [ ! -f dolnoslaskie.osm.pbf ]; then
    echo "  -> dolnoslaskie (~120 MB)"
    wget -c -q --show-progress -O dolnoslaskie.osm.pbf \
        "https://download.geofabrik.de/europe/poland/dolnoslaskie-latest.osm.pbf"
else
    echo "  -> dolnoslaskie (cache: $(du -h dolnoslaskie.osm.pbf | cut -f1))"
fi

if [ ! -f czech.osm.pbf ]; then
    echo "  -> czechy (~750 MB)"
    wget -c -q --show-progress -O czech.osm.pbf \
        "https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf"
else
    echo "  -> czechy (cache: $(du -h czech.osm.pbf | cut -f1))"
fi

echo ""

# 2. Przytnij Czechy do bounding boxa i konwertuj na o5m
echo "[2/4] Przycinanie Czech do regionu przygranicznego..."
osmconvert czech.osm.pbf -b="$BBOX" --complete-ways --out-o5m -o=czech_clip.o5m
echo "  -> przyciety: $(du -h czech_clip.o5m | cut -f1)"
echo ""

# 3. Polacz dolnoslaskie (pbf) + przyciety kawalek Czech (o5m)
#    osmconvert obsluguje max 1 plik pbf, reszta musi byc o5m/osm
echo "[3/4] Laczenie danych..."
osmconvert dolnoslaskie.osm.pbf czech_clip.o5m -o=merged.osm
MERGED_SIZE=$(du -h merged.osm | cut -f1)
echo "  -> polaczony plik: $MERGED_SIZE"
echo ""

# 4. Konwertuj na format Navit binfile
echo "[4/4] Konwersja na mape RetroNavi (to moze troche potrwac)..."
echo ""
"$MAPTOOL" -6 -j8 "$OUTDIR/dolnoslaskie.bin" < merged.osm

echo ""
echo "=========================================="
echo "  GOTOWE!"
echo "=========================================="
echo ""
ls -lh "$OUTDIR/dolnoslaskie.bin"
echo ""
echo "Aby wgrac na telefon:"
echo "  adb push $OUTDIR/dolnoslaskie.bin /sdcard/retronavi/maps/navitmap_002.bin"
echo ""
echo "Albo zamien glowna mape:"
echo "  adb push $OUTDIR/dolnoslaskie.bin /sdcard/retronavi/maps/navitmap_001.bin"
echo ""
echo "Pliki tymczasowe w $WORKDIR mozesz usunac:"
echo "  rm -rf $WORKDIR"

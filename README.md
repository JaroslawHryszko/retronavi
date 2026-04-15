# RetroNavi

Offline navigation for older Android devices (2.3+). Derived from [Navit](https://github.com/navit-gps/navit) and [ZANavi](https://github.com/zoff99/zanavi).

RetroNavi is a revival of the abandoned ZANavi project, adapted to build and run on modern toolchains while targeting legacy Android hardware (512 MB RAM, small screens, slow ARM CPUs).

## Features

- Offline turn-by-turn navigation using OpenStreetMap data
- Works on Android 2.3+ (optimized for 4.x devices with small screens)
- Full Polish and English localization
- Bicycle-friendly: hideable UI bars for maximum map visibility on small screens
- All features unlocked (no donate-version restrictions)
- TTS voice guidance with automatic English fallback
- In-app map download from GitHub releases with background download and notification progress
- In-app update from GitHub releases with background download and notification progress
- TLS 1.2 support on Android 4.1-4.4 via BouncyCastle (for devices without Google Play Services)
- File-based logging to SD card with in-app log viewer

## Installing on device

```bash
adb install -r android-debug.apk
```

On old phones with small /data partition (e.g. 150 MB) this may fail with INSTALL_FAILED_INSUFFICIENT_STORAGE. Workaround:

```bash
adb push android-debug.apk /sdcard/retronavi/retronavi-update.apk
adb shell pm install -r /sdcard/retronavi/retronavi-update.apk
```

## Maps

RetroNavi uses Navit's binfile map format (.bin). Map files go in `/sdcard/retronavi/maps/` on the device.

### Downloading maps from the app

Menu -> "Uaktualnij mape" downloads the latest map from GitHub releases. The download runs in the background - you can keep navigating. Progress and cancel button are in the notification bar.

The default URL points to `https://github.com/JaroslawHryszko/retronavi/releases/latest/download/map.bin`. You can change it in Settings -> "URL pobierania mapy".

### Generating maps with GitHub Actions

The easiest way to generate fresh maps from OpenStreetMap data. No local tools needed.

1. Go to the repository on GitHub
2. Click **Actions** -> **Generate map**
3. Click **Run workflow**
4. Pick a region from the dropdown (poland, dolnoslaskie+czechy, germany, czech, etc.) or select "custom" and paste a Geofabrik PBF URL
5. Wait for the workflow to finish (a few minutes for small regions, up to an hour for large countries)
6. The map is uploaded as `map.bin` to the GitHub release

The `dolnoslaskie+czechy` option downloads both datasets, clips Czech Republic to the border region (bounding box 14.8-17.5 E, 49.7-51.85 N), merges them, and converts to binfile. This gives you Lower Silesia with the Czech side of the border included.

To add more composite regions (e.g. malopolskie+slowacja), add a new case in `.github/workflows/generate-map.yml`.

### Generating maps locally

Requirements: `libglib2.0-dev`, `zlib1g-dev`, `osmctools`

```bash
# Build maptool
cd build-maptool
make -j$(nproc)
cd ..

# Generate a map (downloads PBF from Geofabrik automatically)
./generate_map.sh poland

# Or a composite region
./generate_dolnoslaskie.sh
```

Result goes to `maps/`. Push to device:

```bash
adb push maps/poland.bin /sdcard/retronavi/maps/navitmap_001.bin
```

### Generating maps manually with maptool

```bash
cd build-maptool
wget https://download.geofabrik.de/europe/poland-latest.osm.pbf
osmconvert poland-latest.osm.pbf | ./maptool -6 -j8 poland.bin
```

## Updating the app

Menu -> "Pobierz aktualizacje" downloads the latest APK from GitHub releases and launches the system installer. Maps on SD card are not affected. The download runs in the background with progress in the notification bar.

## Building

### Requirements

- JDK 8
- Android SDK: platform 24, build-tools 24.0.2
- Android NDK r16b (16.1.4479499) - newer NDKs won't work (no arm-linux-androideabi-4.9 toolchain)
- CMake 3.6+
- Gradle 4.4 (included via wrapper)

On newer Linux systems you may need symlinks for NDK's clang:
```bash
sudo ln -sf libncurses.so.6 /usr/lib/x86_64-linux-gnu/libncurses.so.5
sudo ln -sf libtinfo.so.6 /usr/lib/x86_64-linux-gnu/libtinfo.so.5
```

### Native library (libnavit.so)

```bash
mkdir -p build-arm && cd build-arm

cmake \
  -DANDROID=TRUE \
  -DANDROID_ABI=armeabi \
  -DANDROID_NDK=$NDK_PATH \
  -DCMAKE_TOOLCHAIN_FILE=$NDK_PATH/build/cmake/android.toolchain.cmake \
  -DANDROID_NATIVE_API_LEVEL=9 \
  -DCMAKE_BUILD_TYPE=Release \
  -DDISABLE_CXX=TRUE \
  -DUSE_PLUGINS=FALSE \
  -DBUILD_MAPTOOL=FALSE \
  -DSAMPLE_MAP=FALSE \
  ..

make -j$(nproc)

cp navit/libnavit.so ../navit/android/nativelibs/armeabi/libnavit.so
$NDK_PATH/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi-strip \
  ../navit/android/nativelibs/armeabi/libnavit.so
```

Where `$NDK_PATH` is your NDK r16b installation (e.g. `~/Android/Sdk/ndk/16.1.4479499`).

### APK

```bash
cd navit
./gradlew assembleDebug
```

APK will be at `navit/android/build/outputs/apk/debug/android-debug.apk`.

## TLS 1.2 on old Android

Android 4.2 CyanogenMod (and similar old ROMs without Google Play Services) cannot connect to GitHub or other modern HTTPS servers - the platform OpenSSL claims TLS 1.2 support but actually sends TLS 1.0 ClientHello, which is rejected.

RetroNavi solves this with BouncyCastle 1.58 as a pure-Java TLS 1.2 implementation. The TLS handshake takes about 25 seconds on an 800 MHz ARM11 CPU (pure Java crypto), and download speed is around 1 MB/min. This is slow but functional. For practical use, loading maps via `adb push` is faster, but the in-app download works for users who need it.

The verbose logging option in Settings helps diagnose TLS connection issues.

## Architecture

Two layers:
- **Native C** (`libnavit.so`) - map rendering, routing engine, navigation logic, binfile format
- **Java Android** - UI, menus, GPS integration, TTS, system services

Communication via JNI with numbered callbacks (e.g. callback 47 = set map directory, callback 20 = load maps).

Main activity is `Navit.java` (~17k lines). New RetroNavi additions use the `RetroNavi` prefix:

| File | Purpose |
|------|---------|
| `RetroNaviTls12.java` | BouncyCastle TLS 1.2 for Android 4.1-4.4 |
| `RetroNaviMapDownloadService.java` | Background map download with notification progress |
| `RetroNaviMapUpdater.java` | Map download entry point (confirmation dialog) |
| `RetroNaviAppUpdateService.java` | Background APK download with notification progress |
| `RetroNaviUpdater.java` | App update entry point (confirmation dialog) |
| `RetroNaviLogger.java` | File logging to /sdcard/retronavi/retronavi.log |
| `RetroNaviLogViewerActivity.java` | In-app log viewer |

Package name is still `com.zoffcc.applications.zanavi` for compatibility - changing it would break JNI, device paths, and SharedPreferences.

## License

GPLv2, same as Navit and ZANavi. See individual source files for copyright details.

Original Navit authors: Navit Team (2005-2008)
Original ZANavi author: Zoff (2011-2018)

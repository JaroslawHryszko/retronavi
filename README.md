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
- In-app update from GitHub releases (menu: "Pobierz aktualizacje")
- File-based logging to SD card with in-app log viewer (menu: "Logi aplikacji")

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

## Maps

RetroNavi uses Navit's binfile map format (.bin). Place map files in `/sdcard/retronavi/maps/` on the device.

Maps can be downloaded from within the app or generated from OpenStreetMap data using maptool.

## Architecture

Two layers:
- **Native C** (`libnavit.so`) - map rendering, routing engine, navigation logic, binfile format
- **Java Android** - UI, menus, GPS integration, TTS, system services

Communication via JNI with numbered callbacks (e.g. callback 47 = set map directory, callback 20 = load maps).

Main activity is `Navit.java` (~17k lines). New RetroNavi additions use the `RetroNavi` prefix (e.g. `RetroNaviLogger.java`, `RetroNaviUpdater.java`).

Package name is still `com.zoffcc.applications.zanavi` for compatibility - changing it would break JNI, device paths, and SharedPreferences.

## License

GPLv2, same as Navit and ZANavi. See individual source files for copyright details.

Original Navit authors: Navit Team (2005-2008)
Original ZANavi author: Zoff (2011-2018)

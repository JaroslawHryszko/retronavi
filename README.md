# RetroNavi

Offline navigation for older Android devices (4.x+). Derived from [Navit](https://github.com/navit-gps/navit) and [ZANavi](https://github.com/zoff99/zanavi).

RetroNavi is a revival of the abandoned ZANavi project, adapted to build and run on modern toolchains while targeting legacy Android hardware.

## Features

- Offline turn-by-turn navigation using OpenStreetMap data
- Works on Android 2.3+ (optimized for 4.x devices with small screens)
- Full Polish and English localization
- Bicycle-friendly: hideable UI bars for maximum map visibility on small screens
- All features unlocked (no donate-version restrictions)
- TTS voice guidance with automatic English fallback

## Building

Requirements: JDK 8, Android SDK (platform 24, build-tools 24.0.2), Android NDK r16b, CMake 3.6, saxonb-xslt, xsltproc, autoconf, automake.

### Native library (libnavit.so)

```
export _NDK_=/path/to/ndk/16.1.4479499
mkdir build-arm && cd build-arm
../configure \
  CC="arm-linux-androideabi-gcc ..." \
  --host=arm-eabi-linux_android \
  --disable-nls --disable-maptool \
  --with-android-project="android-21"
make -j$(nproc)
cp navit/.libs/lib_data_data_com.zoffcc.applications.zanavi_lib_navit.so \
   ../navit/android/nativelibs/armeabi/libnavit.so
arm-linux-androideabi-strip ../navit/android/nativelibs/armeabi/libnavit.so
```

### APK

```
cd navit
./gradlew assembleDebug
```

The APK will be at `navit/android/build/outputs/apk/debug/android-debug.apk`.

## Maps

RetroNavi uses Navit's binfile map format. Maps can be generated from OpenStreetMap data using ZANavi's maptool (building it from this repo's sources is a work in progress).

Map files (.bin) go into the device's map directory, typically:
`/storage/sdcard0/Android/data/com.zoffcc.applications.zanavi/files/zanavi/maps/`

## License

GPLv2, same as Navit and ZANavi. See individual source files for copyright details.

Original Navit authors: Navit Team (2005-2008)
Original ZANavi author: Zoff (2011-2018)

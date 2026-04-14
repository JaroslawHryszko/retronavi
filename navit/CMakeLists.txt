# Build libnavit.so for Android (or navit binary for desktop).
#
# For Android command-line build, run from parent directory:
#   cmake -DANDROID=TRUE -DUSE_PLUGINS=FALSE -DDISABLE_CXX=TRUE
#         -DANDROID_ABI=armeabi -DANDROID_NDK=<ndk-path>
#         -DCMAKE_TOOLCHAIN_FILE=<ndk-path>/build/cmake/android.toolchain.cmake
#         -DANDROID_NATIVE_API_LEVEL=9 -DCMAKE_BUILD_TYPE=Release
#         -DBUILD_MAPTOOL=FALSE -DSAMPLE_MAP=FALSE ..
#
# Key flags:
#   USE_PLUGINS=FALSE  - required, plugin system is disabled in ZANavi/RetroNavi
#   DISABLE_CXX=TRUE   - no C++ needed, avoids NDK issues
#   ANDROID_ABI=armeabi - target old ARM devices (not armeabi-v7a)
#
# After build: copy navit/libnavit.so -> android/nativelibs/armeabi/libnavit.so
# Then strip with NDK strip and build APK with ./gradlew assembleDebug

include_directories( "${PROJECT_BINARY_DIR}")
include_directories( "${PROJECT_SOURCE_DIR}")
include_directories( "${CMAKE_CURRENT_SOURCE_DIR}")
include_directories( "${CMAKE_CURRENT_BINARY_DIR}")
include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/support")

# All source files compiled directly into libnavit.so (no separate module libs on Android)
set(NAVIT_SRC announcement.c atom.c attr.c cache.c callback.c command.c config_.c coord.c country.c data_window.c debug.c
   event.c file.c graphics.c gui.c item.c layout.c log.c main.c map.c
   linguistics.c mapset.c maptype.c menu.c messages.c bookmarks.c navit.c navigation.c osd.c param.c phrase.c plugin.c popup.c
   profile.c projection.c roadprofile.c route.c search.c speech.c start_real.c sunriset.c transform.c track.c
   maptool/miniz.c coffeecatch.c
   coffeejni.c snprintf.c
   vehicle/android/vehicle_android.c vehicle/demo/vehicle_demo.c
   gui/internal/gui_internal.c
   graphics/android/graphics_android.c map/binfile/binfile.c
   map/csv/csv.c map/csv/quadtree.c map/textfile/textfile.c
   osd/core/osd_core.c speech/android/speech_android.c android.c
   util.c vehicle.c vehicleprofile.c xmlconfig.c )

if(NOT USE_PLUGINS)
  if(ANDROID)
    # On Android, module plugin_init functions are disabled (#ifdef PLUGSSS),
    # so we use a minimal builtin.c that only provides the required symbol.
    list(APPEND NAVIT_SRC  ${CMAKE_CURRENT_SOURCE_DIR}/builtin_android.c)
  else()
    list(APPEND NAVIT_SRC  ${CMAKE_CURRENT_BINARY_DIR}/builtin.c)
  endif()
endif(NOT USE_PLUGINS)

if (${HAVE_GLIB})
   list(APPEND NAVIT_SRC event_glib.c)
endif()

if(ANDROID)
   set(NAVIT_LIBNAME navit)
else()
   set(NAVIT_LIBNAME navit_core)
endif()


foreach ( PLUGIN_PATH ${ALL_PLUGINS})
   if (${PLUGIN_PATH})
      include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/${PLUGIN_PATH}")
      
      string(REPLACE "/" "_" MODULE_INC ${PLUGIN_PATH})
      list(APPEND NAVIT_SUPPORT_LIBS ${MODULE_INC})
   endif()
endforeach()

foreach ( MODULE_PATH ${ALL_MODULES})
   if ( ${MODULE_PATH} )
      include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/${MODULE_PATH}")
      
      string(REPLACE "/" "_" MODULE_INC ${MODULE_PATH})
      list(APPEND MODULES_PROTOTYPE "\nvoid\tmodule_${MODULE_INC}_init(void)")
      list(APPEND MODULES_FUNC "\nmodule_${MODULE_INC}_init()")
      
      if (NOT USE_PLUGINS)
         list(APPEND MODULES_NAME ${MODULE_INC})
      else()
         string(REGEX REPLACE "(^[^/]*)/.*" "\\1" ${MODULE_INC}_TYPE ${MODULE_PATH})
      endif()
   endif()
endforeach()

CONFIGURE_FILE(${CMAKE_CURRENT_SOURCE_DIR}/builtin.c.in ${CMAKE_CURRENT_BINARY_DIR}/builtin.c)

include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/fib-1.1")
include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/../intl")

# Add module paths
foreach (CURRENT_MODULE ${ALL_PLUGINS} ${ALL_MODULE_PLUGINS} ${ALL_MODULES})
   if (${CURRENT_MODULE})
      add_subdirectory( "${CMAKE_CURRENT_SOURCE_DIR}/${CURRENT_MODULE}")
   endif()
endforeach()

add_subdirectory (fib-1.1)
add_subdirectory (../intl ./intl) # out-of-source dir needs bin dir

if(NOT ANDROID)
   set(NAVIT_START_SRC start.c)
   if(WIN32 OR WINCE AND NOT WIN_OMIT_RESOURCES)
      list(APPEND NAVIT_START_SRC ${CMAKE_CURRENT_SOURCE_DIR}/gui/win32/resources/resource.rc)
   else()
      if (APPLE)
          set(NAVIT_START_SRC start_apple.m)
      endif()
   endif()
   if (BUILD_BUNDLE)
      list(APPEND NAVIT_START_SRC resources/share resources/Icon.png resources/Default.png)
      add_custom_command(OUTPUT resources/Icon.png COMMAND convert -scale 79x79 -crop 60x60+8+19 ${CMAKE_CURRENT_SOURCE_DIR}/xpm/desktop_icons/128x128/navit.png resources/Icon.png)
      add_custom_command(OUTPUT resources/Default.png COMMAND convert -scale 79x79 -crop 60x60+8+19 ${CMAKE_CURRENT_SOURCE_DIR}/xpm/desktop_icons/128x128/navit.png resources/Default.png)
   endif()
   add_executable(navit ${NAVIT_START_SRC})
   target_link_libraries (navit ${NAVIT_LIBNAME})
   set_target_properties(navit PROPERTIES COMPILE_DEFINITIONS "MODULE=${MODULE_NAME}")
   if (BUILD_BUNDLE)
      add_custom_command(OUTPUT resources/share COMMAND mkdir -p resources/share)
      set_source_files_properties(resources/share resources/Icon.png resources/Default.png PROPERTIES MACOSX_PACKAGE_LOCATION Resources)
      set_target_properties(navit PROPERTIES MACOSX_BUNDLE TRUE)
      set_target_properties(navit PROPERTIES MACOSX_BUNDLE_INFO_PLIST ${CMAKE_CURRENT_SOURCE_DIR}/Info.plist)
      add_dependencies(navit navit_config_xml_resource)
      add_dependencies(navit images_resource)
      add_dependencies(navit locale_resource)
   endif()
endif()

if (SHARED_LIBNAVIT)
  add_library (${NAVIT_LIBNAME} SHARED ${NAVIT_SRC} )
else(SHARED_LIBNAVIT)
  add_library (${NAVIT_LIBNAME} STATIC ${NAVIT_SRC} )
endif(SHARED_LIBNAVIT)
if(NOT MSVC)
    SET(NAVIT_LIBS ${NAVIT_LIBS} m)
endif(NOT MSVC)
# Module source files are compiled directly into the shared lib (NAVIT_SRC),
# so we set per-file MODULE defines to generate correct module_xxx_init symbols.
# The static module libraries are NOT linked to avoid duplicate symbols.
if(ANDROID AND NOT USE_PLUGINS)
  set_source_files_properties(graphics/android/graphics_android.c PROPERTIES COMPILE_DEFINITIONS "MODULE=graphics_android")
  set_source_files_properties(osd/core/osd_core.c PROPERTIES COMPILE_DEFINITIONS "MODULE=osd_core")
  set_source_files_properties(vehicle/demo/vehicle_demo.c PROPERTIES COMPILE_DEFINITIONS "MODULE=vehicle_demo")
  set_source_files_properties(gui/internal/gui_internal.c PROPERTIES COMPILE_DEFINITIONS "MODULE=gui_internal")
  set_source_files_properties(map/binfile/binfile.c PROPERTIES COMPILE_DEFINITIONS "MODULE=map_binfile")
  set_source_files_properties(map/textfile/textfile.c PROPERTIES COMPILE_DEFINITIONS "MODULE=map_textfile")
  set_source_files_properties(map/csv/csv.c PROPERTIES COMPILE_DEFINITIONS "MODULE=map_csv")
  set_source_files_properties(map/csv/quadtree.c PROPERTIES COMPILE_DEFINITIONS "MODULE=map_csv")
  set_source_files_properties(speech/android/speech_android.c PROPERTIES COMPILE_DEFINITIONS "MODULE=speech_android")
  set_source_files_properties(vehicle/android/vehicle_android.c PROPERTIES COMPILE_DEFINITIONS "MODULE=vehicle_android")
  target_link_libraries(${NAVIT_LIBNAME} ${NAVIT_SUPPORT_LIBS} fib intl ${NAVIT_LIBS})
else()
  target_link_libraries(${NAVIT_LIBNAME}  ${MODULES_NAME} ${NAVIT_SUPPORT_LIBS} fib intl ${NAVIT_LIBS})
endif()
set_target_properties(${NAVIT_LIBNAME} PROPERTIES COMPILE_DEFINITIONS "MODULE=navit;LIBDIR=\"${CMAKE_INSTALL_PREFIX}/${LIB_DIR}\";PREFIX=\"${CMAKE_INSTALL_PREFIX}\"")
if (DEFINED NAVIT_COMPILE_FLAGS)
   set_target_properties(${NAVIT_LIBNAME} PROPERTIES COMPILE_FLAGS "${NAVIT_COMPILE_FLAGS}")
endif()

ADD_CUSTOM_TARGET(
   git_version
   ${CMAKE_COMMAND} -D SRC=${CMAKE_CURRENT_SOURCE_DIR}/version.h.in
                    -D DST=${CMAKE_CURRENT_BINARY_DIR}/version.h
		    -D NAME="GIT_VERSION"
                    -P ${PROJECT_SOURCE_DIR}/cmake/version.cmake
)

# additional parameter are passed to the stylesheet processor as parameter
macro(process_xslt SRC_XML DEST_XML)
   set(XSLT_COMMANDS COMMAND ${CMAKE_COMMAND} -E copy ${SRC_XML} ${DEST_XML}
                     COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_SOURCE_DIR}/navit/navit.dtd ${CMAKE_CURRENT_BINARY_DIR}/navit.dtd)
   if(XSL_PROCESSING AND XSLTS)
       string(REPLACE "," ";" XSLTS "${XSLTS}")
       foreach(tmp ${XSLTS})
          set(XSLT_FILE "${PROJECT_SOURCE_DIR}/navit/xslt/${tmp}.xslt")
          list(APPEND XSLT_FILES "${XSLT_FILE}")
          list(APPEND XSLT_COMMANDS COMMAND ${CMAKE_COMMAND} -E echo  Applying ${tmp}.xslt)
          compose_xslt_transform_command(CMD "${XSLT_FILE}" "${DEST_XML}" "${DEST_XML}.tmp" "${ARGN}")
          list(APPEND XSLT_COMMANDS ${CMD})
          list(APPEND XSLT_COMMANDS COMMAND ${CMAKE_COMMAND} -E rename ${DEST_XML}.tmp ${DEST_XML})
       endforeach()
   endif()
   ADD_CUSTOM_COMMAND(
      OUTPUT ${DEST_XML}
      DEPENDS ${SRC_XML} ${XSLT_FILES}
      ${XSLT_COMMANDS}
   )
endmacro()


ADD_DEPENDENCIES(${NAVIT_LIBNAME} git_version)

install(TARGETS navit
        DESTINATION ${BIN_DIR}
        PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE)

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/navit.xml
        DESTINATION ${SHARE_DIR}
        PERMISSIONS OWNER_READ OWNER_WRITE GROUP_READ WORLD_READ)

get_directory_property(INCLUDE_DIRECTORIES INCLUDE_DIRECTORIES)
WRITE_FILE("cmake_plugin_settings.txt"
	"set(APPLE ${APPLE} CACHE BOOL init)\n" 
	"set(ANDROID ${ANDROID} CACHE BOOL init)\n"
	"set(USE_PLUGINS ${USE_PLUGINS} CACHE BOOL init)\n"
	"set(MODULE_BUILD_TYPE \"${MODULE_BUILD_TYPE}\" CACHE STRING init)\n"
	"set(NAVIT_COMPILE_FLAGS \"${NAVIT_COMPILE_FLAGS}\" CACHE STRING init)\n"
	"set(navit_SOURCE_DIR \"${navit_SOURCE_DIR}\" CACHE STRING init)\n"
	"set(NAVIT_LIBNAME \"${NAVIT_LIBNAME}\" CACHE STRING init)\n"
	"set(ANDROID_API_VERSION \"${ANDROID_API_VERSION}\" CACHE STRING init)\n"
	"set(ANDROID_NDK_API_VERSION \"${ANDROID_NDK_API_VERSION}\" CACHE STRING init)\n"
	"set(CMAKE_TOOLCHAIN_FILE \"${CMAKE_TOOLCHAIN_FILE}\" CACHE STRING init)\n"
	"set(INCLUDE_DIRECTORIES \"${INCLUDE_DIRECTORIES}\" CACHE STRING init)\n"
	"set(LIB_DIR \"${LIB_DIR}\" CACHE STRING init)\n"
	"set(CMAKE_INSTALL_PREFIX \"${CMAKE_INSTALL_PREFIX}\" CACHE STRING init)\n"
	)


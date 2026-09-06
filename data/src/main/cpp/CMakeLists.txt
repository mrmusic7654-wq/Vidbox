cmake_minimum_required(VERSION 3.22)
project(VidboxCodecDependencies LANGUAGES C CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "Use the Android NDK toolchain; these are device libraries, not host binaries")
endif()
if(NOT WEBP_SOURCE_DIR)
    message(FATAL_ERROR "A checksum-verified libwebp source directory is required")
endif()
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared codec libraries" FORCE)
foreach(option_name WEBP_BUILD_ANIM_UTILS WEBP_BUILD_CWEBP WEBP_BUILD_DWEBP WEBP_BUILD_GIF2WEBP
        WEBP_BUILD_IMG2WEBP WEBP_BUILD_VWEBP WEBP_BUILD_WEBPINFO WEBP_BUILD_WEBPMUX
        WEBP_BUILD_EXTRAS WEBP_BUILD_WEBP_JS WEBP_BUILD_FUZZTEST)
    set(${option_name} OFF CACHE BOOL "Only build runtime libraries" FORCE)
endforeach()
set(WEBP_BUILD_LIBWEBPMUX ON CACHE BOOL "FFmpeg's mux dependency" FORCE)
set(WEBP_USE_THREAD ON CACHE BOOL "Use Android threads" FORCE)
add_subdirectory("${WEBP_SOURCE_DIR}" webp)
foreach(codec_target webp webpdecoder webpdemux libwebpmux sharpyuv)
    set_target_properties(${codec_target} PROPERTIES
        VERSION "" SOVERSION ""
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
    target_link_options(${codec_target} PRIVATE "-Wl,-z,max-page-size=16384")
endforeach()

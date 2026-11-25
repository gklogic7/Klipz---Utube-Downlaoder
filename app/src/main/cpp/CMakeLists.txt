cmake_minimum_required(VERSION 3.22.1)
project("klipz_ffmpeg")

set(FFMPEG_PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}")

add_library(
    klipz_ffmpeg
    SHARED
    klipz_ffmpeg_jni.cpp
)

find_library(log-lib log)

if(EXISTS "${FFMPEG_PREBUILT_DIR}/lib/libavformat.so")
    message(STATUS "Klipz: Linking prebuilt LGPL FFmpeg libraries for ${ANDROID_ABI}")
    target_compile_definitions(klipz_ffmpeg PRIVATE KLIPZ_HAS_FFMPEG=1)
    target_include_directories(klipz_ffmpeg PRIVATE "${FFMPEG_PREBUILT_DIR}/include")

    foreach(lib avcodec avformat avutil swresample swscale)
        add_library(${lib} SHARED IMPORTED)
        set_target_properties(${lib} PROPERTIES
            IMPORTED_LOCATION "${FFMPEG_PREBUILT_DIR}/lib/lib${lib}.so")
    endforeach()

    target_link_libraries(
        klipz_ffmpeg
        avformat
        avcodec
        avutil
        swresample
        swscale
        ${log-lib}
    )
else()
    message(WARNING "Klipz: Prebuilt FFmpeg libs not found in ${FFMPEG_PREBUILT_DIR}; compiling JNI bridge in capability-probe mode.")
    target_link_libraries(klipz_ffmpeg ${log-lib})
endif()

# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)
project(ShadowAutoPaddleOcr)

# Creates and names a library, sets it as either STATIC or SHARED, and provides
# the relative paths to its source code. You can define multiple libraries, and
# CMake builds them for you. Gradle automatically packages shared libraries with
# your APK.

set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../PaddleLite")
if (NOT EXISTS ${PaddleLite_DIR}/cxx/include)
    message(FATAL_ERROR "PaddleLite is missing. Run ./gradlew :paddler-ocr:preparePaddleLiteOcr before enabling Paddle OCR.")
endif ()
include_directories(${PaddleLite_DIR}/cxx/include)

set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../OpenCV/sdk/native/jni")
if (NOT EXISTS ${OpenCV_DIR})
    message(FATAL_ERROR "OpenCV Android SDK is missing. Run ./gradlew :paddler-ocr:preparePaddleLiteOcr before enabling Paddle OCR.")
endif ()
message(STATUS "opencv dir: ${OpenCV_DIR}")
find_package(OpenCV REQUIRED)
message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
include_directories(${OpenCV_INCLUDE_DIRS})
file(GLOB SOURCES CONFIGURE_DEPENDS "*.cpp")
set(CMAKE_CXX_FLAGS
        "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os"
        )
set(CMAKE_CXX_FLAGS
        "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections"
        )
set(CMAKE_SHARED_LINKER_FLAGS
        "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc")

add_library(
        # Sets the name of the library.
        Native
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        ${SOURCES})

find_library(
        # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that you want CMake to locate.
        log)

add_library(
        # Sets the name of the library.
        paddle_api_light_bundled
        # Sets the library as a static library.
        STATIC
        # Provides a relative path to your library.
        IMPORTED)

set_target_properties(
        # Specifies the target library.
        paddle_api_light_bundled
        # Specifies the parameter you want to define.
        PROPERTIES
        IMPORTED_LOCATION
        ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_api_light_bundled.a
        # Provides the path to the library you want to import.
)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(OMP_ABI_DIR "aarch64")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(OMP_ABI_DIR "arm")
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set(OMP_ABI_DIR "x86_64")
else ()
    set(OMP_ABI_DIR "i386")
endif ()
get_filename_component(LLVM_BIN_DIR "${CMAKE_CXX_COMPILER}" DIRECTORY)
get_filename_component(LLVM_PREBUILT_DIR "${LLVM_BIN_DIR}" DIRECTORY)
file(GLOB OMP_LIB_CANDIDATES "${LLVM_PREBUILT_DIR}/lib64/clang/*/lib/linux/${OMP_ABI_DIR}/libomp.so")
list(GET OMP_LIB_CANDIDATES 0 OMP_LIB)
if (NOT EXISTS ${OMP_LIB})
    message(FATAL_ERROR "libomp.so is missing for ${ANDROID_ABI}")
endif ()
add_library(openmp_runtime SHARED IMPORTED)
set_target_properties(openmp_runtime PROPERTIES IMPORTED_LOCATION ${OMP_LIB})


# Specifies libraries CMake should link to your target library. You can link
# multiple libraries, such as libraries you define in this build script,
# prebuilt third-party libraries, or system libraries.

target_link_libraries(
        # Specifies the target library.
        Native
        paddle_api_light_bundled
        openmp_runtime
        ${OpenCV_LIBS}
        GLESv2
        EGL
        jnigraphics
        ${log-lib}
)

add_custom_command(
        TARGET Native
        POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libc++_shared.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libc++_shared.so)

add_custom_command(
        TARGET Native
        POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${OMP_LIB}
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libomp.so)

foreach (PADDLE_EXTRA_LIB libhiai.so libhiai_ir.so libhiai_ir_build.so)
    if (EXISTS ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/${PADDLE_EXTRA_LIB})
        add_custom_command(
                TARGET Native
                POST_BUILD
                COMMAND
                ${CMAKE_COMMAND} -E copy
                ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/${PADDLE_EXTRA_LIB}
                ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${PADDLE_EXTRA_LIB})
    endif ()
endforeach ()

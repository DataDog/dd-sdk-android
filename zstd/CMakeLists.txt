cmake_minimum_required(VERSION 3.22.1)

project("zstd-jni-1" C ASM)

# Define sources
file(GLOB COMMON_SOURCES "src/main/cpp/common/*.c")
file(GLOB COMPRESS_SOURCES "src/main/cpp/compress/*.c")
# Only include necessary JNI files
set(JNI_SOURCES
    "src/main/cpp/jni_zstd.c"
    "src/main/cpp/jni_outputstream_zstd.c"
)

# Define the library
add_library(
    zstd-jni-1
    SHARED
    ${COMMON_SOURCES}
    ${COMPRESS_SOURCES}
    ${JNI_SOURCES}
)

# Include directories
target_include_directories(zstd-jni-1 PRIVATE
    src/main/cpp
    src/main/cpp/common
    src/main/cpp/compress
)

# Standard libraries
find_library(log-lib log)

target_link_libraries(
    zstd-jni-1
    ${log-lib}
)

# Zstd configuration defines
target_compile_definitions(zstd-jni-1 PRIVATE
    ZSTD_LEGACY_SUPPORT=0
    ZSTD_MULTITHREAD=0
    ZSTD_STRIP_ERROR_STRINGS=1
    ZSTD_NO_UNUSED_FUNCTIONS=1
    ZSTD_LIB_EXCLUDE_COMPRESSORS_DFAST_AND_UP=1
    DYNAMIC_BMI2=0
    ZSTD_NO_INLINE=1
)

# Use version script to hide internal symbols and reduce binary size
# Add LTO linker flag for better dead code elimination
set_target_properties(zstd-jni-1 PROPERTIES
    LINK_FLAGS "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/libzstd-jni.map -flto"
)

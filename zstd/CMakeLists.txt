cmake_minimum_required(VERSION 3.22.1)

project("zstd-jni-1" C ASM)

# Define sources
file(GLOB COMMON_SOURCES "src/main/cpp/common/*.c")
file(GLOB COMPRESS_SOURCES "src/main/cpp/compress/*.c")
file(GLOB DECOMPRESS_SOURCES "src/main/cpp/decompress/*.c" "src/main/cpp/decompress/*.S")
file(GLOB DICT_BUILDER_SOURCES "src/main/cpp/dictBuilder/*.c")
file(GLOB LEGACY_SOURCES "src/main/cpp/legacy/*.c")
file(GLOB JNI_SOURCES "src/main/cpp/*.c")

# Define the library
add_library(
    zstd-jni-1
    SHARED
    ${COMMON_SOURCES}
    ${COMPRESS_SOURCES}
    ${DECOMPRESS_SOURCES}
    ${DICT_BUILDER_SOURCES}
    ${LEGACY_SOURCES}
    ${JNI_SOURCES}
)

# Include directories
target_include_directories(zstd-jni-1 PRIVATE
    src/main/cpp
    src/main/cpp/common
    src/main/cpp/compress
    src/main/cpp/decompress
    src/main/cpp/dictBuilder
    src/main/cpp/legacy
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
    ZSTD_MULTITHREAD=1
)

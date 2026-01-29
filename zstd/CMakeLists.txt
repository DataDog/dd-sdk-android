cmake_minimum_required(VERSION 3.22.1)

project("zstd-native-lib")

add_library(
    zstd-native-lib
    SHARED
    src/main/cpp/zstd-native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    zstd-native-lib
    ${log-lib}
)

cmake_minimum_required(VERSION 3.22.1)
project("dd-sdk-android-ndk")
# use the C++ 17 compiler
set(CMAKE_CXX_STANDARD 17)
add_subdirectory(src/main/cpp)

if(${CMAKE_BUILD_TYPE} STREQUAL Debug)
    enable_testing()
    add_subdirectory(src/test/cpp)
endif()

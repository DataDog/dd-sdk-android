cmake_minimum_required(VERSION 3.10.2)
add_subdirectory(src/main/cpp)

if(${CMAKE_BUILD_TYPE} STREQUAL Debug)
    enable_testing()
    add_subdirectory(src/test/cpp)
endif()

include_directories(
        ./
        ${CMAKE_SOURCE_DIR}/src/main/include
)
add_library( # Sets the name of the library.
        datadog-tracer-native-lib
        # Sets the library as a shared library.
        SHARED
        datadog-tracer-native-lib.cpp
)
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
add_library(
        tracer_lib
        SHARED
        IMPORTED
)

target_link_libraries( # Specifies the target library.
        datadog-tracer-native-lib
        ${log-lib}
        tracer_lib
)


set_target_properties(
        datadog-tracer-native-lib
        PROPERTIES
        COMPILE_OPTIONS
        -Werror -Wall -pedantic)
set_target_properties(
        tracer_lib PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libtracer_lib.so
)
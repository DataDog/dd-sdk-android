include_directories(
        ./
        ./../include
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

set_target_properties(
        # print the path to the library
        tracer_lib PROPERTIES
        IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libtracer_lib.so
)

target_link_libraries( # Specifies the target library.
        datadog-tracer-native-lib
        tracer_lib
        ${log-lib}
)

set_target_properties(
        datadog-tracer-native-lib
        PROPERTIES
        COMPILE_OPTIONS
        -Werror -Wall -pedantic)


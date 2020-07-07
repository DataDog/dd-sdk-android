cmake_minimum_required(VERSION 3.10.2)

add_library( # Sets the name of the library.
        datadog-native-sample-lib
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        datadog-native-sample-lib.cpp
        )
find_library(
        # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
target_link_libraries( # Specifies the target library.
        datadog-native-sample-lib
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

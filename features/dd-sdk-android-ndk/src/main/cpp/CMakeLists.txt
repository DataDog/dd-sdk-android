include_directories(
        ./
        utils
        model
)
add_library( # Sets the name of the library.
        datadog-ndk
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        datadog-ndk.cpp
        datadog-ndk.h
        utils/signal-monitor.c
        utils/signal-monitor.h
        utils/file-utils.cpp
        utils/file-utils.h
        utils/string-utils.cpp
        utils/string-utils.h
        utils/datetime-utils.cpp
        utils/datetime-utils.h
        utils/backtrace-handler.cpp
        utils/backtrace-handler.h
        )
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
target_link_libraries( # Specifies the target library.
        datadog-ndk
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
set_target_properties(datadog-ndk
        PROPERTIES
        COMPILE_OPTIONS
        -Werror -Wall -pedantic)

# Size optimizations for non-Debug builds
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(datadog-ndk PRIVATE
            -Oz
            -fno-exceptions
            -fno-rtti
            -ffunction-sections
            -fdata-sections
            -flto=thin
            )
    target_link_options(datadog-ndk PRIVATE
            -Oz
            -flto=thin
            -Wl,--gc-sections
            -Wl,--exclude-libs,ALL
            -Wl,--icf=safe
            )
endif()

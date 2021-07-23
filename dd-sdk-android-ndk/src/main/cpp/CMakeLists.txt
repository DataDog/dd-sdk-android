include_directories(
        ./
        utils
        model
)
add_library( # Sets the name of the library.
        datadog-native-lib
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        datadog-native-lib.cpp
        datadog-native-lib.h
        model/crash-log.cpp
        model/crash-log.h
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
        utils/format-utils.cpp
        )
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
target_link_libraries( # Specifies the target library.
        datadog-native-lib
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
set_target_properties(datadog-native-lib
        PROPERTIES
        COMPILE_OPTIONS
        -Werror -Wall -pedantic)

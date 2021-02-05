include_directories(
        ../../main/cpp
        ../../main/utils
        ../cpp/utils
)
add_library(
        datadog-native-lib-test
        SHARED
        integration-tests.cpp
        test-crash-log.cpp
        test-datetime-utils.cpp
        test-format-utils.cpp
        test-generate-backtrace.cpp
        test-signal-monitor.cpp
        test-utils.cpp
        test-utils.h
)
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
target_link_libraries(datadog-native-lib-test datadog-native-lib ${log-lib})
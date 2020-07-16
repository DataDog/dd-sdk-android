include_directories(
        ../../main/cpp
        ../../main/utils
        ../cpp/utils
)
add_library(
        datadog-native-lib-test
        SHARED
        integration-tests.cpp
        test_datetime_utils.cpp
        test_generate_backtrace.cpp
        test_signal_monitor.cpp
        test_utils.cpp
        test_utils.h
)
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)
target_link_libraries(datadog-native-lib-test datadog-native-lib ${log-lib})
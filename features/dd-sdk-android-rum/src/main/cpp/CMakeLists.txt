find_package(Threads REQUIRED)

include_directories(
        ./ # current directory
)

# 1. Location of the Simpleperf sources that ship with the NDK
set(SIMPLEPERF_DIR
        "${ANDROID_NDK}/simpleperf/app_api/cpp")
set(SIMPLEPERF_CPP
        "${SIMPLEPERF_DIR}/simpleperf.cpp")

# 2. Generate (at configure-time) a header that kills the macros
set(UNDEF_HEADER "${CMAKE_CURRENT_BINARY_DIR}/undef_stdio.h")
file(WRITE "${UNDEF_HEADER}"
        "#include <stdio.h>\n"
        "#undef stdout\n"
        "#undef stderr\n")

# 3. Build Simpleperf as a STATIC library and inject the header
add_library(simpleperf_app_api STATIC ${SIMPLEPERF_CPP})
set_source_files_properties(${SIMPLEPERF_CPP} PROPERTIES
        COMPILE_FLAGS "-include \"${UNDEF_HEADER}\"")

target_include_directories(simpleperf_app_api PUBLIC
        ${SIMPLEPERF_DIR})

add_library(datadog-profiling SHARED datadog_profiling.cpp)

# log the path to the perfetto library
target_link_libraries(
        datadog-profiling
        simpleperf_app_api
        log
)
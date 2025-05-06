find_package(Threads REQUIRED)

include_directories(
        ./ # current directory
)

# set the path to the Android NDK
set(PERFETTO_PATH "${PROJECT_SOURCE_DIR}/../../../perfetto/sdk")
# load the perfetto dynamic library
#add_library(perfetto STATIC "${PERFETTO_PATH}/perfetto.cc")
include_directories("${PERFETTO_PATH}")

add_library(datadog-profiling SHARED datadog_profiling.cpp)

add_library(perfetto STATIC ${PERFETTO_PATH}/perfetto.cc)

# log the path to the perfetto library
target_link_libraries(
        datadog-profiling
        perfetto
        log
)
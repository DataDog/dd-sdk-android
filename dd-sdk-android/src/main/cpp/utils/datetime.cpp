#include <ctime>
#include <ctime>
#include <chrono>
#include <cstdio>
#include <cerrno>
#include "datetime.h"

uint64_t timeSinceEpochMillisec() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}


void dateTimeWithFormat(char *buffer, size_t bufferSize, const char *format) {
    using namespace std::chrono;
    system_clock::time_point p = system_clock::now();
    std::time_t t = system_clock::to_time_t(p);
    struct tm *timeinfo;
    timeinfo = gmtime(&t);
    strftime(buffer, bufferSize, format, timeinfo);
}
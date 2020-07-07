#include "datetime.h"

#include <cerrno>
#include <chrono>
#include <ctime>
#include <ctime>
#include <cstdio>

uint64_t time_since_epoch() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

void format_date(char *buffer, size_t buffer_size, const char *format) {
    using namespace std::chrono;
    const std::time_t t = time(nullptr);
    const struct tm *timeinfo = gmtime(&t);
    strftime(buffer, buffer_size, format, timeinfo);
}
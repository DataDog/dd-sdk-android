/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#ifndef DD_SDK_ANDROID_CRASH_LOG_H
#define DD_SDK_ANDROID_CRASH_LOG_H

#include <string>

class CrashLog {
    int signal;
    uint64_t timestamp;
    std::string signal_name;
    std::string error_message;
    std::string error_stacktrace;
public:
    CrashLog(
            int signal,
            uint64_t timestamp,
            const std::string error_message,
            const std::string error_stacktrace,
            const std::string signal_name);

    std::string serialise();
};

#endif //DD_SDK_ANDROID_CRASH_LOG_H

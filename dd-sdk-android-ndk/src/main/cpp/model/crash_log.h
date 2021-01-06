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
    std::string error_message;
    std::string error_stacktrace;

public:
    CrashLog(const int signal, const std::string error_message, const std::string error_stacktrace);
    std::string serialise();
};

#endif //DD_SDK_ANDROID_CRASH_LOG_H

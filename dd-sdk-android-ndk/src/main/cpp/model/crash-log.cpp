/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#include "crash-log.h"
#include <string>
#include "format-utils.cpp"

CrashLog::CrashLog(
        int signal,
        uint64_t timestamp,
        const std::string signal_name,
        const std::string error_message,
        const std::string error_stacktrace) {
    this->signal = signal;
    this->timestamp = timestamp;
    this->signal_name = signal_name;
    this->error_message = error_message;
    this->error_stacktrace = error_stacktrace;
}

std::string CrashLog::serialise() {
    using namespace std;
    static const string json_formatter = R"({"signal":%s,"timestamp":%s,"signal_name":"%s","message":"%s","stacktrace":"%s"})";
    return strformat::format(json_formatter,
                             to_string(this->signal).c_str(),
                             to_string(this->timestamp).c_str(),
                             this->signal_name.c_str(),
                             this->error_message.c_str(),
                             this->error_stacktrace.c_str());
}
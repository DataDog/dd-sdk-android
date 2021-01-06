/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#include "crash_log.h"
#include <string>
#include "../utils/format-utils.cpp"

CrashLog::CrashLog(const int signal, const std::string error_message,
                   const std::string error_stacktrace) {
    this->signal = signal;
    this->error_message = error_message;
    this->error_stacktrace = error_stacktrace;
}

std::string CrashLog::serialise() {
    using namespace std;
    static const string json_formatter = R"({"signal":%s,"message":"%s","stacktrace":"%s"})";
    return strformat::format(json_formatter,
                             to_string(this->signal).c_str(),
                             this->error_message.c_str(),
                             this->error_stacktrace.c_str());
}
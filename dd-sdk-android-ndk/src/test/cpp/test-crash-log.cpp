/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <string>
#include "greatest/greatest.h"
#include "model/crash-log.h"

using namespace std;

TEST test_will_serialise_the_crash_log(void) {
    const string fake_error_message = "an error message";
    const string fake_error_stacktrace = "an error stacktrace";
    const int fake_error_signal = 2;
    const uint64_t fake_timestamp = 100;
    const string fake_signal_name = "a signal name";
    unique_ptr<CrashLog> crashLogPointer = make_unique<CrashLog>(fake_error_signal,
                                                                 fake_timestamp,
                                                                 fake_signal_name,
                                                                 fake_error_message,
                                                                 fake_error_stacktrace);
    const string expected_serialised_log = string("{\"signal\":")
            .append(to_string(fake_error_signal))
            .append(",\"timestamp\":")
            .append(to_string(fake_timestamp))
            .append(",\"signal_name\":")
            .append("\"")
            .append(fake_signal_name)
            .append("\"")
            .append(",\"message\":")
            .append("\"")
            .append(fake_error_message)
            .append("\"")
            .append(",\"stacktrace\":")
            .append("\"")
            .append(fake_error_stacktrace)
            .append("\"")
            .append("}");
    const string serialised_log = crashLogPointer->serialise();
            ASSERT_STR_EQ(serialised_log.c_str(), expected_serialised_log.c_str());
            PASS();
}

SUITE (crash_log) {
            RUN_TEST(test_will_serialise_the_crash_log);
}


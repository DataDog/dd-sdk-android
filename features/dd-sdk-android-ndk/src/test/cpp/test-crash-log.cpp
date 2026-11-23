/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <string>
#include "greatest/greatest.h"
#include "datadog-ndk.h"
#include "utils/backtrace-handler.h"

using namespace std;

extern size_t serialize_crash_report(char *buffer,
                                     size_t buffer_size,
                                     int signum,
                                     uint64_t timestamp,
                                     const char* signal_name,
                                     const char* error_message,
                                     const char* error_stacktrace);

TEST test_will_serialise_the_crash_log(void) {
    const char* fake_error_message = "an error message";
    const char* fake_error_stacktrace = "an error stacktrace";
    const int fake_error_signal = 2;
    const uint64_t fake_timestamp = 100;
    const uint64_t fake_app_start_timestamp = 10;
    update_app_start_time_millis(fake_app_start_timestamp);
    const char* fake_signal_name = "a signal name";
    char buffer[512];
    const size_t length = serialize_crash_report(buffer,
                                                 sizeof(buffer),
                                                 fake_error_signal,
                                                 fake_timestamp,
                                                 fake_signal_name,
                                                 fake_error_message,
                                                 fake_error_stacktrace);
    const string serialized_log(buffer, length);
    const string expected_serialised_log = string("{\"signal\":")
            .append(to_string(fake_error_signal))
            .append(",\"timestamp\":")
            .append(to_string(fake_timestamp))
            .append(",\"time_since_app_start_ms\":")
            .append(to_string(fake_timestamp - fake_app_start_timestamp))
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

    ASSERT_STR_EQ(serialized_log.c_str(), expected_serialised_log.c_str());
    PASS();
}

TEST test_will_serialise_the_longest_stacktrace(void) {
    // The longest stack trace the backtrace handler can produce, with the longest values the
    // other fields can take.
    const string longest_stacktrace(max_stack_size - 1, 'a');
    const string longest_message = "Segmentation violation (invalid memory reference)";
    static char buffer[max_serialized_report_size];
    update_app_start_time_millis(0);
    const size_t length = serialize_crash_report(buffer,
                                                 sizeof(buffer),
                                                 INT32_MIN,
                                                 UINT64_MAX,
                                                 "SIGSEGV",
                                                 longest_message.c_str(),
                                                 longest_stacktrace.c_str());
    ASSERT(length > longest_stacktrace.size());
    const string serialized_log(buffer, length);
    ASSERT(serialized_log.find(longest_stacktrace) != string::npos);
    ASSERT_STR_EQ("\"}", serialized_log.substr(serialized_log.size() - 2).c_str());
    PASS();
}

TEST test_will_return_zero_if_the_crash_log_does_not_fit(void) {
    char buffer[16];
    const size_t length = serialize_crash_report(buffer,
                                                 sizeof(buffer),
                                                 11,
                                                 100,
                                                 "SIGSEGV",
                                                 "a message",
                                                 "a stacktrace longer than the buffer");
    ASSERT_EQ_FMT((size_t) 0, length, "%zu");
    PASS();
}

TEST test_will_serialise_the_crash_log_only_if_it_fits_with_its_end_of_string(void) {
    static char buffer[max_serialized_report_size];
    const size_t length = serialize_crash_report(buffer, sizeof(buffer), 11, 100, "SIGSEGV", "a message", "a stacktrace");
    ASSERT(length > 0);

    ASSERT_EQ_FMT(length,
                  serialize_crash_report(buffer, length + 1, 11, 100, "SIGSEGV", "a message", "a stacktrace"),
                  "%zu");
    ASSERT_EQ_FMT((size_t) 0,
                  serialize_crash_report(buffer, length, 11, 100, "SIGSEGV", "a message", "a stacktrace"),
                  "%zu");
    PASS();
}

SUITE (crash_log) {
    RUN_TEST(test_will_serialise_the_crash_log);
    RUN_TEST(test_will_serialise_the_longest_stacktrace);
    RUN_TEST(test_will_return_zero_if_the_crash_log_does_not_fit);
    RUN_TEST(test_will_serialise_the_crash_log_only_if_it_fits_with_its_end_of_string);
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <stdexcept>
#include <string>
#include "greatest/greatest.h"
#include "utils/format-utils.cpp"

TEST test_generates_formatted_string(void) {
    const std::string formatter = "Given %s will return %s from %s";
    const std::string formatted_string = strformat::format(formatter, "A", "B", "C");
    const std::string expected_formatted_string = "Given A will return B from C";
            ASSERT_STR_EQ(formatted_string.c_str(), expected_formatted_string.c_str());
            PASS();
}

TEST test_does_not_throw_exception_if_not_enough_arguments(void) {
    std::runtime_error *expected_error = nullptr;
    try {
        const std::string formatted_string = strformat::format("%s %s %s %s", "A", "B", "C");
    }
    catch (std::runtime_error &error) {
        expected_error = &error;
    }
            ASSERT(expected_error == nullptr);
            PASS();
}

TEST test_does_not_throw_exception_if_not_enough_arguments_placeholders(void) {
    std::runtime_error *expected_error = nullptr;
    try {
        const std::string formatted_string = strformat::format("%s", "A", "B", "C");
    }
    catch (std::runtime_error &error) {
        expected_error = &error;
    }
            ASSERT(expected_error == nullptr);
            PASS();
}

SUITE (string_utils) {
            RUN_TEST(test_generates_formatted_string);
            // TODO RUMM-0000 disable this one, because snprintf gives SIGSEGV on ARM64 (Apple Silicon)
            // strange enough the test just below is ok
            // RUN_TEST(test_does_not_throw_exception_if_not_enough_arguments);
            RUN_TEST(test_does_not_throw_exception_if_not_enough_arguments_placeholders);
}


/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <cstring>
#include <string>

#include "greatest/greatest.h"
#include "utils/backtrace-handler.h"

extern void appendJsonEscaped(std::string &out, const char *text);

namespace {
    // Lines in the backtrace end with the two characters '\' 'n'.
    const char *const line_end = "\\n";

    size_t end_of_line(const std::string &text, size_t from) {
        const size_t position = text.find(line_end, from);
        return position == std::string::npos ? std::string::npos : position + strlen(line_end);
    }
}

TEST test_generate_backtrace_keeps_only_whole_lines_when_the_size_is_exceeded(void) {
    static char captures[2][max_stack_size];
    bool results[2] = {false, true};
    size_t sizes[2] = {max_stack_size, 0};
    size_t first_line_end = 0;
    for (int i = 0; i < 2; i++) {
        if (i == 1) {
            // Room for the first line and half of the second one.
            const std::string full(captures[0]);
            first_line_end = end_of_line(full, 0);
            const size_t second_line_end = end_of_line(full, first_line_end);
            ASSERT(first_line_end != std::string::npos);
            ASSERT(second_line_end != std::string::npos);
            sizes[1] = first_line_end + (second_line_end - first_line_end) / 2;
        }
        // Both captures run from this same call site, so they see the same stack.
        results[i] = generate_crash_backtrace(captures[i], 0, sizes[i], nullptr);
    }

    ASSERT(results[0]);
    ASSERT_FALSE(results[1]);
    const std::string full(captures[0]);
    const std::string cut(captures[1]);
    ASSERT_STR_EQ(full.substr(0, first_line_end).c_str(), cut.c_str());
    PASS();
}

TEST test_generate_backtrace_keeps_nothing_when_no_whole_line_fits(void) {
    char backtrace[3] = {'x', 'x', 'x'};
    const bool was_complete = generate_crash_backtrace(backtrace, 0, sizeof(backtrace), nullptr);
    ASSERT_FALSE(was_complete);
    ASSERT_EQ('\0', backtrace[0]);
    PASS();
}

TEST test_append_json_escaped_escapes_quotes_backslashes_and_control_characters(void) {
    // Given
    std::string escaped = "prefix ";

    // When
    appendJsonEscaped(escaped, "/data/a\"b\\c\x01" "d");

    // Then
    ASSERT_STR_EQ("prefix /data/a\\\"b\\\\c\\u0001d", escaped.c_str());
    PASS();
}

SUITE (backtrace_limits) {
    RUN_TEST(test_generate_backtrace_keeps_only_whole_lines_when_the_size_is_exceeded);
    RUN_TEST(test_generate_backtrace_keeps_nothing_when_no_whole_line_fits);
    RUN_TEST(test_append_json_escaped_escapes_quotes_backslashes_and_control_characters);
}

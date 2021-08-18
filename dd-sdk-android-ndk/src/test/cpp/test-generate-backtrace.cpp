#include "test-utils.h"

#include <list>
#include <regex>
#include <string>

#include "greatest/greatest.h"
#include "utils/backtrace-handler.h"

TEST test_generate_backtrace(void) {
    char backtrace[max_stack_size];
    const bool was_successful = generate_backtrace(backtrace, 0, max_stack_size);
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(
            backtrace);
    unsigned int lines_count = backtrace_lines.size();
            ASSERT(was_successful);
    const char *regex = "(\\d+)(.*(?<!libdatadog-native-lib\\.so ))0[xX][0-9a-fA-F]+(.*)";
            ASSERT(lines_count > 0 && lines_count <= max_stack_frames);
    for (auto it = backtrace_lines.begin(); it != backtrace_lines.end(); ++it) {
                ASSERT(std::regex_match(it->c_str(), std::regex(regex)));
    }
            PASS();
}

TEST test_generate_backtrace_starts_from_the_given_frame_index(void) {
    const size_t fake_start_frame_index = 2;
    const size_t fake_max_stack_size = 5;
    char backtrace[max_stack_size];
    const bool was_successful = generate_backtrace(backtrace,
                                                   fake_start_frame_index,
                                                   fake_max_stack_size);
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(
            backtrace);
    unsigned int lines_count = backtrace_lines.size();
            ASSERT(was_successful);
            ASSERT(lines_count == (fake_max_stack_size - fake_start_frame_index + 1));
            PASS();
}

TEST test_generate_backtrace_will_return_false_if_size_is_exceeded(void) {
    const size_t backtrace_size = 1;
    char backtrace[backtrace_size];
    const bool was_successful = generate_backtrace(backtrace, 0, backtrace_size);
            ASSERT_FALSE(was_successful);
            PASS();
}

TEST test_generate_backtrace_will_return_truncated_string_if_size_is_exceeded(void) {
    const size_t backtrace_size = 3;
    char backtrace[backtrace_size];
    const bool was_successful = generate_backtrace(backtrace, 0, backtrace_size);
            ASSERT_FALSE(was_successful);
            ASSERT(backtrace[0] != '\0');
            ASSERT(backtrace[1] != '\0');
            PASS();
}


SUITE (backtrace_generation) {
            RUN_TEST(test_generate_backtrace);
            RUN_TEST(test_generate_backtrace_will_return_false_if_size_is_exceeded);
            RUN_TEST(test_generate_backtrace_will_return_truncated_string_if_size_is_exceeded);
            RUN_TEST(test_generate_backtrace_starts_from_the_given_frame_index);
}

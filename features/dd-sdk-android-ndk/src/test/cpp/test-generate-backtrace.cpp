#include "test-utils.h"

#include <list>
#include <regex>
#include <string>

#include "greatest/greatest.h"
#include "utils/backtrace-handler.h"

extern bool copyString(const std::string &str, char *ptr, size_t max_size);

TEST test_generate_backtrace(void) {
    char backtrace[max_stack_size];
    const bool was_successful = generate_crash_backtrace(backtrace, 0, max_stack_size, nullptr);
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(
            backtrace);
    unsigned int lines_count = backtrace_lines.size();
            ASSERT(was_successful);
    const char *regex = "(\\d+)(.*(?<!libdatadog-ndk\\.so ))0[xX][0-9a-fA-F]+(.*)";
            ASSERT(lines_count > 0 && lines_count <= max_stack_frames);
    for (auto it = backtrace_lines.begin(); it != backtrace_lines.end(); ++it) {
                ASSERT(std::regex_match(it->c_str(), std::regex(regex)));
    }
            PASS();
}

TEST test_generate_backtrace_starts_from_the_given_frame_index(void) {
    const size_t fake_start_frame_index = 2;
    static char all_frames[max_stack_size];
    static char backtrace[max_stack_size];
    const bool all_were_complete =
            generate_crash_backtrace(all_frames, 0, max_stack_size, nullptr);
    const bool was_successful = generate_crash_backtrace(backtrace,
                                                         fake_start_frame_index,
                                                         max_stack_size,
                                                         nullptr);
    std::list<std::string> all_lines = testutils::split_backtrace_into_lines(all_frames);
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(backtrace);
            ASSERT(all_were_complete);
            ASSERT(was_successful);
            ASSERT(backtrace_lines.size() + fake_start_frame_index >= all_lines.size());
            PASS();
}

TEST test_generate_backtrace_will_return_false_if_size_is_exceeded(void) {
    const size_t backtrace_size = 1;
    char backtrace[backtrace_size];
    const bool was_successful = generate_crash_backtrace(backtrace, 0, backtrace_size, nullptr);
            ASSERT_FALSE(was_successful);
            PASS();
}

TEST test_generate_backtrace_will_not_cut_a_line_if_size_is_exceeded(void) {
    const size_t backtrace_size = 3;
    char backtrace[backtrace_size];
    const bool was_successful = generate_crash_backtrace(backtrace, 0, backtrace_size, nullptr);
            ASSERT_FALSE(was_successful);
            ASSERT(backtrace[0] == '\0');
            PASS();
}

TEST test_copy_string_will_null_terminate_within_bounds_when_truncated(void) {
    const std::string source = "abcdef";
    char destination[4] = {'x', 'x', 'x', 'z'};

    const bool was_copied = copyString(source, destination, sizeof(destination));

            ASSERT_FALSE(was_copied);
            ASSERT_EQ(destination[0], 'a');
            ASSERT_EQ(destination[1], 'b');
            ASSERT_EQ(destination[2], 'c');
            ASSERT_EQ(destination[3], '\0');
            PASS();
}


SUITE (backtrace_generation) {
            RUN_TEST(test_generate_backtrace);
            RUN_TEST(test_generate_backtrace_will_return_false_if_size_is_exceeded);
            RUN_TEST(test_generate_backtrace_will_not_cut_a_line_if_size_is_exceeded);
            RUN_TEST(test_generate_backtrace_starts_from_the_given_frame_index);
            RUN_TEST(test_copy_string_will_null_terminate_within_bounds_when_truncated);
}

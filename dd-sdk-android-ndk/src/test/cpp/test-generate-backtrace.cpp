#include "test-utils.h"

#include <list>
#include <regex>
#include <string>

#include "greatest/greatest.h"
#include "utils/backtrace-handler.h"

TEST test_generate_backtrace(void) {
    char backtrace[max_stack_size];
    generate_backtrace(backtrace, max_stack_size);
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(
            backtrace);
    // we don't know if the stack is big enough to cover the required max size of 30
    unsigned int lines_count = backtrace_lines.size();
    const char *regex = "(\\d+)(.*)0[xX][0-9a-fA-F]+(.*)";
            ASSERT(lines_count > 0 && lines_count <= 30);
    for (auto it = backtrace_lines.begin(); it != backtrace_lines.end(); ++it) {
                ASSERT(std::regex_match(it->c_str(), std::regex(regex)));
    }
            PASS();
}

TEST test_generate_backtrace_will_return_false_if_size_is_exceeded(void) {
    const size_t backtrace_size = 0;
    char backtrace[backtrace_size];
    const bool was_generated = generate_backtrace(backtrace, backtrace_size);
            ASSERT_FALSE(was_generated);
            PASS();
}

TEST test_generate_backtrace_will_return_truncated_string_if_size_is_exceeded(void) {
    const size_t backtrace_size = 2;
    char backtrace[backtrace_size];
    generate_backtrace(backtrace, backtrace_size);
            ASSERT(backtrace[0]!='\0');
            ASSERT(backtrace[1]!='\0');
            PASS();
}


SUITE (backtrace_generation) {
            RUN_TEST(test_generate_backtrace);
            RUN_TEST(test_generate_backtrace_will_return_false_if_size_is_exceeded);
            RUN_TEST(test_generate_backtrace_will_return_truncated_string_if_size_is_exceeded);
}

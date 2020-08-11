#include "test_utils.h"

#include <list>
#include <regex>
#include <string>

#include "greatest/greatest.h"
#include "utils/backtrace-handler.h"

TEST test_generate_backtrace(void) {
    std::string backtrace = backtrace::generate_backtrace();
    std::list<std::string> backtrace_lines = testutils::split_backtrace_into_lines(
            backtrace.c_str());
    // we don't know if the stack is big enough to cover the required max size of 30
    unsigned int lines_count = backtrace_lines.size();
    const char *regex = "(\\d+):0[xX][0-9a-fA-F]+(\\s*)(.*)";
            ASSERT(lines_count > 0 && lines_count <= 30);
    for (auto it = backtrace_lines.begin(); it != backtrace_lines.end(); ++it) {
                ASSERT(std::regex_match(it->c_str(), std::regex(regex)));
    }
            PASS();
}


SUITE (backtrace_generation) {
            RUN_TEST(test_generate_backtrace);
}

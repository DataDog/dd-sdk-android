#include <regex>
#include "greatest/greatest.h"
#include "utils/datetime-utils.h"

TEST test_generate_event_date_format(void) {
    char buffer[100];
    format_date(buffer, sizeof(buffer) / sizeof(buffer[0]), "%Y-%m-%d'T'%H:%M:%S.000Z");
            ASSERT(std::regex_match(buffer, std::regex(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}'T'[0-9]{2}:[0-9]{2}:[0-9]{2}.000Z")));
            PASS();
}


SUITE (datetime_utils) {
            RUN_TEST(test_generate_event_date_format);
}

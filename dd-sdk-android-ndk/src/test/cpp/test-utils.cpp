/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#include "test-utils.h"

#include <iostream>
#include <list>
#include <string>

namespace testutils {

    std::string substr(char *source, size_t size) {
        char substr[size];
        strncpy(substr, source, size);
        return std::string(substr);
    }

    std::list<std::string> split_backtrace_into_lines(const char *buffer) {
        std::list<std::string> to_return;
        char *current_address = (char *) buffer;
        int substr_length = 0;
        char *start_substr_buffer = (char *) current_address;
        for (char c = *current_address; c; c = *++current_address) {
            // cannot match a single \n character as we specifically
            // add them as separated escaped characters in the backtrace to match the Java one
            if (c == 'n' && *(current_address - 1) == '\\') {
                const std::string x = substr(start_substr_buffer, substr_length - 2);
                to_return.push_back(x);
                // we want to skip the 'n' character next time we do the substring
                start_substr_buffer = (char *) (current_address + 1);
                substr_length = 0;
            }
            substr_length++;
        }
        // add the last line if there was no split character at the end
        if (start_substr_buffer < current_address) {
            to_return.push_front(substr(start_substr_buffer, substr_length));
        }

        return to_return;
    }

}
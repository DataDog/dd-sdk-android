/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#ifndef STRINGUTILS_H
#define STRINGUTILS_H

#include <jni.h>
#include <string>

namespace stringutils {
    std::string copy_to_string(JNIEnv *env, jstring from);

    template<typename... Args>
    std::string format(const char* formatter, Args... args) {
        // first we compute the size of the buffer required to format this string
        const int size = snprintf(nullptr, 0, formatter, args...);
        if (size <= 0) {
            return {};
        }
        // Formatted into the string's own memory (not a variable-length array on the stack),
        // which also holds the end of string /0 character.
        std::string result(static_cast<size_t>(size), '\0');
        snprintf(&result[0], result.size() + 1, formatter, args...);
        return result;
    }
}

#endif

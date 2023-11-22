/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#include <jni.h>
#include <string>


#ifndef STRINGUTILS_H
#define STRINGUTILS_H


namespace stringutils {
    std::string copy_to_string(JNIEnv *env, jstring from);

    template<typename... Args>
    std::string format(const char* formatter, Args... args) {
        // first we compute the size of the buffer required to format this string
        // we will add 1 at the end for the end of string /0 character
        const size_t size = snprintf(nullptr, 0, formatter, args...);
        char buffer[size + 1];
        snprintf(buffer, size + 1, formatter, args...);
        return std::string(buffer, buffer + size);
    }
}

#endif


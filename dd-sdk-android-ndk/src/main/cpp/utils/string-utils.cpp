/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "string-utils.h"

#include <jni.h>
#include <string>
#include <stdexcept>

namespace stringutils {

    std::string copy_to_string(JNIEnv *env, jstring from) {
        if (from == nullptr) {
            return std::string();
        }

        const char *raw_str = env->GetStringUTFChars(from, 0);

        std::string result(raw_str);

        env->ReleaseStringUTFChars(from, raw_str);

        return result;
    }

}

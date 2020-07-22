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
}

#endif


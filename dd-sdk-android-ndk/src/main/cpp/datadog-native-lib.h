/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <jni.h>

#ifndef DATADOG_NATIVE_LIB_H
#define DATADOG_NATIVE_LIB_H


#ifdef __cplusplus
extern "C" {
#endif

void update_main_context(JNIEnv *env,
                         jstring storage_path,
                         jstring service_name,
                         jstring environment);

void update_rum_context(JNIEnv *env,
                        jstring application_id,
                        jstring session_id,
                        jstring view_id);

void update_tracking_consent(jint consent);

void crash_signal_intercepted(int signal, const char *signal_name, const char *error_message);

#ifdef __cplusplus
}
#endif
#endif
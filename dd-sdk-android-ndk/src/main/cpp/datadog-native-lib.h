/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#ifndef DATADOG_NATIVE_LIB_H
#define DATADOG_NATIVE_LIB_H


#ifdef __cplusplus
extern "C" {
#endif

void crash_signal_intercepted(int signal, const char *signal_name, const char *error_message);

#ifdef __cplusplus
}
#endif
#endif
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#ifndef DATETIME_H
#define DATETIME_H

#include "cstdint"

#ifdef __cplusplus
extern "C" {
#endif

uint64_t time_since_epoch();


void format_date(char *buffer, size_t buffer_size, const char *format);

#ifdef __cplusplus
}
#endif
#endif
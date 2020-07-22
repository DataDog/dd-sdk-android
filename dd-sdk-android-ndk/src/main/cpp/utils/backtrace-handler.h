/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <string>

#ifndef BACKTRACE_HANDLER_H
#define BACKTRACE_HANDLER_H

#ifdef __cplusplus
extern "C" {
#endif

namespace backtrace {

    std::string generate_backtrace();
}

#ifdef __cplusplus
}
#endif
#endif
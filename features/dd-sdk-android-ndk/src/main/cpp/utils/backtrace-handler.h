/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#ifndef BACKTRACE_HANDLER_H
#define BACKTRACE_HANDLER_H

#include <stdbool.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

const size_t stack_frames_start_index = 3;
const size_t max_stack_frames = 70 + stack_frames_start_index;
const size_t max_characters_per_stack_frame = 500;
const size_t max_stack_size = max_stack_frames * max_characters_per_stack_frame;
// The serialized crash report: the stack trace plus the other fields, which are short.
const size_t max_serialized_report_size = max_stack_size + 512;

// We cannot use a namespace here as this function will be called from C file (signal_monitor.c)
/**
 * Writes the current stack trace into backtrace_ptr. For a crash, when the unwinder stops at code
 * without unwind tables, the frames after it are recovered from the frame pointer chain (arm64)
 * and added after the unwinder's ones.
 * @param user_context the crash signal's context (ucontext_t), or nullptr to only use the unwinder
 */
bool generate_crash_backtrace(char *backtrace_ptr,
                              size_t start_index,
                              size_t max_size,
                              const void *user_context);

#ifdef __cplusplus
}
#endif
#endif

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#ifndef FP_UNWINDER_H
#define FP_UNWINDER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Recovers the frames the unwinder could not reach because it stopped at code without unwind
 * tables (e.g. code compiled by Mono or Dart, or generated at runtime), by following the frame
 * pointer chain from where it stopped, and appends them after the unwinder's frames. The unwinder's
 * frames are never changed.
 *
 * Only implemented for arm64; elsewhere nothing is appended. Safe to call from a crash signal
 * handler: it allocates no memory; like the unwinder itself, looking up unwind tables may take the
 * dynamic linker's lock.
 *
 * @param frames the unwinder's frames (instruction addresses, starting with the handler's own),
 * followed by room for the recovered ones
 * @param count the number of unwinder frames in frames
 * @param max the capacity of frames
 * @param unwinder_filled_buffer whether the unwinder stopped because frames was full
 * @param last_frame_fp the frame pointer of the last frame the unwinder reached
 * @param user_context the crash signal's context (ucontext_t)
 * @return the number of frames in frames, including the appended ones
 */
size_t fp_unwinder_append_frames(uintptr_t *frames,
                                 size_t count,
                                 size_t max,
                                 bool unwinder_filled_buffer,
                                 uintptr_t last_frame_fp,
                                 const void *user_context);

#ifdef __cplusplus
}
#endif

#endif

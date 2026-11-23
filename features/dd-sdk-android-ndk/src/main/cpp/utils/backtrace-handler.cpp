/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "backtrace-handler.h"

#include <cstdio>
#include <dlfcn.h>
#include <unwind.h>
#include <string>

#include "fp-unwinder.h"

struct BacktraceState {
    uintptr_t *current;
    uintptr_t *end;
    // whether the unwind stopped because the buffer was full
    bool filled;
    // the frame pointer of the last frame reached (arm64)
    uintptr_t last_frame_pointer;
};

namespace {
    _Unwind_Reason_Code unwind_callback(struct _Unwind_Context *context, void *arg) {
        auto *state = static_cast<BacktraceState *>(arg);
        uintptr_t pointer_to_stack_line = _Unwind_GetIP(context);
        if (pointer_to_stack_line) {
            // we have reached the end of the given buffer size so we stop the unwind loop
            if (state->current == state->end) {
                state->filled = true;
                return _URC_END_OF_STACK;
            } else {
                // we set the state->current to current+1, and we set
                // its value as the pointer of the current stack line
                *state->current++ = pointer_to_stack_line;
#if defined(__aarch64__)
                state->last_frame_pointer = _Unwind_GetGR(context, 29);
#endif
            }
        }
        return _URC_NO_REASON;
    }

    // Always inlined, as the release build already did before generate_crash_backtrace existed: the
    // number of our own frames at the top of the trace must stay the same (see
    // stack_frames_start_index).
    __attribute__((always_inline)) inline size_t capture_backtrace(uintptr_t *buffer,
                                                                   size_t max,
                                                                   BacktraceState &state) {
        state = {buffer, buffer + max, false, 0};
        // unwinds the backtrace and fills the buffer with stack lines addresses
        _Unwind_Backtrace(unwind_callback, &state);
        return state.current - buffer;
    }

    void append_hex_address_no_prefix(uintptr_t address, std::string& string) {
        char address_as_hexa[20];
        // The ARM_32 processors will use an unsigned long long to represent a pointer so we will choose the
        // String format that fits both ARM_32 and ARM_64 (lx).
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wformat"
        std::snprintf(address_as_hexa, sizeof(address_as_hexa), "%08lx", address);
#pragma clang diagnostic pop
        string.append(address_as_hexa);
    }

    void get_info_from_address(size_t index,
                               const uintptr_t address,
                               std::string& backtrace) {
        Dl_info info;
        int fetch_info_success = dladdr(reinterpret_cast<void *>(address), &info);
        backtrace.append(std::to_string(index));
        backtrace.append("  pc ");
        // No reason to output relative pointers if we don't have both the name
        // and base address of the shared object
        if (fetch_info_success && info.dli_fbase && info.dli_fname) {
            uintptr_t offset = address - (uintptr_t)info.dli_fbase;
            append_hex_address_no_prefix(offset, backtrace);

            backtrace.append("  ");
            backtrace.append(info.dli_fname);

            if (info.dli_sname) {
                backtrace.append(" (");
                backtrace.append(info.dli_sname);

                if (info.dli_saddr) {
                    uintptr_t symbol_offset = address - (uintptr_t)info.dli_saddr;
                    backtrace.append("+");
                    backtrace.append(std::to_string(symbol_offset));
                }
                backtrace.append(")");
            }
        } else {
            append_hex_address_no_prefix(address, backtrace);
        }

        backtrace.append("\\n");
    }
}

bool copyString(const std::string &str, char *ptr, size_t max_size) {
    size_t str_size = str.size();
    size_t copy_size = std::min(str_size, max_size - 1);
    memcpy(ptr, str.data(), copy_size);
    ptr[copy_size] = '\0';
    return copy_size == str_size;
}

namespace {
    // Always inlined into both entry points, so that the frames at the top of the trace (the ones
    // skipped by start_index) are the same as before.
    __attribute__((always_inline)) inline bool generate(char *backtrace_ptr,
                                                        size_t start_index,
                                                        size_t max_size,
                                                        const void *user_context) {
        // define the buffer which will hold pointers to stack memory addresses
        uintptr_t buffer[max_stack_frames];
        // we will now unwind the stack and capture all the memory addresses up to max_stack_frames in
        // the buffer
        BacktraceState state = {};
        size_t number_of_captured_frames = capture_backtrace(buffer, max_stack_frames, state);
        if (user_context != nullptr) {
            // For a crash, recover the frames the unwinder could not reach (code without unwind
            // tables), after the ones it found.
            number_of_captured_frames = fp_unwinder_append_frames(buffer,
                                                                  number_of_captured_frames,
                                                                  max_stack_frames,
                                                                  state.filled,
                                                                  state.last_frame_pointer,
                                                                  user_context);
        }
        std::string backtrace;
        backtrace.reserve(max_size);
        std::string line;
        line.reserve(max_characters_per_stack_frame);
        bool complete = true;
        for (size_t idx = start_index; idx < number_of_captured_frames; ++idx) {
            // we will iterate through all the stack addresses and translate each address in
            // readable information
            line.clear();
            get_info_from_address(idx - start_index, buffer[idx], line);
            if (backtrace.length() + line.length() >= max_size) {
                // No room left for this whole line (and the end of string \0 character): stop here
                // rather than cutting the line, so every frame in the report is complete.
                complete = false;
                break;
            }
            backtrace.append(line);
        }
        return copyString(backtrace, backtrace_ptr, max_size) && complete;
    }
}

bool generate_backtrace(char *backtrace_ptr, size_t start_index, size_t max_size) {
    return generate(backtrace_ptr, start_index, max_size, nullptr);
}

bool generate_crash_backtrace(char *backtrace_ptr,
                              size_t start_index,
                              size_t max_size,
                              const void *user_context) {
    return generate(backtrace_ptr, start_index, max_size, user_context);
}

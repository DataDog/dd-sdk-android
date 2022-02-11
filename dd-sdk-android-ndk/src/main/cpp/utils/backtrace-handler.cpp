/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "backtrace-handler.h"

#include <cstdlib>
#include <cstdio>
#include <dlfcn.h>
#include <fcntl.h>
#include <iomanip>
#include <iosfwd>
#include <unwind.h>
#include <unistd.h>
#include <string>

struct BacktraceState {
    uintptr_t *current;
    uintptr_t *end;
};

namespace {
    _Unwind_Reason_Code unwind_callback(struct _Unwind_Context *context, void *arg) {
        auto *state = static_cast<BacktraceState *>(arg);
        uintptr_t pointer_to_stack_line = _Unwind_GetIP(context);
        if (pointer_to_stack_line) {
            // we have reached the end of the given buffer size so we stop the unwind loop
            if (state->current == state->end) {
                return _URC_END_OF_STACK;
            } else {
                // we set the state->current to current+1 and we set
                // its value as the pointer of the current stack line
                *state->current++ = pointer_to_stack_line;
            }
        }
        return _URC_NO_REASON;
    }

    size_t capture_backtrace(uintptr_t *buffer, size_t max) {
        BacktraceState state = {buffer, buffer + max};
        // unwinds the backtrace and fills the buffer with stack lines addresses
        _Unwind_Backtrace(unwind_callback, &state);
        return state.current - buffer;
    }

    std::string address_to_hexa(uintptr_t address) {
        char address_as_hexa[20];
        // The ARM_32 processors will use an unsigned long long to represent a pointer so we will choose the
        // String format that fits both ARM_32 and ARM_64 (lx).
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wformat"
        std::snprintf(address_as_hexa, sizeof(address_as_hexa), "0x%lx", address);
#pragma clang diagnostic pop
        return std::string(address_as_hexa);
    }

    void get_info_from_address(size_t index,
                               const uintptr_t address,
                               std::string *backtrace) {
        Dl_info info;
        int fetch_info_success = dladdr(reinterpret_cast<void *>(address), &info);
        backtrace->append(std::to_string(index));
        if (fetch_info_success) {

            if (info.dli_fname) {
                backtrace->append(" ");
                backtrace->append(info.dli_fname);
            }

            backtrace->append(" ");
            backtrace->append(address_to_hexa(address));

            if (info.dli_sname) {
                backtrace->append(" ");
                backtrace->append(info.dli_sname);
            }

            if (info.dli_fbase) {
                backtrace->append(" ");
                backtrace->append("+");
                backtrace->append(" ");
                auto address_offset = address - reinterpret_cast<uintptr_t>(info.dli_fbase);
                backtrace->append(std::to_string(address_offset));
            }
        } else {
            backtrace->append(" ");
            backtrace->append(address_to_hexa(address));
        }

        backtrace->append("\\n");
    }

}

bool copyString(const std::string &str, char *ptr, size_t max_size) {
    size_t str_size = str.size();
    size_t copy_size = std::min(str_size, max_size - 1);
    memcpy(ptr, str.data(), copy_size);
    ptr[str.size()] = '\0';
    return copy_size == str_size;
}

bool generate_backtrace(char *backtrace_ptr, size_t start_index, size_t max_size) {
    // define the buffer which will hold pointers to stack memory addresses
    uintptr_t buffer[max_stack_frames];
    // we will now unwind the stack and capture all the memory addresses up to max_stack_frames in
    // the buffer
    const size_t number_of_captured_frames = capture_backtrace(buffer, max_stack_frames);
    std::string backtrace;
    for (size_t idx = start_index; idx < number_of_captured_frames; ++idx) {
        // we will iterate through all the stack addresses and translate each address in
        // readable information
        get_info_from_address(idx - start_index, buffer[idx], &backtrace);
    }
    return copyString(backtrace, backtrace_ptr, max_size);
}





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


static const size_t STACK_SIZE = 30;

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

    void get_info_from_address(const uintptr_t address, std::string *backtrace) {
        backtrace->append(std::to_string(address));
        Dl_info info;
        int fetch_info_success = dladdr(reinterpret_cast<void *>(address), &info);
        if (fetch_info_success) {

            if (info.dli_fname) {
                backtrace->append("  ");
                backtrace->append(info.dli_fname);
            }

            backtrace->append("  ");
            backtrace->append(address_to_hexa(address));

            if (info.dli_sname) {
                backtrace->append("  ");
                backtrace->append(info.dli_sname);
            }

            if (info.dli_fbase) {
                backtrace->append(" ");
                backtrace->append("+");
                backtrace->append(" ");
                const uintptr_t address_offset =
                        address - reinterpret_cast<uintptr_t>(info.dli_fbase);
                backtrace->append(std::to_string(address_offset));
            }

        }

        backtrace->append("\\n");
    }

}

namespace backtrace {

    std::string generate_backtrace() {
        // define the buffer which will hold pointers to stack memory addresses
        uintptr_t buffer[STACK_SIZE];
        // we will now unwind the stack and capture all the memory addresses up to STACK_SIZE in
        // the buffer
        const size_t captured_stacksize = capture_backtrace(buffer, STACK_SIZE);
        std::string backtrace;
        for (size_t idx = 0; idx < captured_stacksize; ++idx) {
            // we will iterate through all the stack addresses and translate each address in
            // readable informationdsadsa
            get_info_from_address(buffer[idx], &backtrace);

        }
        return backtrace;
    }


}



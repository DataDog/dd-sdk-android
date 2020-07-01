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

    const char *get_line_symbol(const void *addr) {
        const char *symbol = "";
        Dl_info info;
        if (dladdr(addr, &info) && info.dli_sname) {
            symbol = info.dli_sname;
        }
        return symbol;
    }

}

namespace backtrace {
    const char *generate_backtrace() {
        // define the buffer which will hold pointers to stack memory addresses
        uintptr_t buffer[STACK_SIZE];
        // we will now unwind the stack and capture all the memory addresses up to STACK_SIZE in
        // the buffer
        const size_t captured_stacksize = capture_backtrace(buffer, STACK_SIZE);

        std::string backtrace;
        for (size_t idx = 0; idx < captured_stacksize; ++idx) {
            // we will iterate through all the stack addresses and translate each address in
            // readable information
            void *addr = reinterpret_cast<void *>(buffer[idx]);
            const int hexa_address_buffer_size = 20;
            char address_as_hexa[20];
            std::snprintf(address_as_hexa, hexa_address_buffer_size, "0x%x", buffer[idx]);

            const char *lineSymbol = get_line_symbol(addr);
            backtrace.append("  ");
            backtrace.append(std::to_string(idx));
            backtrace.append(": ");
            backtrace.append(address_as_hexa);
            backtrace.append("  ");
            backtrace.append(lineSymbol);
            backtrace.append("\\n");
        }
        return backtrace.c_str();
    }

}



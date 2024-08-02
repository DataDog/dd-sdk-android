/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "signal-monitor.h"

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "backtrace-handler.h"
#include "datadog-native-lib.h"

static const char *LOG_TAG = "DatadogNdkCrashReporter";
static stack_t signal_stack;

/* the original sigaction array which will hold the original handlers for each overridden signal */
volatile struct sigaction *volatile original_sigactions = NULL;

/* pre-allocated memory for working with the backtrace */
char* backtrace_scratch = NULL;

volatile bool handlers_installed = false;

// for testing purposes
#ifndef NDEBUG
int performed_install_ops = 0;
int performed_uninstall_ops = 0;
#endif

void recordInstallOp() {
#ifndef NDEBUG
    performed_install_ops++;
#endif
}

void recordUninstallOp() {
#ifndef NDEBUG
    performed_uninstall_ops++;
#endif
}

/**
 * Native signals which will be captured by the signal handler™
 */

struct signal {
    int signal_value;
    char *signal_name;
    char *signal_error_message;
};

static const struct signal handled_signals[] = {
        {SIGILL,  "SIGILL",  "Illegal instruction"},
        {SIGBUS,  "SIGBUS",  "Bus error (bad memory access)"},
        {SIGFPE,  "SIGFPE",  "Floating-point exception"},
        {SIGABRT, "SIGABRT", "The process was terminated"},
        {SIGSEGV, "SIGSEGV", "Segmentation violation (invalid memory reference)"},
        {SIGTRAP, "SIGTRAP", "Signal trap"}
};

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

size_t handled_signals_size() { return sizeof(handled_signals) / sizeof(handled_signals[0]); }


void free_up_memory() {
    if (backtrace_scratch != NULL) {
        free((void*) backtrace_scratch);
        backtrace_scratch = NULL;
    }
    if (original_sigactions != NULL) {
        free((void *) original_sigactions);
        original_sigactions = NULL;
    }
    if (signal_stack.ss_sp != NULL) {
        free(signal_stack.ss_sp);
        signal_stack.ss_sp = NULL;
    }
}


void invoke_previous_handler(int signum, siginfo_t *info, void *user_context) {
    // It may happen that the process is killed during this function execution.
    // Therefore this function may never return.

    if(pthread_mutex_trylock(&mutex) != 0){
        // There is no action to take if the mutex cannot be acquired.
        // In this case will just return here in order not to block the process.
        return;
    }

    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; ++i) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {
            struct sigaction previous = original_sigactions[i];
            // From sigaction(2):
            //  > If act is non-zero, it specifies an action (SIG_DFL, SIG_IGN, or a
            //  handler routine)
            if (previous.sa_flags & SA_SIGINFO) {
                // This handler can handle signal number, info, and user context
                // (POSIX). From sigaction(2): > If this bit is set, the handler
                // function is assumed to be pointed to by the sa_sigaction member of
                // struct sigaction and should match the proto- type shown above or as
                // below in EXAMPLES.  This bit should not be set when assigning SIG_DFL
                // or SIG_IGN.
                previous.sa_sigaction(signum, info, user_context);
            } else if (previous.sa_handler == SIG_DFL) {
                // raise to trigger the default handler. It cannot be called directly.
                raise(signum);

            } else if (previous.sa_handler != SIG_IGN) {
                // This handler can only handle to signal number (ANSI C)
                void (*previous_handler)(int) = previous.sa_handler;
                previous_handler(signum);
            }
        }
    }

    pthread_mutex_unlock(&mutex);
}

void uninstall_handlers() {
    if (!handlers_installed) {
        return;
    }
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        volatile struct sigaction *volatile original_action = &original_sigactions[i];
        if (original_action) {
            const int signal = handled_signals[i].signal_value;
            sigaction(signal, (struct sigaction *) original_action, 0);
        }
    }
    recordUninstallOp();
    handlers_installed = false;
}

void handle_signal(int signum, siginfo_t *info, void *user_context) {
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {

            // in case the stacktrace is bigger than the required size it will be truncated
            // because we are unwinding the stack strace at this level we are always going to
            // to have for the top 3 levels at the top of the trace the executed lines from our
            // library: handle_signal, generate_backtrace and capture_backtrace.
            // If you are changing this code make sure to start from the new frame
            // index when generating the backtrace.
            generate_backtrace(backtrace_scratch, stack_frames_start_index, max_stack_size);


            write_crash_report(signal,
                               handled_signals[i].signal_name,
                               handled_signals[i].signal_error_message,
                               backtrace_scratch);

            break;
        }
    }

    // We need to uninstall our custom handlers otherwise we will go in a continuous loop when
    // calling the prev handler (sigaction) with this signum.
    uninstall_handlers();
    invoke_previous_handler(signum, info, user_context);
}

bool configure_signal_stack() {

    // we increase the intercepted signal stack size to be able to handle the memory allocation
    // to unwind the backstack. By using the SA_ONSTACK flag in the sigaction below,
    // we are specifically requesting that the signal handler should be executed in a freshly new
    // signal stack for which we allocate extra memory here.
    // Art is already allocating memory for the signal stack here:
    // https://android.googlesource.com/platform/art/+/master/runtime/thread_linux.cc#35) but
    // because we need a bit more than that we will re - allocate it on our end.
    size_t expected_stack_size = 32 * 1024; // 32K -- same as the ART but on  our own dedicated stack
    size_t stack_size = expected_stack_size < MINSIGSTKSZ ? MINSIGSTKSZ : expected_stack_size;
    if ((signal_stack.ss_sp = calloc(1, stack_size)) == NULL) {
        return false;
    }
    signal_stack.ss_size = stack_size;
    signal_stack.ss_flags = 0;
    if (sigaltstack(&signal_stack, 0) < 0) {
        free(signal_stack.ss_sp);
        signal_stack.ss_sp=NULL;
        return false;
    }

    // Create a scratch area for grabbing the backtrace
    backtrace_scratch = malloc(max_stack_size * sizeof(char));
    if (backtrace_scratch == NULL) {
        return false;
    }

    return true;
}

bool override_native_signal_handlers() {
    struct sigaction datadog_sigaction = {};

    if (sigemptyset((sigset_t *) &datadog_sigaction.sa_mask) != 0) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Not able to initialize the Datadog signal handler");
        return false;
    }
    datadog_sigaction.sa_sigaction = handle_signal;
    // we will use the SA_ONSTACK mask here to handle the signal in a freshly new signal stack.
    datadog_sigaction.sa_flags = SA_SIGINFO | SA_ONSTACK;

    const size_t signals_array_size = handled_signals_size();
    original_sigactions = calloc(signals_array_size, sizeof(struct sigaction));
    if (original_sigactions == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Not able to allocate the memory to persist the original handlers");
        return false;
    }
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        int success = sigaction(signal, &datadog_sigaction,
                                (struct sigaction *) &original_sigactions[i]);
        if (success != 0) {
            __android_log_print(ANDROID_LOG_ERROR,
                                LOG_TAG,
                                "Not able to catch the signal: %d",
                                signal);
        }
    }
    recordInstallOp();
    return true;
}

bool try_to_install_handlers() {
    if (handlers_installed) {
        return true;
    }
    handlers_installed = configure_signal_stack() && override_native_signal_handlers();
    return handlers_installed;
}

bool start_monitoring() {
    pthread_mutex_lock(&mutex);
    bool installed = try_to_install_handlers();
    if (installed) {
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG,
                            "Successfully installed Datadog NDK signal handlers");
    } else {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Unable to install Datadog NDK signal handlers");
    }
    pthread_mutex_unlock(&mutex);
    return installed;
}

void stop_monitoring() {
    pthread_mutex_lock(&mutex);
    uninstall_handlers();
    free_up_memory();
    pthread_mutex_unlock(&mutex);
}


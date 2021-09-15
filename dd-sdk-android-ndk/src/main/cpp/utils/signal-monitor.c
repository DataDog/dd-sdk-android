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

/* the datadog sigaction which overrides the original signal handler */
static volatile struct sigaction *volatile datadog_sigaction;

/* the original sigaction array which will hold the original handlers for each overridden signal */
volatile struct sigaction *volatile original_sigaction;

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
        {SIGSEGV, "SIGSEGV", "Segmentation violation (invalid memory reference)"}
};

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

size_t handled_signals_size() { return sizeof(handled_signals) / sizeof(handled_signals[0]); }


void free_up_memory() {
    if (datadog_sigaction != NULL) {
        free((void *) datadog_sigaction);
        datadog_sigaction = NULL;
    }
    if (original_sigaction != NULL) {
        free((void *) original_sigaction);
        original_sigaction = NULL;
    }
    if (signal_stack.ss_sp != NULL) {
        free(signal_stack.ss_sp);
        signal_stack.ss_sp = NULL;
    }
}


void invoke_previous_handler(int signum, siginfo_t *info, void *user_context) {
    // It may happen that the process is killed during this function execution.
    // Therefore this function may never return.
    pthread_mutex_lock(&mutex);

    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; ++i) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {
            struct sigaction previous = original_sigaction[i];
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
        volatile struct sigaction *volatile original_action = &original_sigaction[i];
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
            char backtrace[max_stack_size];
            // in case the stacktrace is bigger than the required size it will be truncated
            // because we are unwinding the stack strace at this level we are always going to
            // to have for the top 3 levels at the top of the trace the executed lines from our
            // library: handle_signal, generate_backtrace and capture_backtrace.
            // If you are changing this code make sure to start from the new frame
            // index when generating the backtrace.
            generate_backtrace(backtrace, stack_frames_start_index, max_stack_size);


            write_crash_report(signal,
                               handled_signals[i].signal_name,
                               handled_signals[i].signal_error_message,
                               backtrace);

            break;
        }
    }

    // We need to uninstall our custom handlers otherwise we will go in a continuous loop when
    // calling the prev handler (sigaction) with this signum.
    uninstall_handlers();
    invoke_previous_handler(signum, info, user_context);
}

bool configure_signal_stack() {
    // we increase the intercepted signal stack size to double the normal size
    static size_t stackSize = SIGSTKSZ * 2;
    if ((signal_stack.ss_sp = calloc(1, stackSize)) == NULL) {
        return false;
    }
    signal_stack.ss_size = stackSize;
    signal_stack.ss_flags = 0;
    if (sigaltstack(&signal_stack, 0) < 0) {
        return false;
    }
    return true;
}

bool init_datadog_signal_handler() {
    datadog_sigaction = malloc(sizeof(struct sigaction));
    if (datadog_sigaction == NULL) {
        return false;
    }
    if (sigemptyset((sigset_t *) &datadog_sigaction->sa_mask) != 0) {
        return false;
    }
    datadog_sigaction->sa_sigaction = handle_signal;
    // we will use the SA_ONSTACK mask here to handle the signal in a freshly new stack.
    datadog_sigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;

    return true;
}

bool override_native_signal_handlers() {
    if (!init_datadog_signal_handler()) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Not able to initialize the Datadog signal handler");
        return false;
    }
    const size_t signals_array_size = handled_signals_size();
    original_sigaction = calloc(signals_array_size, sizeof(struct sigaction));
    if (original_sigaction == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Not able to allocate the memory to persist the original handlers");
        return false;
    }
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        int success = sigaction(signal, (struct sigaction *) datadog_sigaction,
                                (struct sigaction *) &original_sigaction[i]);
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
    pthread_mutex_unlock(&mutex);
    return installed;
}

void stop_monitoring() {
    pthread_mutex_lock(&mutex);
    uninstall_handlers();
    free_up_memory();
    pthread_mutex_unlock(&mutex);
}


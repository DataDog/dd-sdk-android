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

#include "../datadog-native-lib.h"

static const char *LOG_TAG = "DatadogNdkCrashReporter";
static sigset_t signals_mask;
static stack_t signal_stack;

/* the signal handler array */
static struct sigaction *global_sigaction;

/* the previous signal handler array */
static struct sigaction *global_previous_sigaction;

static pthread_t datadog_watchdog_thread;

bool handlers_installed = false;

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

// TODO https://datadoghq.atlassian.net/browse/RUMM-576
// We should use a Hashtable (Hashmap) here. For now I could not find something in C so probably
// will have to implement one or just use a pure array[key] -> value implementation even though
// this will take more memory.
static const struct signal handled_signals[] = {
        {SIGILL,  "SIGILL",  "Illegal instruction"},
        {SIGBUS,  "SIGBUS",  "Bus error (bad memory access)"},
        {SIGFPE,  "SIGFPE",  "Floating-point exception"},
        {SIGABRT,  "SIGABRT",  "The process was terminated"},
        {SIGSEGV, "SIGSEGV", "Segmentation violation (invalid memory reference)"},
        {SIGQUIT, "SIGQUIT", "Application Not Responding"}
};

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

size_t handled_signals_size() { return sizeof(handled_signals) / sizeof(handled_signals[0]); }

// It may happen that the process is killed during this function execution.
// Therefore this function may never return.
void invoke_previous_handler(int signum, siginfo_t *info, void *user_context) {

    pthread_mutex_lock(&mutex);

    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; ++i) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {
            struct sigaction previous = global_previous_sigaction[i];
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

void handle_signal(int signum, siginfo_t *info, void *user_context) {
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {
            crash_signal_intercepted(signal,
                                     handled_signals[i].signal_name,
                                     handled_signals[i].signal_error_message);
            break;
        }
    }

    // We need to uninstall our custom handlers otherwise we will go in a continuous loop when
    // calling the prev handler (sigaction) with this signum.
    uninstall_signal_handlers();
    invoke_previous_handler(signum, info, user_context);
}

bool configure_signal_stack() {
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

bool configure_global_sigaction() {
    global_sigaction = calloc(handled_signals_size(), sizeof(struct sigaction));
    if (global_sigaction == NULL) {
        return false;
    }
    sigemptyset(&global_sigaction->sa_mask);
    global_sigaction->sa_sigaction = handle_signal;
    // we will use the SA_ONSTACK mask here to handle the signal in a freshly new stack.
    global_sigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;

    return true;
}

// This function should be called inside a locked mutex for Thread safety
bool override_native_signal_handlers() {
    if (handlers_installed) {
        return false;
    }
    const size_t signals_array_size = handled_signals_size();
    global_previous_sigaction = calloc(signals_array_size, sizeof(struct sigaction));
    if (global_previous_sigaction == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, "Was not able to initialise.");
        return false;
    }
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        int success = sigaction(signal, global_sigaction,
                                &global_previous_sigaction[i]);
        if (success != 0) {
            __android_log_print(ANDROID_LOG_ERROR,
                                LOG_TAG,
                                "Was not able to catch the signal: %d",
                                signal);
        }
    }
    handlers_installed = true;
    recordInstallOp();
    return true;
}

void *initialize_watchdog_thread() {
    pthread_mutex_lock(&mutex);
    override_native_signal_handlers();
    pthread_mutex_unlock(&mutex);
    return NULL;
}

bool start_watchdog_thread() {
    bool success = false;
    sigemptyset(&signals_mask);
    // Main thread does not block by default the SIGQUIT signals so we will have to
    // block it and start a new thread for signals handling. When starting a new thread
    // from main thread the new one will take the signals mask of the parent.
    sigaddset(&signals_mask, SIGQUIT);
    if (pthread_sigmask(SIG_BLOCK, &signals_mask, NULL) == 0) {
        if (pthread_create(&datadog_watchdog_thread, NULL,
                           initialize_watchdog_thread, NULL) != 0) {
            __android_log_write(ANDROID_LOG_ERROR,
                                LOG_TAG,
                                "Was not able to create the watchdog thread");
        }

        // we restore the defaults on the main thread
        if (pthread_sigmask(SIG_UNBLOCK, &signals_mask, NULL) != 0) {
            __android_log_write(ANDROID_LOG_ERROR,
                                LOG_TAG,
                                "Was not able to restore the mask on SIGQUIT signal");
        }
        success = true;

    } else {
        __android_log_write(ANDROID_LOG_ERROR,
                            LOG_TAG,
                            "Was not able to mask SIGQUIT signal");
    }

    return success;
}

bool try_to_install_handlers() {
    if (handlers_installed) {
        return true;
    }

    return (configure_signal_stack() &&
            configure_global_sigaction() &&
            start_watchdog_thread());
}

bool install_signal_handlers() {
    pthread_mutex_lock(&mutex);
    bool installed = try_to_install_handlers();
    pthread_mutex_unlock(&mutex);
    return installed;
}

void free_up_memory() {
    if (global_sigaction != NULL) {
        free(global_sigaction);
    }
    if (global_previous_sigaction != NULL) {
        free(global_previous_sigaction);
    }
    if (signal_stack.ss_sp != NULL) {
        free(signal_stack.ss_sp);
    }
    global_sigaction = NULL;
    global_previous_sigaction = NULL;
    signal_stack.ss_sp = NULL;
}

void uninstall_signal_handlers() {
    pthread_mutex_lock(&mutex);

    if (!handlers_installed) {
        pthread_mutex_unlock(&mutex);
        return;
    }

    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        struct sigaction *prev_action = &global_previous_sigaction[i];
        if (prev_action) {
            const int signal = handled_signals[i].signal_value;
            sigaction(signal, prev_action, 0);
        }
    }

    free_up_memory();
    recordUninstallOp();
    handlers_installed = false;
    pthread_mutex_unlock(&mutex);
}


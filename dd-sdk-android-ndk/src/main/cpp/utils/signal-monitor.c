/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "signal-monitor.h"

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

void handle_signal(int signum, siginfo_t *info,
                   void *user_context);

bool configure_signal_stack(void);

stack_t signal_stack;
/**
 * Global shared context reports
 */
static JNIEnv *global_env;
/* the signal handler array */
struct sigaction *global_sigaction;

/* the previous signal handler array */
struct sigaction *global_previous_sigaction;

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
        {SIGTRAP, "SIGTRAP", "Trace/breakpoint trap"},
        {SIGABRT, "SIGABRT", "Abort program"},
        {SIGBUS,  "SIGBUS",  "Floating-point exception"},
        {SIGFPE,  "SIGFPE",  "Bus error (bad memory access)"},
        {SIGSEGV, "SIGSEGV", "Segmentation violation (invalid memory reference)"}
};

static pthread_mutex_t handlerMutex = PTHREAD_MUTEX_INITIALIZER;

size_t handled_signals_size() { return sizeof(handled_signals) / sizeof(handled_signals[0]); }


bool configure_global_sigaction() {
    global_sigaction = calloc(sizeof(struct sigaction), handled_signals_size());
    if (global_sigaction == NULL) {
        return false;
    }
    sigemptyset(&global_sigaction->sa_mask);
    global_sigaction->sa_sigaction = handle_signal;
    global_sigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;

    return true;
}

bool override_native_signal_handlers() {
    const size_t signals_array_size = handled_signals_size();
    global_previous_sigaction = calloc(sizeof(struct sigaction), signals_array_size);
    if (global_previous_sigaction == NULL) {
        return false;
    }
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        int success = sigaction(signal, global_sigaction,
                                &global_previous_sigaction[i]);
        if (success != 0) {
            return false;
        }
    }
    return true;
}

bool try_to_install_handlers(JNIEnv *env) {
    if (global_env != NULL) {
        return true; // already installed
    }
    global_env = env;

    if (!configure_signal_stack()) {
        return false;
    }

    if (!configure_global_sigaction()) {
        return false;
    }

    return override_native_signal_handlers();
}


bool install_signal_handlers(JNIEnv *env) {
    pthread_mutex_lock(&handlerMutex);
    bool installed = try_to_install_handlers(env);
    pthread_mutex_unlock(&handlerMutex);

    return installed;
}


void uninstall_signal_handlers() {
    if (global_env == NULL)
        return;

    pthread_mutex_lock(&handlerMutex);

    if (global_env == NULL) {
        pthread_mutex_unlock(&handlerMutex);
        return;
    }
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        sigaction(signal, &global_previous_sigaction[i], 0);
    }
    global_env = NULL;

    pthread_mutex_unlock(&handlerMutex);
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

void invoke_previous_handler(int signum, siginfo_t *info,
                             void *user_context) {

    pthread_mutex_lock(&handlerMutex);

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

    pthread_mutex_unlock(&handlerMutex);
}

void handle_signal(int signum, siginfo_t *info,
                   void *user_context) {
    int is_initialized = 0;
    pthread_mutex_lock(&handlerMutex);
    is_initialized = (global_env == NULL);
    pthread_mutex_unlock(&handlerMutex);
    if (is_initialized) return;

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
    uninstall_signal_handlers();
    invoke_previous_handler(signum, info, user_context);
}

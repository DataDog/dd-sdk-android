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
static sigset_t signals_mask;
static stack_t signal_stack;

/* the datadog sigaction which overrides the original signal handler */
static volatile struct sigaction *volatile datadog_sigaction;

/* the original sigaction array which will hold the original handlers for each overridden signal */
volatile struct sigaction *volatile original_sigaction;

static pthread_t override_signal_handlers_thread;

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
        {SIGQUIT, "SIGQUIT", "Application Not Responding"}
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
            generate_backtrace(backtrace, max_stack_size);
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
    sigemptyset((sigset_t *) &datadog_sigaction->sa_mask);
    datadog_sigaction->sa_sigaction = handle_signal;
    // we will use the SA_ONSTACK mask here to handle the signal in a freshly new stack.
    datadog_sigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;

    return true;
}

/*
 * Overriding the signal handlers from the new spawned thread. This will allow us to block (handle)
 * also the `SIQGUIT` signal triggered when the process is unresponsive - ANR.
 * This function should be called inside a locked mutex for Thread safety
 */
bool override_native_signal_handlers() {
    if (handlers_installed) {
        return false;
    }
    init_datadog_signal_handler();
    const size_t signals_array_size = handled_signals_size();
    original_sigaction = calloc(signals_array_size, sizeof(struct sigaction));
    if (original_sigaction == NULL) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, "Was not able to initialise.");
        return false;
    }
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        int success = sigaction(signal, (struct sigaction *) datadog_sigaction,
                                (struct sigaction *) &original_sigaction[i]);
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

void *override_handlers_runnable() {
    pthread_mutex_lock(&mutex);
    override_native_signal_handlers();
    pthread_mutex_unlock(&mutex);
    return NULL;
}
/*
 * We need to perform the handlers override operation in this new thread as the main thread does
 * not block by default the SIGQUIT signals so we will have to
 * block it and start a new thread for signals handling. When starting a new thread
 * from main thread the new one will take the signals mask of the parent.
 */
bool spawn_thread_to_override_signal_handlers() {
    bool success = false;
    sigemptyset(&signals_mask);
    sigaddset(&signals_mask, SIGQUIT);
    // try to modify main thread signal masks by marking the `SIGQUIT` as blockable
    if (pthread_sigmask(SIG_BLOCK, &signals_mask, NULL) == 0) {
        // spawning a new thread will take the current signal mask from the main thread
        if (pthread_create(&override_signal_handlers_thread, NULL,
                           override_handlers_runnable, NULL) != 0) {
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
            spawn_thread_to_override_signal_handlers());
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


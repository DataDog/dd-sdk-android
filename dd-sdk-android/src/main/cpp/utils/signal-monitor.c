#include "signal-monitor.h"
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <jni.h>
#include "../datadog-native-lib.h"

#define HANDLED_SIGNAL_COUNT 6

void handleSignal(int signum, siginfo_t *info,
                  void *user_context);

bool configureSignalStack(void);

stack_t signalStack;
/**
 * Global shared context for Bugsnag reports
 */
static JNIEnv *globalEnv;
/* the signal handler array */
struct sigaction *globalSigaction;

/* the previous signal handler array */
struct sigaction *globalSigactionPrevious;

/**
 * Native signals which will be captured by the Bugsnag signal handler™
 */
static const int native_signals[HANDLED_SIGNAL_COUNT + 1] = {
        SIGILL, SIGTRAP, SIGABRT, SIGBUS, SIGFPE, SIGSEGV};
static const char signal_name[HANDLED_SIGNAL_COUNT + 1][8] = {
        "SIGILL", "SIGTRAP", "SIGABRT", "SIGBUS", "SIGFPE", "SIGSEGV"};
static const char signal_generic_error_message[HANDLED_SIGNAL_COUNT + 1][60] = {
        "Illegal instruction",
        "Trace/breakpoint trap",
        "Abort program",
        "Bus error (bad memory access)",
        "Floating-point exception",
        "Segmentation violation (invalid memory reference)"};


bool installSignalHandlers(JNIEnv *env) {
    if (globalEnv != NULL) {
        return true; // already installed
    }
    static pthread_mutex_t handlerMutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&handlerMutex);
    if (!configureSignalStack()) {
        pthread_mutex_unlock(&handlerMutex);
        return false;
    }

    globalEnv = env;
    globalSigaction =
            calloc(sizeof(struct sigaction), HANDLED_SIGNAL_COUNT);
    if (globalSigaction == NULL) {
        pthread_mutex_unlock(&handlerMutex);
        return false;
    }
    sigemptyset(&globalSigaction->sa_mask);
    globalSigaction->sa_sigaction = handleSignal;
    globalSigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;

    globalSigactionPrevious =
            calloc(sizeof(struct sigaction), HANDLED_SIGNAL_COUNT);
    if (globalSigactionPrevious == NULL) {
        pthread_mutex_unlock(&handlerMutex);
        return false;
    }
    for (int i = 0; i < HANDLED_SIGNAL_COUNT; i++) {
        const int signal = native_signals[i];
        int success = sigaction(signal, globalSigaction,
                                &globalSigactionPrevious[i]);
        if (success != 0) {
            pthread_mutex_unlock(&handlerMutex);
            return false;
        }
    }

    pthread_mutex_unlock(&handlerMutex);

    return true;
}

void uninstallSignalHandlers() {
    if (globalEnv == NULL)
        return;
    for (int i = 0; i < HANDLED_SIGNAL_COUNT; i++) {
        const int signal = native_signals[i];
        sigaction(signal, &globalSigactionPrevious[i], 0);
    }
    globalEnv = NULL;
}

bool configureSignalStack() {
    static size_t stackSize = SIGSTKSZ * 2;
    if ((signalStack.ss_sp = calloc(1, stackSize)) == NULL) {
        return false;
    }
    signalStack.ss_size = stackSize;
    signalStack.ss_flags = 0;
    if (sigaltstack(&signalStack, 0) < 0) {
        return false;
    }
    return true;
}

void invokePreviousSignalHandler(int signum, siginfo_t *info,
                                 void *user_context) {
    for (
            int i = 0;
            i < HANDLED_SIGNAL_COUNT; ++i) {
        const int signal = native_signals[i];
        if (signal == signum) {
            struct sigaction previous = globalSigactionPrevious[i];
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
                previous.
                        sa_sigaction(signum, info, user_context
                );
            } else if (previous.sa_handler == SIG_DFL) {
                raise(signum); // raise to trigger the default handler. It cannot be
// called directly.
            } else if (previous.sa_handler != SIG_IGN) {
// This handler can only handle to signal number (ANSI C)
                void (*previous_handler)(int) = previous.sa_handler;

                previous_handler(signum);
            }
        }
    }
}

void handleSignal(int signum, siginfo_t *info,
                  void *user_context) {
    if (globalEnv == NULL) {
        return;
    }

    for (int i = 0; i < HANDLED_SIGNAL_COUNT; i++) {
        const int signal = native_signals[i];
        if (signal == signum) {
            onCrashSignalIntercepted(signal, signal_name[i], signal_generic_error_message[i]);
            break;
        }
    }
    uninstallSignalHandlers();
    invokePreviousSignalHandler(signum, info, user_context);
}
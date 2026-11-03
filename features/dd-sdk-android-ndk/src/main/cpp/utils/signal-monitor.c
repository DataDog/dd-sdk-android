/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "signal-monitor.h"

#include <android/log.h>
#include <pthread.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <ucontext.h>

#include "backtrace-handler.h"
#include "datadog-ndk.h"

static const char *LOG_TAG = "DatadogNdkCrashReporter";
/*
 * Alternate stacks are per thread, so each thread that starts monitoring keeps its own: it is
 * reused when monitoring starts again on that thread, and stays registered there otherwise.
 * Only accessed when starting monitoring, never from the signal handler.
 */
static _Thread_local stack_t thread_signal_stack;

/*
 * The signal stack, the original sigactions and the backtrace scratch area are allocated once and
 * never freed: a handler running on another thread may still use them while monitoring is
 * stopped, and the signal stack stays registered with sigaltstack on the thread that set it up.
 */

/* the original sigaction array which will hold the original handlers for each overridden signal */
volatile struct sigaction *volatile original_sigactions = NULL;

/* pre-allocated memory for working with the backtrace */
char* backtrace_scratch = NULL;

volatile bool handlers_installed = false;

/*
 * When true, fault signals are first forwarded to the previously installed handler, and are not
 * reported if that handler recovered from them. See handle_signal for details.
 * Lock-free atomic: it is written by any thread registering an SDK core and read in the handler.
 */
static atomic_bool forward_fault_signals_first = false;
_Static_assert(ATOMIC_BOOL_LOCK_FREE == 2, "atomic_bool must be lock-free to be used in a signal handler");

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


static void call_sigaction(const struct sigaction *action,
                           int signum,
                           siginfo_t *info,
                           void *user_context) {
    // From sigaction(2):
    //  > If act is non-zero, it specifies an action (SIG_DFL, SIG_IGN, or a
    //  handler routine)
    if (action->sa_flags & SA_SIGINFO) {
        // This handler can handle signal number, info, and user context
        // (POSIX). From sigaction(2): > If this bit is set, the handler
        // function is assumed to be pointed to by the sa_sigaction member of
        // struct sigaction and should match the prototype shown above or as
        // below in EXAMPLES.  This bit should not be set when assigning SIG_DFL
        // or SIG_IGN.
        action->sa_sigaction(signum, info, user_context);
    } else if (action->sa_handler == SIG_DFL) {
        // raise to trigger the default handler. It cannot be called directly.
        raise(signum);
    } else if (action->sa_handler != SIG_IGN) {
        // This handler can only handle to signal number (ANSI C)
        void (*previous_handler)(int) = action->sa_handler;
        previous_handler(signum);
    }
}

void invoke_previous_handler(int signum, siginfo_t *info, void *user_context) {
    // It may happen that the process is killed during this function execution.
    // Therefore, this function may never return.

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
            call_sigaction(&previous, signum, info, user_context);
        }
    }

    pthread_mutex_unlock(&mutex);
}

static bool is_fault_signal(int signum) {
    return signum == SIGSEGV || signum == SIGBUS || signum == SIGFPE;
}

static bool read_registers(const void *user_context, uintptr_t *ip, uintptr_t *sp) {
    if (user_context == NULL) {
        return false;
    }
    const ucontext_t *uc = (const ucontext_t *) user_context;
#if defined(__aarch64__)
    *ip = (uintptr_t) uc->uc_mcontext.pc;
    *sp = (uintptr_t) uc->uc_mcontext.sp;
#elif defined(__arm__)
    *ip = (uintptr_t) uc->uc_mcontext.arm_pc;
    *sp = (uintptr_t) uc->uc_mcontext.arm_sp;
#elif defined(__x86_64__)
    *ip = (uintptr_t) uc->uc_mcontext.gregs[REG_RIP];
    *sp = (uintptr_t) uc->uc_mcontext.gregs[REG_RSP];
#elif defined(__i386__)
    *ip = (uintptr_t) uc->uc_mcontext.gregs[REG_EIP];
    *sp = (uintptr_t) uc->uc_mcontext.gregs[REG_ESP];
#else
    (void) uc;
    return false;
#endif
    return true;
}

/*
 * Forwards the fault signal to the previously installed handler before we handle it.
 *
 * Managed runtimes such as Mono (.NET MAUI) install their own handler and rely on fault signals
 * for regular control flow: a null member access in compiled C# code faults, and the runtime
 * turns the fault into a NullReferenceException by rewriting the instruction and stack pointers
 * of the interrupted context before returning. In that case the signal is not a crash.
 *
 * @return true if the previous handler recovered from the signal, false otherwise.
 */
static bool forward_to_previous_handler_first(int signum, siginfo_t *info, void *user_context) {
    if (original_sigactions == NULL) {
        return false;
    }
    uintptr_t ip_before = 0;
    uintptr_t sp_before = 0;
    if (!read_registers(user_context, &ip_before, &sp_before)) {
        return false;
    }
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; ++i) {
        if (handled_signals[i].signal_value != signum) {
            continue;
        }
        struct sigaction previous = original_sigactions[i];
        // A default or ignored action cannot recover from a fault. Raising it here while our
        // handler is still installed would only deliver the signal to us again.
        bool is_handler_routine = (previous.sa_flags & SA_SIGINFO) ||
                                  (previous.sa_handler != SIG_DFL &&
                                   previous.sa_handler != SIG_IGN);
        if (!is_handler_routine) {
            return false;
        }
        // The previous handler runs with our signal mask, which includes its own sa_mask (merged
        // when our handler is installed) and keeps the signal blocked: when it cannot recover, it
        // may chain to a handler that re-raises the signal (e.g. Mono chaining to debuggerd),
        // which would otherwise kill the process before the crash report is written. The mask is
        // not changed here: ART's sigchain removes the signals it claims (e.g. SIGSEGV) from
        // masks set through pthread_sigmask.
        call_sigaction(&previous, signum, info, user_context);
        uintptr_t ip_after = 0;
        uintptr_t sp_after = 0;
        read_registers(user_context, &ip_after, &sp_after);
        return ip_after != ip_before || sp_after != sp_before;
    }
    return false;
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
    if (atomic_load_explicit(&forward_fault_signals_first, memory_order_relaxed) &&
        is_fault_signal(signum) &&
        forward_to_previous_handler_first(signum, info, user_context)) {
        // The previous handler recovered: execution resumes where it redirected the context.
        // This is not a crash, so we neither report it nor uninstall our handlers.
        return;
    }

    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        if (signal == signum) {

            // in case the stacktrace is bigger than the required size it will be truncated
            // because we are unwinding the stack strace at this level we are always going to
            // have for the top 3 levels at the top of the trace the executed lines from our
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
    // https://android.googlesource.com/platform/art/+/master/runtime/thread_linux.cc#35 but
    // because we need a bit more than that we will re - allocate it on our end.
    size_t expected_stack_size = 32 * 1024; // 32K -- same as the ART but on our own dedicated stack
    size_t stack_size = expected_stack_size < MINSIGSTKSZ ? MINSIGSTKSZ : expected_stack_size;
    stack_t stack = thread_signal_stack;
    const bool needs_new_stack = stack.ss_sp == NULL;
    if (needs_new_stack) {
        if ((stack.ss_sp = calloc(1, stack_size)) == NULL) {
            return false;
        }
        stack.ss_size = stack_size;
        stack.ss_flags = 0;
    }
    if (sigaltstack(&stack, 0) < 0) {
        if (needs_new_stack) {
            // never registered, so nothing can be running on it
            free(stack.ss_sp);
        }
        return false;
    }
    thread_signal_stack = stack;

    // Create a scratch area for grabbing the backtrace
    if (backtrace_scratch == NULL) {
        backtrace_scratch = malloc(max_stack_size * sizeof(char));
    }
    if (backtrace_scratch == NULL) {
        return false;
    }

    return true;
}

static bool init_datadog_sigaction(struct sigaction *datadog_sigaction) {
    if (sigemptyset((sigset_t *) &datadog_sigaction->sa_mask) != 0) {
        return false;
    }
    datadog_sigaction->sa_sigaction = handle_signal;
    // we will use the SA_ONSTACK mask here to handle the signal in a freshly new signal stack.
    datadog_sigaction->sa_flags = SA_SIGINFO | SA_ONSTACK;
    return true;
}

/*
 * When forwarding, the previous handler runs inside ours, so it needs the signals it asked to
 * block (e.g. Mono blocks its GC suspend signal while handling SIGSEGV on an altstack). Our own
 * mask is empty, so it becomes the saved previous handler's mask. This runs on every start, as
 * forwarding can be enabled by an SDK core registering after the handlers were installed.
 */
static void apply_previous_masks() {
    if (!atomic_load_explicit(&forward_fault_signals_first, memory_order_relaxed) ||
        original_sigactions == NULL) {
        return;
    }
    struct sigaction action = {};
    if (!init_datadog_sigaction(&action)) {
        return;
    }
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        if (!is_fault_signal(signal)) {
            continue;
        }
        // Only update our own handler: one installed on top of ours since must not be replaced.
        struct sigaction current = {};
        if (sigaction(signal, NULL, &current) != 0 ||
            !(current.sa_flags & SA_SIGINFO) || current.sa_sigaction != handle_signal) {
            continue;
        }
        action.sa_mask = original_sigactions[i].sa_mask;
        sigaction(signal, &action, NULL);
    }
}

bool override_native_signal_handlers() {
    struct sigaction datadog_sigaction = {};

    if (!init_datadog_sigaction(&datadog_sigaction)) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Not able to initialize the Datadog signal handler");
        return false;
    }

    const size_t signals_array_size = handled_signals_size();
    if (original_sigactions == NULL) {
        original_sigactions = calloc(signals_array_size, sizeof(struct sigaction));
    }
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
        apply_previous_masks();
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG,
                            "Successfully installed Datadog NDK signal handlers");
    } else {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "Unable to install Datadog NDK signal handlers");
    }
    pthread_mutex_unlock(&mutex);
    return installed;
}

void enable_forward_fault_signals_first() {
    atomic_store_explicit(&forward_fault_signals_first, true, memory_order_relaxed);
}

#ifndef NDEBUG
void reset_forward_fault_signals_first() {
    atomic_store_explicit(&forward_fault_signals_first, false, memory_order_relaxed);
}
#endif

void stop_monitoring() {
    pthread_mutex_lock(&mutex);
    uninstall_handlers();
    pthread_mutex_unlock(&mutex);
}


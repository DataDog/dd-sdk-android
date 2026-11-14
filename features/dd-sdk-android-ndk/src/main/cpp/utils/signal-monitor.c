/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "signal-monitor.h"

#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <ucontext.h>
#include <unistd.h>

#include "backtrace-handler.h"
#include "datadog-ndk.h"

static const char *LOG_TAG = "DatadogNdkCrashReporter";
/*
 * Alternate stacks are per thread, so each thread that starts monitoring keeps its own: it is
 * reused when monitoring starts again on that thread, stays registered there otherwise, and is
 * released when the thread exits. Only accessed when starting monitoring and at thread exit.
 */
static pthread_key_t signal_stack_key;
static pthread_once_t signal_stack_key_once = PTHREAD_ONCE_INIT;
static bool signal_stack_key_created = false;
static const size_t signal_stack_size = 32 * 1024;

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
 * Mono's lookup of managed code by instruction address and its current domain getter (public
 * embedding API), taken from the library of a previous handler that belongs to Mono. See
 * handle_signal.
 */
typedef void *(*find_managed_code_fn)(void *domain, void *address);
typedef void *(*get_current_domain_fn)(void);
static _Atomic(find_managed_code_fn) find_managed_code = NULL;
static _Atomic(get_current_domain_fn) get_current_domain = NULL;

/* Implicit null checks only cover the first page, see Mono's mono_is_addr_implicit_null_check. */
static uintptr_t page_size = 0;

_Static_assert(ATOMIC_BOOL_LOCK_FREE == 2, "atomic bools must be lock-free in a signal handler");
_Static_assert(ATOMIC_POINTER_LOCK_FREE == 2, "atomic pointers must be lock-free in a signal handler");

// for testing purposes
#ifndef NDEBUG
int performed_install_ops = 0;
int performed_uninstall_ops = 0;
int released_signal_stacks = 0;
static bool mono_detection_ignored = false;
static int mono_detection_ignored_signal = 0;
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

#define HANDLED_SIGNALS_COUNT 6
_Static_assert(sizeof(handled_signals) / sizeof(handled_signals[0]) == HANDLED_SIGNALS_COUNT,
               "HANDLED_SIGNALS_COUNT must match handled_signals");

/*
 * For each handled signal, whether its previous handler belongs to Mono, in which case the faults
 * Mono can recover from are forwarded to it first. Published after Mono's lookup and masks.
 */
static atomic_bool forwarded_to_mono[HANDLED_SIGNALS_COUNT];

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
 * Whether the given handler belongs to Mono. If so, sets Mono's lookup of managed code and current
 * domain getter.
 */
static bool find_mono_lookup(int signum,
                             const struct sigaction *handler,
                             find_managed_code_fn *find,
                             get_current_domain_fn *root) {
#ifndef NDEBUG
    if (mono_detection_ignored || mono_detection_ignored_signal == signum) {
        return false;
    }
#else
    (void) signum;
#endif
    void *handler_address = NULL;
    if (handler->sa_flags & SA_SIGINFO) {
        memcpy(&handler_address, &handler->sa_sigaction, sizeof(handler_address));
    }
    Dl_info handler_library;
    if (handler_address == NULL || dladdr(handler_address, &handler_library) == 0 ||
        handler_library.dli_fname == NULL) {
        return false;
    }
    void *library = dlopen(handler_library.dli_fname, RTLD_NOW | RTLD_NOLOAD);
    if (library == NULL) {
        return false;
    }
    void *find_symbol = dlsym(library, "mono_jit_info_table_find");
    void *root_symbol = dlsym(library, "mono_domain_get");
    // Mono is never unloaded, so its functions stay valid without our reference.
    dlclose(library);
    // dlsym also searches dependencies: only Mono's own library counts, not one linking it.
    Dl_info symbol_library;
    if (find_symbol == NULL || root_symbol == NULL ||
        dladdr(find_symbol, &symbol_library) == 0 ||
        symbol_library.dli_fbase != handler_library.dli_fbase) {
        return false;
    }
    memcpy(find, &find_symbol, sizeof(*find));
    memcpy(root, &root_symbol, sizeof(*root));
    return true;
}

/*
 * Whether Mono can recover from the fault by throwing a managed exception, mirroring its handlers:
 * SIGFPE in managed code (division by zero), and SIGSEGV or SIGBUS from a data access in the first
 * page (an implicit null check) in managed code.
 *
 * Any other fault is a crash: it is reported before any other handler runs, as without forwarding.
 * Mono would chain it to debuggerd, and the system kills the process as soon as debuggerd reports
 * the crash, possibly before our report is written.
 */
static bool mono_may_recover(int signum, const siginfo_t *info, void *user_context) {
    const find_managed_code_fn find =
            atomic_load_explicit(&find_managed_code, memory_order_relaxed);
    const get_current_domain_fn root =
            atomic_load_explicit(&get_current_domain, memory_order_relaxed);
    uintptr_t ip = 0;
    uintptr_t sp = 0;
    if (find == NULL || root == NULL || info == NULL || !read_registers(user_context, &ip, &sp)) {
        return false;
    }
    const bool data_fault = signum == SIGSEGV || signum == SIGBUS;
    const uintptr_t fault_address = (uintptr_t) info->si_addr;
    if (data_fault && (fault_address == ip || fault_address > page_size)) {
        return false;
    }
    // As in Mono's handler: a thread without a current domain is not running managed code.
    void *domain = root();
    if (domain == NULL) {
        return false;
    }
    return find(domain, (void *) ip) != NULL;
}

enum forward_result {
    FORWARD_NOT_CALLED,
    FORWARD_RECOVERED,
    FORWARD_NOT_RECOVERED
};

/*
 * Forwards the fault signal to the previously installed handler before we handle it.
 *
 * Mono (e.g. .NET MAUI) relies on fault signals for regular control flow: a null member access in
 * compiled C# code faults, and Mono turns the fault into a NullReferenceException by rewriting the
 * instruction and stack pointers of the interrupted context before returning. In that case the
 * signal is not a crash.
 */
static enum forward_result forward_to_previous_handler_first(int signum,
                                                             siginfo_t *info,
                                                             void *user_context) {
    if (original_sigactions == NULL) {
        return FORWARD_NOT_CALLED;
    }
    uintptr_t ip_before = 0;
    uintptr_t sp_before = 0;
    if (!read_registers(user_context, &ip_before, &sp_before)) {
        return FORWARD_NOT_CALLED;
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
            return FORWARD_NOT_CALLED;
        }
        // The previous handler runs with our signal mask, which includes its own sa_mask (applied
        // when our handler is installed) and keeps the signal blocked: if it cannot recover after
        // all, it may chain to a handler that re-raises the signal (e.g. Mono chaining to
        // debuggerd), which would otherwise kill the process before the crash report is written.
        // The mask is not changed here: ART's sigchain removes the signals it claims (e.g.
        // SIGSEGV) from masks set through pthread_sigmask.
        call_sigaction(&previous, signum, info, user_context);
        uintptr_t ip_after = 0;
        uintptr_t sp_after = 0;
        read_registers(user_context, &ip_after, &sp_after);
        return ip_after != ip_before || sp_after != sp_before
               ? FORWARD_RECOVERED
               : FORWARD_NOT_RECOVERED;
    }
    return FORWARD_NOT_CALLED;
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

static bool is_forwarded_to_mono(int signum) {
    const size_t signals_array_size = handled_signals_size();
    for (int i = 0; i < signals_array_size; i++) {
        if (handled_signals[i].signal_value == signum) {
            return atomic_load_explicit(&forwarded_to_mono[i], memory_order_acquire);
        }
    }
    return false;
}

void handle_signal(int signum, siginfo_t *info, void *user_context) {
    enum forward_result forwarded = FORWARD_NOT_CALLED;
    if (is_fault_signal(signum) && is_forwarded_to_mono(signum) &&
        mono_may_recover(signum, info, user_context)) {
        forwarded = forward_to_previous_handler_first(signum, info, user_context);
        if (forwarded == FORWARD_RECOVERED) {
            // Execution resumes where the previous handler redirected the context. This is not
            // a crash, so we neither report it nor uninstall our handlers.
            return;
        }
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
    // A forwarded hardware fault reaches the previous handler again when the faulting
    // instruction runs on return, so it is not called a second time. A sent signal does not run
    // again and is passed on.
    if (forwarded == FORWARD_NOT_RECOVERED && info != NULL && info->si_code > 0) {
        return;
    }
    invoke_previous_handler(signum, info, user_context);
}

static void release_signal_stack(void *stack_memory) {
    stack_t current = {};
    const bool registered = sigaltstack(NULL, &current) == 0 && current.ss_sp == stack_memory;
    if (registered) {
        stack_t disabled = {};
        disabled.ss_flags = SS_DISABLE;
        if (sigaltstack(&disabled, NULL) != 0) {
            // still registered, so it must stay allocated
            return;
        }
    }
    free(stack_memory);
#ifndef NDEBUG
    released_signal_stacks++;
#endif
}

static void create_signal_stack_key() {
    signal_stack_key_created = pthread_key_create(&signal_stack_key, release_signal_stack) == 0;
}

bool configure_signal_stack() {

    // we increase the intercepted signal stack size to be able to handle the memory allocation
    // to unwind the backstack. By using the SA_ONSTACK flag in the sigaction below,
    // we are specifically requesting that the signal handler should be executed in a freshly new
    // signal stack for which we allocate extra memory here.
    // Art is already allocating memory for the signal stack here:
    // https://android.googlesource.com/platform/art/+/master/runtime/thread_linux.cc#35 but
    // because we need a bit more than that we will re - allocate it on our end.
    // 32K -- same as the ART but on our own dedicated stack
    size_t stack_size = signal_stack_size < MINSIGSTKSZ ? MINSIGSTKSZ : signal_stack_size;
    pthread_once(&signal_stack_key_once, create_signal_stack_key);
    if (!signal_stack_key_created) {
        return false;
    }
    stack_t stack = {};
    stack.ss_sp = pthread_getspecific(signal_stack_key);
    const bool needs_new_stack = stack.ss_sp == NULL;
    if (needs_new_stack && (stack.ss_sp = calloc(1, stack_size)) == NULL) {
        return false;
    }
    stack.ss_size = stack_size;
    stack.ss_flags = 0;
    if (sigaltstack(&stack, 0) < 0) {
        if (needs_new_stack) {
            // never registered, so nothing can be running on it
            free(stack.ss_sp);
        }
        return false;
    }
    if (needs_new_stack && pthread_setspecific(signal_stack_key, stack.ss_sp) != 0) {
        // not released at thread exit, but it is registered, so it must stay allocated
        __android_log_write(ANDROID_LOG_WARN, LOG_TAG,
                            "Unable to track the signal stack of this thread");
    }

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

static bool is_same_action(const struct sigaction *first, const struct sigaction *second) {
    return first->sa_flags == second->sa_flags &&
           ((first->sa_flags & SA_SIGINFO)
            ? first->sa_sigaction == second->sa_sigaction
            : first->sa_handler == second->sa_handler);
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
        atomic_store_explicit(&forwarded_to_mono[i], false, memory_order_relaxed);
    }
    page_size = (uintptr_t) getpagesize();
    bool any_forwarded = false;
    for (int i = 0; i < signals_array_size; i++) {
        const int signal = handled_signals[i].signal_value;
        struct sigaction action = datadog_sigaction;
        struct sigaction current = {};
        find_managed_code_fn find = NULL;
        get_current_domain_fn root = NULL;
        // Forwarding is prepared before our handler becomes active, so a managed fault on another
        // thread is never reported while we install. Mono runs inside our handler, so it gets the
        // signals it asked to block (e.g. its GC suspend signal while handling SIGSEGV on an
        // altstack): our own mask is empty, so it becomes the previous handler's mask.
        const bool forwarded = is_fault_signal(signal) &&
                               sigaction(signal, NULL, &current) == 0 &&
                               find_mono_lookup(signal, &current, &find, &root);
        if (forwarded) {
            action.sa_mask = current.sa_mask;
            atomic_store_explicit(&find_managed_code, find, memory_order_relaxed);
            atomic_store_explicit(&get_current_domain, root, memory_order_relaxed);
            atomic_store_explicit(&forwarded_to_mono[i], true, memory_order_release);
        }
        int success = sigaction(signal, &action, (struct sigaction *) &original_sigactions[i]);
        struct sigaction previous = original_sigactions[i];
        if (forwarded && (success != 0 || !is_same_action(&current, &previous))) {
            // Another handler was installed in between: it is not the Mono handler we checked.
            atomic_store_explicit(&forwarded_to_mono[i], false, memory_order_release);
        }
        any_forwarded = any_forwarded ||
                        atomic_load_explicit(&forwarded_to_mono[i], memory_order_relaxed);
        if (success != 0) {
            __android_log_print(ANDROID_LOG_ERROR,
                                LOG_TAG,
                                "Not able to catch the signal: %d",
                                signal);
        }
    }
    if (any_forwarded) {
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG,
                            "Mono found: forwarding fault signals it can recover from first");
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

#ifndef NDEBUG
void ignore_mono_detection(bool ignored) {
    mono_detection_ignored = ignored;
    mono_detection_ignored_signal = 0;
    mono_detection_ignored_signal = 0;
}

void ignore_mono_detection_for(int signum) {
    mono_detection_ignored_signal = signum;
}
#endif

void stop_monitoring() {
    pthread_mutex_lock(&mutex);
    uninstall_handlers();
    pthread_mutex_unlock(&mutex);
}


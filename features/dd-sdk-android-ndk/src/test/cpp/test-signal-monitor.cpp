#include <csignal>
#include <cstring>
#include <thread>
#include <ucontext.h>
#include <unistd.h>

#include "greatest/greatest.h"
#include "utils/signal-monitor.h"

extern bool handlers_installed;
extern "C" void ignore_mono_detection(bool ignored);
extern "C" void ignore_mono_detection_for(int signum);

// The fake runtime handler lives in this library, so it is resolved as the runtime we forward to.
static bool fake_mono_reports_managed_code = true;
static int fake_jit_info = 0;

static bool fake_mono_has_domain = true;
static int fake_domain = 0;

extern "C" __attribute__((visibility("default"))) void *mono_jit_info_table_find(void *, void *) {
    return fake_mono_reports_managed_code ? &fake_jit_info : nullptr;
}

extern "C" __attribute__((visibility("default"))) void *mono_domain_get() {
    return fake_mono_has_domain ? &fake_domain : nullptr;
}

extern struct sigaction *original_sigactions;
extern char *backtrace_scratch;

extern "C" void handle_signal(int signum, siginfo_t *info, void *user_context);


#ifndef NDEBUG
extern int performed_install_ops;
extern int performed_uninstall_ops;
extern int released_signal_stacks;
#endif

void clear_tests() {
#ifndef NDEBUG
    // reset the signal_monitor state
    stop_monitoring();
    performed_install_ops = 0;
    performed_uninstall_ops = 0;
#endif
}

bool performedUninstall(int times) {
#ifndef NDEBUG
    return performed_uninstall_ops == times;
#else
    return true;
#endif
}

bool performedInstall(int times) {
#ifndef NDEBUG
    return performed_install_ops == times;
#else
    return true;
#endif
}


TEST calling_start_monitoring_will_install_signal_handlers(void) {
    start_monitoring();
            ASSERT(handlers_installed);
    clear_tests();
            PASS();
}

TEST calling_start_monitoring_more_times_in_a_row_will_only_install_once(void) {
    start_monitoring();
    start_monitoring();
    start_monitoring();
            ASSERT(handlers_installed);
            ASSERT(performedInstall(1));
    clear_tests();
            PASS();
}


TEST calling_stop_monitoring_will_uninstall_the_handlers(void) {
    // given
    start_monitoring();
    // when
    stop_monitoring();
            ASSERT_FALSE(handlers_installed);
    clear_tests();
            PASS();
}

TEST calling_stop_monitoring_more_times_in_a_row_will_only_uninstall_once(void) {
    // given
    start_monitoring();
    // when
    stop_monitoring();
    stop_monitoring();
    stop_monitoring();
            ASSERT_FALSE(handlers_installed);
            ASSERT(performedUninstall(1));
    clear_tests();
            PASS();
}

TEST calling_start_monitor_from_different_threads_will_only_install_once(void) {
    auto start_monitoring_lambda = [] {
        start_monitoring();
    };
    std::thread t1 = std::thread(start_monitoring_lambda);
    std::thread t2 = std::thread(start_monitoring_lambda);
    t1.join();
    t2.join();
            ASSERT(handlers_installed);
            ASSERT(performedInstall(1));
    clear_tests();
            PASS();
}

TEST calling_start_monitor_from_different_threads_will_not_corrupt_the_memory(void) {
    auto start_monitoring_lambda = [] {
        start_monitoring();
    };
    std::thread t1 = std::thread(start_monitoring_lambda);
    std::thread t2 = std::thread(start_monitoring_lambda);
    t1.join();
    t2.join();
    // as the watchdog reference will change here we need to make sure we are waiting for both
    // to finish so we will sleep for 5 seconds
    sleep(5);
    int sigactions_size = 0;
    const int handled_signals = 6;
    struct sigaction *end_pointer = original_sigactions + handled_signals;
    struct sigaction *pointer = original_sigactions;
    while (pointer != end_pointer && pointer != nullptr) {
        sigactions_size++;
        pointer++;
    }
            ASSERT(handlers_installed);
            ASSERT(performedInstall(1));
            ASSERT_EQ(sigactions_size, handled_signals);
    clear_tests();
            PASS();
}

TEST calling_stop_monitoring_from_2_different_threads_will_only_uninstall_once(void) {
    // given
    start_monitoring();
    // when
    auto stop_monitoring_lambda = [] {
        stop_monitoring();
    };
    std::thread t1 = std::thread(stop_monitoring_lambda);
    std::thread t2 = std::thread(stop_monitoring_lambda);
    t1.join();
    t2.join();
            ASSERT_FALSE(handlers_installed);
            ASSERT(performedUninstall(1));
    clear_tests();
            PASS();
}


static int fake_runtime_handler_calls = 0;
static bool fake_runtime_recovers = false;

static void advance_instruction_pointer(ucontext_t *uc) {
#if defined(__aarch64__)
    uc->uc_mcontext.pc += 4;
#elif defined(__arm__)
    uc->uc_mcontext.arm_pc += 4;
#elif defined(__x86_64__)
    uc->uc_mcontext.gregs[REG_RIP] += 4;
#elif defined(__i386__)
    uc->uc_mcontext.gregs[REG_EIP] += 4;
#endif
}

static void fake_runtime_handler(int, siginfo_t *, void *user_context) {
    fake_runtime_handler_calls++;
    if (fake_runtime_recovers) {
        advance_instruction_pointer(static_cast<ucontext_t *>(user_context));
    }
}

static struct sigaction previous_sigsegv_action;
static struct sigaction previous_sigabrt_action;
static struct sigaction previous_sigfpe_action;

static void install_fake_runtime_handler(bool recovers) {
    fake_runtime_handler_calls = 0;
    fake_runtime_recovers = recovers;
    struct sigaction fake_action = {};
    sigemptyset(&fake_action.sa_mask);
    sigaddset(&fake_action.sa_mask, SIGUSR2);
    fake_action.sa_sigaction = fake_runtime_handler;
    fake_action.sa_flags = SA_SIGINFO;
    sigaction(SIGSEGV, &fake_action, &previous_sigsegv_action);
    sigaction(SIGABRT, &fake_action, &previous_sigabrt_action);
    sigaction(SIGFPE, &fake_action, &previous_sigfpe_action);
}

static void clear_fake_runtime_tests() {
    clear_tests();
    ignore_mono_detection(false);
    fake_mono_reports_managed_code = true;
    fake_mono_has_domain = true;
    sigaction(SIGSEGV, &previous_sigsegv_action, nullptr);
    sigaction(SIGABRT, &previous_sigabrt_action, nullptr);
    sigaction(SIGFPE, &previous_sigfpe_action, nullptr);
}

static void set_instruction_pointer(ucontext_t *uc, uintptr_t ip) {
#if defined(__aarch64__)
    uc->uc_mcontext.pc = ip;
#elif defined(__arm__)
    uc->uc_mcontext.arm_pc = ip;
#elif defined(__x86_64__)
    uc->uc_mcontext.gregs[REG_RIP] = (greg_t) ip;
#elif defined(__i386__)
    uc->uc_mcontext.gregs[REG_EIP] = (greg_t) ip;
#endif
}

static const uintptr_t fake_data_address = 0x10;

static void simulate_signal_at(int signum,
                               uintptr_t ip,
                               uintptr_t fault_address = fake_data_address,
                               int code = SEGV_MAPERR) {
    siginfo_t info = {};
    info.si_signo = signum;
    info.si_code = code;
    info.si_addr = (void *) fault_address;
    ucontext_t context;
    memset(&context, 0, sizeof(context));
    set_instruction_pointer(&context, ip);
    handle_signal(signum, &info, &context);
}

static void simulate_signal(int signum) {
    simulate_signal_at(signum, 0);
}

static uintptr_t address_in_libc() { return (uintptr_t) &strlen; }

TEST forwarding_first_will_keep_the_handlers_when_the_previous_handler_recovers(void) {
    // given
    install_fake_runtime_handler(true);
    start_monitoring();
    // when
    simulate_signal(SIGSEGV);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    const bool uninstalled_none = performedUninstall(0);
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT(installed);
            ASSERT(uninstalled_none);
            PASS();
}

TEST forwarding_first_will_handle_the_crash_when_the_previous_handler_does_not_recover(void) {
    // given
    install_fake_runtime_handler(false);
    start_monitoring();
    // when
    simulate_signal(SIGSEGV);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    const bool uninstalled_once = performedUninstall(1);
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            ASSERT(uninstalled_once);
            PASS();
}

TEST forwarding_first_will_not_apply_to_non_fault_signals(void) {
    // given
    install_fake_runtime_handler(true);
    start_monitoring();
    // when
    simulate_signal(SIGABRT);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            PASS();
}

TEST calling_stop_monitoring_will_keep_the_signal_handling_memory_allocated(void) {
    // given
    start_monitoring();
    struct sigaction *sigactions_before = original_sigactions;
    char *scratch_before = backtrace_scratch;
    stack_t signal_stack_before;
    sigaltstack(nullptr, &signal_stack_before);
    // when
    stop_monitoring();
    // then
    struct sigaction *sigactions_after_stop = original_sigactions;
    char *scratch_after_stop = backtrace_scratch;
    start_monitoring();
    stack_t signal_stack_after_restart;
    sigaltstack(nullptr, &signal_stack_after_restart);
    clear_tests();
            ASSERT(sigactions_before != nullptr);
            ASSERT(scratch_before != nullptr);
            ASSERT_EQ(sigactions_before, sigactions_after_stop);
            ASSERT_EQ(scratch_before, scratch_after_stop);
            ASSERT_EQ(signal_stack_before.ss_sp, signal_stack_after_restart.ss_sp);
            PASS();
}

static bool installed_sigsegv_action_blocks(int signum) {
    struct sigaction installed = {};
    sigaction(SIGSEGV, nullptr, &installed);
    return sigismember(&installed.sa_mask, signum) == 1;
}

TEST forwarding_first_will_install_the_handler_with_the_previous_handler_mask(void) {
    // given
    install_fake_runtime_handler(true);
    // when
    start_monitoring();
    // then
    const bool blocks_previous_mask = installed_sigsegv_action_blocks(SIGUSR2);
    clear_fake_runtime_tests();
            ASSERT(blocks_previous_mask);
            PASS();
}

TEST installing_the_handlers_will_not_merge_the_previous_mask_when_forwarding_first_is_disabled(void) {
    // given
    install_fake_runtime_handler(true);
    ignore_mono_detection(true);
    // when
    start_monitoring();
    // then
    const bool blocks_previous_mask = installed_sigsegv_action_blocks(SIGUSR2);
    clear_fake_runtime_tests();
            ASSERT_FALSE(blocks_previous_mask);
            PASS();
}

TEST starting_monitoring_again_on_a_thread_will_reuse_its_signal_stack(void) {
    // given
    start_monitoring();
    stack_t first_stack;
    sigaltstack(nullptr, &first_stack);
    stop_monitoring();
    std::thread t([] {
        start_monitoring();
        stop_monitoring();
    });
    t.join();
    // when
    start_monitoring();
    // then
    stack_t stack_after_restart;
    sigaltstack(nullptr, &stack_after_restart);
    clear_tests();
            ASSERT(first_stack.ss_sp != nullptr);
            ASSERT_EQ(first_stack.ss_sp, stack_after_restart.ss_sp);
            PASS();
}

TEST a_thread_exiting_will_release_its_signal_stack(void) {
#ifndef NDEBUG
    // given
    const int released_before = released_signal_stacks;
    // when
    std::thread t([] {
        start_monitoring();
        stop_monitoring();
    });
    t.join();
    // then
    const int released = released_signal_stacks - released_before;
    clear_tests();
            ASSERT_EQ(1, released);
#endif
            PASS();
}

TEST starting_monitoring_from_another_thread_will_register_a_distinct_signal_stack(void) {
    // given
    start_monitoring();
    stack_t first_thread_stack;
    sigaltstack(nullptr, &first_thread_stack);
    stop_monitoring();
    // when
    stack_t second_thread_stack = {};
    std::thread t([&second_thread_stack] {
        start_monitoring();
        sigaltstack(nullptr, &second_thread_stack);
    });
    t.join();
    // then
    clear_tests();
            ASSERT(first_thread_stack.ss_sp != nullptr);
            ASSERT(second_thread_stack.ss_sp != nullptr);
            ASSERT(first_thread_stack.ss_sp != second_thread_stack.ss_sp);
            PASS();
}

TEST forwarding_first_will_report_a_native_crash_before_the_previous_handler(void) {
    // given
    install_fake_runtime_handler(true);
    fake_mono_reports_managed_code = false;
    start_monitoring();
    // when
    simulate_signal_at(SIGSEGV, address_in_libc());
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            PASS();
}

TEST forwarding_first_will_forward_a_fault_in_managed_code(void) {
    // given
    install_fake_runtime_handler(true);
    start_monitoring();
    // when
    simulate_signal_at(SIGSEGV, address_in_libc());
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT(installed);
            PASS();
}

TEST forwarding_first_will_report_a_fault_outside_managed_code_and_any_library(void) {
    // given
    install_fake_runtime_handler(true);
    fake_mono_reports_managed_code = false;
    start_monitoring();
    // when
    simulate_signal_at(SIGSEGV, 0);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            PASS();
}

TEST forwarding_first_will_report_an_instruction_fetch_fault_before_the_previous_handler(void) {
    // given
    install_fake_runtime_handler(true);
    start_monitoring();
    // when
    simulate_signal_at(SIGSEGV, 0x100, 0x100);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            PASS();
}

TEST forwarding_first_will_pass_a_sent_signal_on_after_reporting(void) {
    // given
    install_fake_runtime_handler(false);
    start_monitoring();
    // when
    simulate_signal_at(SIGSEGV, 0, fake_data_address, SI_USER);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(2, calls);
            ASSERT_FALSE(installed);
            PASS();
}

TEST forwarding_first_will_forward_a_floating_point_fault_at_its_instruction(void) {
    // given
    install_fake_runtime_handler(true);
    start_monitoring();
    // when
    simulate_signal_at(SIGFPE, 0x100, 0x100, FPE_INTDIV);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT(installed);
            PASS();
}

static bool reports_without_forwarding(int signum, uintptr_t ip, uintptr_t fault_address, int *calls) {
    start_monitoring();
    simulate_signal_at(signum, ip, fault_address);
    *calls = fake_runtime_handler_calls;
    return !handlers_installed;
}

TEST forwarding_first_will_report_a_fault_beyond_the_first_page(void) {
    // given
    install_fake_runtime_handler(true);
    int calls = 0;
    // when
    const bool reported = reports_without_forwarding(SIGSEGV, 0, 0x100000, &calls);
    // then
    clear_fake_runtime_tests();
            ASSERT(reported);
            ASSERT_EQ(1, calls);
            PASS();
}

TEST forwarding_first_will_report_a_floating_point_fault_outside_managed_code(void) {
    // given
    install_fake_runtime_handler(true);
    fake_mono_reports_managed_code = false;
    int calls = 0;
    // when
    const bool reported = reports_without_forwarding(SIGFPE, 0x100, 0x100, &calls);
    // then
    clear_fake_runtime_tests();
            ASSERT(reported);
            ASSERT_EQ(1, calls);
            PASS();
}

TEST forwarding_first_will_report_a_fault_without_a_current_domain(void) {
    // given
    install_fake_runtime_handler(true);
    fake_mono_has_domain = false;
    int calls = 0;
    // when
    const bool reported = reports_without_forwarding(SIGSEGV, 0, fake_data_address, &calls);
    // then
    clear_fake_runtime_tests();
            ASSERT(reported);
            ASSERT_EQ(1, calls);
            PASS();
}

TEST forwarding_first_will_not_apply_to_a_signal_whose_previous_handler_is_not_mono(void) {
    // given
    install_fake_runtime_handler(true);
    ignore_mono_detection_for(SIGFPE);
    int calls = 0;
    // when
    const bool reported = reports_without_forwarding(SIGFPE, 0x100, 0x100, &calls);
    // then
    clear_fake_runtime_tests();
            ASSERT(reported);
            ASSERT_EQ(1, calls);
            PASS();
}

TEST handling_a_fault_signal_will_uninstall_the_handlers_when_forwarding_first_is_disabled(void) {
    // given
    install_fake_runtime_handler(true);
    ignore_mono_detection(true);
    start_monitoring();
    // when
    simulate_signal(SIGSEGV);
    // then
    const int calls = fake_runtime_handler_calls;
    const bool installed = handlers_installed;
    clear_fake_runtime_tests();
            ASSERT_EQ(1, calls);
            ASSERT_FALSE(installed);
            PASS();
}


SUITE (signal_monitor) {
            RUN_TEST(calling_start_monitor_from_different_threads_will_only_install_once);
            RUN_TEST(calling_start_monitor_from_different_threads_will_not_corrupt_the_memory);
            RUN_TEST(calling_stop_monitoring_from_2_different_threads_will_only_uninstall_once);
            RUN_TEST(calling_start_monitoring_will_install_signal_handlers);
            RUN_TEST(calling_start_monitoring_more_times_in_a_row_will_only_install_once);
            RUN_TEST(calling_stop_monitoring_will_uninstall_the_handlers);
            RUN_TEST(calling_stop_monitoring_more_times_in_a_row_will_only_uninstall_once);
            RUN_TEST(forwarding_first_will_keep_the_handlers_when_the_previous_handler_recovers);
            RUN_TEST(forwarding_first_will_handle_the_crash_when_the_previous_handler_does_not_recover);
            RUN_TEST(forwarding_first_will_not_apply_to_non_fault_signals);
            RUN_TEST(handling_a_fault_signal_will_uninstall_the_handlers_when_forwarding_first_is_disabled);
            RUN_TEST(forwarding_first_will_report_a_native_crash_before_the_previous_handler);
            RUN_TEST(forwarding_first_will_forward_a_fault_in_managed_code);
            RUN_TEST(forwarding_first_will_report_a_fault_outside_managed_code_and_any_library);
            RUN_TEST(forwarding_first_will_report_an_instruction_fetch_fault_before_the_previous_handler);
            RUN_TEST(forwarding_first_will_pass_a_sent_signal_on_after_reporting);
            RUN_TEST(forwarding_first_will_forward_a_floating_point_fault_at_its_instruction);
            RUN_TEST(forwarding_first_will_report_a_fault_beyond_the_first_page);
            RUN_TEST(forwarding_first_will_report_a_floating_point_fault_outside_managed_code);
            RUN_TEST(forwarding_first_will_report_a_fault_without_a_current_domain);
            RUN_TEST(forwarding_first_will_not_apply_to_a_signal_whose_previous_handler_is_not_mono);
            RUN_TEST(calling_stop_monitoring_will_keep_the_signal_handling_memory_allocated);
            RUN_TEST(forwarding_first_will_install_the_handler_with_the_previous_handler_mask);
            RUN_TEST(installing_the_handlers_will_not_merge_the_previous_mask_when_forwarding_first_is_disabled);
            RUN_TEST(starting_monitoring_again_on_a_thread_will_reuse_its_signal_stack);
            RUN_TEST(starting_monitoring_from_another_thread_will_register_a_distinct_signal_stack);
            RUN_TEST(a_thread_exiting_will_release_its_signal_stack);
}

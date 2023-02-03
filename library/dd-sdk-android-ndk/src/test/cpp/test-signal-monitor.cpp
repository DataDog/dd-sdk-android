#include <csignal>
#include <thread>
#include <unistd.h>

#include "greatest/greatest.h"
#include "utils/signal-monitor.h"

extern bool handlers_installed;
extern struct sigaction *original_sigactions;


#ifndef NDEBUG
extern int performed_install_ops;
extern int performed_uninstall_ops;
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


SUITE (signal_monitor) {
            RUN_TEST(calling_start_monitor_from_different_threads_will_only_install_once);
            RUN_TEST(calling_start_monitor_from_different_threads_will_not_corrupt_the_memory);
            RUN_TEST(calling_stop_monitoring_from_2_different_threads_will_only_uninstall_once);
            RUN_TEST(calling_start_monitoring_will_install_signal_handlers);
            RUN_TEST(calling_start_monitoring_more_times_in_a_row_will_only_install_once);
            RUN_TEST(calling_stop_monitoring_will_uninstall_the_handlers);
            RUN_TEST(calling_stop_monitoring_more_times_in_a_row_will_only_uninstall_once);
}

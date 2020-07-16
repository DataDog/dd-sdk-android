#include <unistd.h>

#include "greatest/greatest.h"
#include "utils/signal-monitor.h"

extern bool handlers_installed;
#ifndef NDEBUG
extern int performed_install_ops;
extern int performed_uninstall_ops;
#endif

void clear_tests() {
#ifndef NDEBUG
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

TEST installs_signal_handlers(void) {
    install_signal_handlers();
    // give time for the install thread to finish
    sleep(5);
            ASSERT(handlers_installed);
    uninstall_signal_handlers();
    clear_tests();
            PASS();
}

TEST calling_install_more_times_in_a_row_will_only_install_once(void) {
    install_signal_handlers();
    install_signal_handlers();
    install_signal_handlers();
    // give time for the install thread to finish
    sleep(5);
            ASSERT(handlers_installed);
            ASSERT(performedInstall(1));
    uninstall_signal_handlers();
    clear_tests();
            PASS();
}


TEST uninstalls_signal_handlers(void) {
    // given
    install_signal_handlers();
    sleep(5);
    // when
    uninstall_signal_handlers();
            ASSERT_FALSE(handlers_installed);
    clear_tests();
            PASS();
}

TEST calling_uninstall_more_times_in_a_row_will_only_uninstall_once(void) {
    // given
    install_signal_handlers();
    sleep(5);
    // when
    uninstall_signal_handlers();
    uninstall_signal_handlers();
    uninstall_signal_handlers();
            ASSERT_FALSE(handlers_installed);
            ASSERT(performedUninstall(1));
    clear_tests();
            PASS();
}


SUITE (signal_monitor) {
            RUN_TEST(installs_signal_handlers);
            RUN_TEST(calling_install_more_times_in_a_row_will_only_install_once);
            RUN_TEST(uninstalls_signal_handlers);
            RUN_TEST(calling_uninstall_more_times_in_a_row_will_only_uninstall_once);
}

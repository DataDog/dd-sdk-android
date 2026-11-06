/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#ifndef SIGNAL_MONITOR_H
#define SIGNAL_MONITOR_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Monitor for fatal signals, updating the crash context when detected, the
 * serialize to disk and invoke the previously-installed handler
 * @return true if monitoring started successfully
 */
bool start_monitoring();

/**
 * Forwards SIGSEGV, SIGBUS and SIGFPE to the previously installed handler first. They are neither
 * reported nor uninstall the handlers if that handler recovered from them (e.g. Mono turning a
 * fault into a managed NullReferenceException).
 * Signal handlers are process-wide while SDK cores are not, so once enabled (the process hosts a
 * managed runtime), forwarding stays enabled.
 */
void enable_forward_fault_signals_first(void);

/**
 * Stop monitoring for fatal exceptions and reinstall previously-installed
 * handlers
 */
void stop_monitoring(void);

#ifdef __cplusplus
}
#endif
#endif

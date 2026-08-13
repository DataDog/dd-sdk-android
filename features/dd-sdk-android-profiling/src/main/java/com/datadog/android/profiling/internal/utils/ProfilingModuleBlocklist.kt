/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

/**
 * Version of the `com.google.android.profiling` system package known to be broken: profiling
 * requests succeed but the recorded traces are empty, so there is nothing to report.
 */
private const val EMPTY_TRACE_MODULE_VERSION_CODE = 370546200L

/**
 * Whether profiling must not be started at all because the device runs a broken version of the
 * profiling system package. Additional versions can be added to the check as they are found.
 */
internal fun isProfilingModuleVersionBlocked(versionCode: Long): Boolean {
    return versionCode == EMPTY_TRACE_MODULE_VERSION_CODE
}

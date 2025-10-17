/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.internal.data.SharedPreferencesStorage

private const val KEY_PROFILING_ENABLED = "dd_profiling_enabled"

internal fun setProfilingFlag(context: Context) {
    SharedPreferencesStorage(context).putBoolean(KEY_PROFILING_ENABLED, true)
}

internal fun isProfilingEnabled(context: Context): Boolean {
    return SharedPreferencesStorage(context).getBoolean(KEY_PROFILING_ENABLED, false)
}

internal fun removeProfilingFlag(context: Context) {
    SharedPreferencesStorage(context).remove(KEY_PROFILING_ENABLED)
}

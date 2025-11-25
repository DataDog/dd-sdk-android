/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.internal.data.SharedPreferencesStorage

private const val KEY_PROFILING_ENABLED = "dd_profiling_enabled"

internal object ProfilingStorage {

    private var sharedPreferencesStorage: SharedPreferencesStorage? = null

    internal fun setProfilingFlag(appContext: Context, sdkInstanceName: String) {
        getStorage(appContext).putString(KEY_PROFILING_ENABLED, sdkInstanceName)
    }

    internal fun getProfilingEnabledInstanceName(appContext: Context): String? {
        return getStorage(appContext).getString(KEY_PROFILING_ENABLED)
    }

    internal fun removeProfilingFlag(appContext: Context, sdkInstanceName: String) {
        val oldValue = getStorage(appContext).getString(KEY_PROFILING_ENABLED)
        if (oldValue == sdkInstanceName) {
            getStorage(appContext).remove(KEY_PROFILING_ENABLED)
        }
    }

    @Suppress("ReturnCount")
    private fun getStorage(context: Context): SharedPreferencesStorage {
        sharedPreferencesStorage?.let { return it }
        synchronized(this) {
            sharedPreferencesStorage?.let { return it }
            SharedPreferencesStorage(context).also {
                sharedPreferencesStorage = it
                return it
            }
        }
    }
}

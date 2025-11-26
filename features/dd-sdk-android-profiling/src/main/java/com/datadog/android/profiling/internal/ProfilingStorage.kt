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

    internal fun addProfilingFlag(appContext: Context, sdkInstanceName: String) {
        getStorage(appContext).apply {
            val oldValue = getStringSet(KEY_PROFILING_ENABLED, emptySet()).orEmpty()
            val newSet = oldValue + sdkInstanceName
            putStringSet(KEY_PROFILING_ENABLED, newSet)
        }
    }

    internal fun getProfilingEnabledInstanceNames(appContext: Context): Set<String> {
        return getStorage(appContext).getStringSet(KEY_PROFILING_ENABLED) ?: emptySet()
    }

    internal fun removeProfilingFlag(appContext: Context, sdkInstanceNames: Set<String>) {
        getStorage(appContext).apply {
            val oldValue = getStringSet(KEY_PROFILING_ENABLED) ?: emptySet()
            val newSet = mutableSetOf<String>()
            newSet.addAll(oldValue)
            val removed = newSet.removeAll(sdkInstanceNames)
            if (removed) {
                putStringSet(KEY_PROFILING_ENABLED, newSet)
            }
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

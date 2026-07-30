/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.internal.data.SharedPreferencesStorage

internal object ProfilingStorage {

    internal const val KEY_PROFILING_ENABLED = "dd_profiling_enabled"
    internal const val KEY_PROFILING_SAMPLE_RATE = "dd_profiling_sample_rate"

    @Volatile
    internal var sharedPreferencesStorage: SharedPreferencesStorage? = null

    internal fun setSampleRate(appContext: Context, sampleRate: Float) {
        getStorage(appContext).putFloat(KEY_PROFILING_SAMPLE_RATE, sampleRate)
    }

    internal fun getSampleRate(appContext: Context): Float {
        return getStorage(appContext).getFloat(KEY_PROFILING_SAMPLE_RATE, -1f)
    }

    internal fun removeSampleRate(appContext: Context) {
        return getStorage(appContext).remove(KEY_PROFILING_SAMPLE_RATE)
    }

    @JvmStatic
    internal fun addProfilingFlag(appContext: Context) {
        getStorage(appContext).putBoolean(KEY_PROFILING_ENABLED, true)
    }

    @JvmStatic
    internal fun isProfilingEnabled(appContext: Context): Boolean {
        return getStorage(appContext).getBoolean(KEY_PROFILING_ENABLED, false)
    }

    @JvmStatic
    internal fun removeProfilingFlag(appContext: Context) {
        getStorage(appContext).remove(KEY_PROFILING_ENABLED)
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

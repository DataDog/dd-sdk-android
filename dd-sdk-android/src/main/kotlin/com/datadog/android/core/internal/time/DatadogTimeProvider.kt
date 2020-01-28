/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.content.Context
import android.content.SharedPreferences
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Suppress("DEPRECATION")
internal class DatadogTimeProvider(
    context: Context
) : MutableTimeProvider {

    private val contextRef = WeakReference(context.applicationContext)
    private var serverOffset = 0L
    private var samples: Int = 0

    init {
        val restoredOffset = getPreferences()?.getLong(PREF_OFFSET_MS, 0) ?: 0L

        if (restoredOffset != 0L) {
            serverOffset = restoredOffset
            samples = 1
        }
    }

    // region MutableTimeProvider

    override fun updateOffset(offsetMs: Long) {
        val currentOffsetMs = serverOffset

        if (samples == 0) {
            serverOffset = offsetMs
            samples = 1
        } else if (abs(currentOffsetMs - offsetMs) > MAX_OFFSET_DEVIATION) {
            serverOffset = offsetMs
            samples = 1
        } else {
            val newAverageMs = ((currentOffsetMs * samples) + offsetMs) / (samples + 1)
            serverOffset = newAverageMs
            if (samples < MAX_SAMPLES) {
                samples++
            }
        }

        val prefs = getPreferences()
        if (prefs != null) {
            prefs.edit()
                .putLong(PREF_OFFSET_MS, serverOffset)
                .apply()
        }
    }

    // endregion

    // region TimeProvider

    override fun getDeviceTimestamp(): Long {
        return System.currentTimeMillis()
    }

    override fun getServerTimestamp(): Long {
        return System.currentTimeMillis() + serverOffset
    }

    override fun getServerOffsetNanos(): Long {
        return TimeUnit.MILLISECONDS.toNanos(serverOffset)
    }

    // endregion

    // region Internal

    private fun getPreferences(): SharedPreferences? {
        val context = contextRef.get()
        return context?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    // endregion

    companion object {
        internal const val PREF_OFFSET_MS = "server_timestamp_offset_ms"
        internal const val PREFERENCES_NAME = "datadog"

        private const val TAG = "DatadogTimeProvider"
        private const val MAX_SAMPLES = 64

        // The Max allowed deviation, accounting for long transport in bad network conditions
        internal const val MAX_OFFSET_DEVIATION: Long = 60L * 1000L
    }
}

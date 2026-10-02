/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import android.content.Context
import com.datadog.android.api.InternalLogger

internal interface ProfilingTriggerRegistrar {

    var internalLogger: InternalLogger?

    /**
     * Sets which trigger types (ANR, out of memory, memory anomaly) should be registered.
     * Trigger types not supported by the device API level are ignored.
     */
    fun setEnabledTriggers(
        anrTriggerEnabled: Boolean,
        oomTriggerEnabled: Boolean,
        anomalyTriggerEnabled: Boolean
    )

    fun register(appContext: Context, listener: ProfilingTriggerListener)

    fun unregister(appContext: Context)
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration

/**
 * Applies a [RemoteConfiguration] to this [ProfilingConfiguration], returning a new instance
 * with the remote values overlaid on top of the developer-supplied configuration.
 *
 * Fields absent from the remote payload (null) are left unchanged. Only the `profiling`
 * section of the remote configuration is consumed here; other sections (rum, sessionReplay, trace)
 * are handled by their respective feature modules.
 */
@ExperimentalProfilingApi
internal fun ProfilingConfiguration.applyRemoteConfiguration(
    rc: RemoteConfiguration?
): ProfilingConfiguration {
    val profiling = rc?.profiling ?: return this
    return copy(
        applicationLaunchSampleRate = profiling.applicationLaunchSampleRate?.toFloat()
            ?: applicationLaunchSampleRate,
        continuousSampleRate = profiling.continuousSampleRate?.toFloat()
            ?: continuousSampleRate
    )
}

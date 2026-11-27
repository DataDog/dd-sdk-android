/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.profiling.quota.QuotaReason

/**
 * State the profiling feature shares with other features through its feature context.
 */
internal data class ProfilingContextState(
    val continuousSampleRate: Float,
    val applicationLaunchSampleRate: Float,
    val isAnrEnabled: Boolean,
    val isProfilerRunning: Boolean = false,
    val isOomTriggerRegistered: Boolean = false,
    val quotaDeniedReason: QuotaReason? = null,
    val quotaDeniedSessionId: String? = null
) {

    fun writeTo(context: MutableMap<String, Any?>) {
        context[FeatureContextKeys.PROFILING_SAMPLE_RATE] = continuousSampleRate
        context[FeatureContextKeys.PROFILING_APPLICATION_LAUNCH_SAMPLE_RATE] = applicationLaunchSampleRate
        context[FeatureContextKeys.PROFILING_ANR_ENABLED] = isAnrEnabled
        context[FeatureContextKeys.PROFILER_IS_RUNNING] = isProfilerRunning
        context[FeatureContextKeys.PROFILING_OOM_TRIGGER_REGISTERED] = isOomTriggerRegistered
        if (quotaDeniedReason != null && quotaDeniedSessionId != null) {
            context[FeatureContextKeys.PROFILING_QUOTA_REASON] = quotaDeniedReason
            context[FeatureContextKeys.PROFILING_QUOTA_SESSION_ID] = quotaDeniedSessionId
        } else {
            context.remove(FeatureContextKeys.PROFILING_QUOTA_REASON)
            context.remove(FeatureContextKeys.PROFILING_QUOTA_SESSION_ID)
        }
    }
}

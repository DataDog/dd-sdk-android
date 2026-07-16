/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.profiling

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys

/**
 * Whether the profiler is currently running, as published by the profiling feature.
 */
internal fun DatadogContext.isProfilerRunning(): Boolean =
    featuresContext[Feature.PROFILING_FEATURE_NAME]
        ?.get(FeatureContextKeys.PROFILER_IS_RUNNING) == true

/**
 * Returns the profiling quota-denied reason only when it applies to [currentSessionId].
 *
 * The profiling feature stamps each quota decision with the session id it was made for. A reason
 * left over from a previous session — e.g. published before the current session's quota check has
 * resolved — has a mismatching session id and is treated as absent.
 */
@Suppress("ReturnCount")
internal fun DatadogContext.resolveProfilingQuotaReason(currentSessionId: String?): String? {
    val profilingContext = featuresContext[Feature.PROFILING_FEATURE_NAME] ?: return null
    val reason = profilingContext[FeatureContextKeys.PROFILING_QUOTA_REASON] as? String ?: return null
    val quotaSessionId = profilingContext[FeatureContextKeys.PROFILING_QUOTA_SESSION_ID] as? String
    return reason.takeIf { quotaSessionId != null && quotaSessionId == currentSessionId }
}

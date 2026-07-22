/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.fixtures

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.ProfilingFeature

/**
 * Test handle around an internal [ProfilingFeature] for use from integration tests in
 * other modules. Holds a [NoOpProfiler] so no real Perfetto profiling is started, while
 * the surrounding cross-feature wiring (event receiver, context-update receiver,
 * scheduler bootstrap) runs exactly as in production.
 */
@OptIn(ExperimentalProfilingApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProfilingFeatureTestHandle internal constructor(
    private val feature: ProfilingFeature
) {

    val asFeature: Feature get() = feature

    val lastSeenRumSessionId: String? get() = feature.lastSeenRumSessionId

    val schedulerSessionId: String? get() = feature.continuousProfilingScheduler?.currentSessionId

    val isSchedulerSessionSampled: Boolean
        get() = feature.continuousProfilingScheduler?.currentSessionSampled ?: false

    companion object {

        /**
         * Build a [ProfilingFeatureTestHandle] backed by a [NoOpProfiler] and register the
         * feature with [sdkCore], mirroring the production `Profiling.enable` entry point.
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun create(
            sdkCore: FeatureSdkCore,
            configuration: ProfilingConfiguration = ProfilingConfiguration.DEFAULT
        ): ProfilingFeatureTestHandle {
            val feature = ProfilingFeature(
                sdkCore = sdkCore,
                configuration = configuration,
                profiler = NoOpProfiler()
            )
            sdkCore.registerFeature(feature)
            return ProfilingFeatureTestHandle(feature)
        }
    }
}

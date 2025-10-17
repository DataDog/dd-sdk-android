/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.removeProfilingFlag
import java.util.concurrent.Executors

/**
 * An entry point to Datadog Profiling feature.
 */
object Profiling {

    private var profiler: Profiler = NoOpProfiler()

    /**
     * Enables the Perfetto based profiler to start recording callstack samples during application
     * launch.
     *
     * @param configuration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmStatic
    @JvmOverloads
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun enable(
        configuration: ProfilingConfiguration = ProfilingConfiguration.DEFAULT,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        val profilingFeature = ProfilingFeature(
            sdkCore = featureSdkCore,
            configuration = configuration,
            profiler = profiler
        )
        featureSdkCore.registerFeature(profilingFeature)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun start(context: Context) {
        profiler = PerfettoProfiler(
            timeProvider = DefaultTimeProvider(),
            profilingExecutor = Executors.newSingleThreadExecutor()
        ).apply {
            this.start(context)
        }
        removeProfilingFlag(context)
    }
}

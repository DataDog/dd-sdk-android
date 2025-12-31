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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An entry point to Datadog Profiling feature.
 */
object Profiling {

    internal var profiler: Profiler = NoOpProfiler()
    internal val isProfilerInitialized = AtomicBoolean(false)

    /**
     * Enables the profiling feature.
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
        initializeProfiler()
        val profilingFeature = ProfilingFeature(
            sdkCore = featureSdkCore,
            configuration = configuration,
            profiler = profiler
        )
        featureSdkCore.registerFeature(profilingFeature)
    }

    /**
     * Decides if the next app launch should be profiled.
     *
     * @param sdkCore SDK instance to register feature in.
     * @param enable enables the profiling for next app launch, otherwise disables it.
     */
    @JvmStatic
    @JvmOverloads
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun profileNextAppStartup(
        sdkCore: SdkCore = Datadog.getInstance(),
        enable: Boolean
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        val profilingFeature = featureSdkCore
            .getFeature(Feature.PROFILING_FEATURE_NAME)?.let {
                it.unwrap() as? ProfilingFeature
            }
        profilingFeature?.profileNextAppStartup(enable) ?: run {
            featureSdkCore.internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.USER,
                messageBuilder = { "Profiling feature needs to be enabled before calling this method." }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun start(context: Context, sdkInstanceNames: Set<String>) {
        initializeProfiler()
        profiler.start(context, sdkInstanceNames)
        ProfilingStorage.removeProfilingFlag(context, sdkInstanceNames)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun initializeProfiler() {
        if (!isProfilerInitialized.getAndSet(true)) {
            profiler = PerfettoProfiler(
                timeProvider = DefaultTimeProvider(),
                profilingExecutor = Executors.newSingleThreadExecutor()
            )
        }
    }
}

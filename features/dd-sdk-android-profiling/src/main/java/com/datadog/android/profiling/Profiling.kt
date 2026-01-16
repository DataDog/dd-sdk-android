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
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An entry point to Datadog Profiling feature.
 */
object Profiling {

    @Volatile
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
     * Start profiling with given SDK instances names.
     *
     * @param context application context
     * @param sdkInstanceNames the set of the SDK instances name
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun start(context: Context, sdkInstanceNames: Set<String>) {
        initializeProfiler()
        profiler.start(context, sdkInstanceNames)
        ProfilingStorage.removeProfilingFlag(context, sdkInstanceNames)
    }

    /**
     * Start profiling for a given SDK instance.
     *
     * @param context application context
     * @param sdkCore SDK instance to start profiling with. If not provided, default SDK instance.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun start(context: Context, sdkCore: SdkCore = Datadog.getInstance()) {
        start(context, setOf(sdkCore.name))
    }

    /**
     * Stop profiling for a given SDK instance.
     *
     * @param sdkCore SDK instance to stop profiling. If not provided, default SDK instance.
     */
    fun stop(sdkCore: SdkCore = Datadog.getInstance()) {
        profiler.stop(sdkCore.name)
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

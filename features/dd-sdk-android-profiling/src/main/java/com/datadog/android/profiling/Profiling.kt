/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An entry point to Datadog Profiling feature.
 */
@ExperimentalProfilingApi
object Profiling {

    @Volatile
    internal var profiler: Profiler = NoOpProfiler()
    internal val isProfilerInitialized = AtomicBoolean(false)

    @VisibleForTesting
    internal var currentRegisteredCore: WeakReference<SdkCore>? = null

    internal const val IS_ALREADY_REGISTERED_WARNING =
        "Profiling is already enabled and does not support multiple instances. " +
            "The existing instance will continue to be used."

    internal const val IS_ALREADY_REGISTERED_USER_WARNING =
        "Profiling is already enabled and does not support multiple instances. " +
            "The existing instance (registered with SDK core \"%s\") will continue to be used."

    /**
     * Enables the profiling feature.
     *
     * Profiling supports a single SDK instance. If profiling is already enabled on another active
     * SDK instance, this call is ignored and a warning is logged.
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
        // Serialize the check-and-set so two concurrent enable() calls with different cores cannot
        // both pass the guard and register competing features against the single shared profiler.
        synchronized(this) {
            if (isAlreadyRegistered()) {
                logAlreadyRegisteredWarning(featureSdkCore.internalLogger)
                return
            }
            initializeProfiler()
            val profilingFeature = ProfilingFeature(
                sdkCore = featureSdkCore,
                configuration = configuration,
                profiler = profiler
            )
            currentRegisteredCore = WeakReference(sdkCore)
            featureSdkCore.registerFeature(profilingFeature)
        }
    }

    /**
     * Start profiling.
     *
     * @param context application context
     * @param startReason reason to start a profiling session
     * @param additionalAttributes additional attributes to include in the profiling telemetry
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun start(
        context: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>
    ) {
        initializeProfiler()
        profiler.start(context, startReason, additionalAttributes)
        ProfilingStorage.removeProfilingFlag(context)
    }

    /**
     * Stop profiling.
     */
    internal fun stop() {
        profiler.stop()
    }

    private fun isAlreadyRegistered() =
        currentRegisteredCore?.get()?.isCoreActive() == true

    private fun logAlreadyRegisteredWarning(internalLogger: InternalLogger) {
        val registeredInstanceName = currentRegisteredCore?.get()?.name
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER),
            messageBuilder = {
                IS_ALREADY_REGISTERED_USER_WARNING.format(Locale.US, registeredInstanceName)
            }
        )

        internalLogger.log(
            level = InternalLogger.Level.DEBUG,
            targets = listOf(InternalLogger.Target.TELEMETRY),
            messageBuilder = { IS_ALREADY_REGISTERED_WARNING }
        )
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun initializeProfiler() {
        if (!isProfilerInitialized.getAndSet(true)) {
            profiler = PerfettoProfiler(
                timeProvider = MutableTimeProvider.create(DefaultTimeProvider()),
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
            )
        }
    }
}

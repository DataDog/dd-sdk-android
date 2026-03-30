/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.internal.profiling.ProfilerStopEvent
import com.datadog.android.internal.profiling.TTIDRumContext
import com.datadog.android.internal.rum.RumSessionRenewedEvent
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalProfilingApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    private val configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver, ProfilerCallback {

    private var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    @Volatile
    private var ttidRumContext: TTIDRumContext? = null

    // True once ProfilerStopEvent.TTID has been received, regardless of whether the RUM session
    // was sampled (i.e. ttidRumContext may still be null when this is true).
    @Volatile
    private var ttidEventReceived = false

    @Volatile
    private var perfettoResult: PerfettoResult? = null

    private val isTtidProfileSent: AtomicBoolean = AtomicBoolean(false)

    private lateinit var appContext: Context

    @Volatile
    private var continuousProfilingScheduler: ContinuousProfilingScheduler? = null

    override val requestFactory: RequestFactory = ProfilingRequestFactory(
        configuration.customEndpointUrl
    )

    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.DEFAULT

    override val name: String
        get() = Feature.PROFILING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        this.appContext = appContext
        profiler.apply {
            this.internalLogger = sdkCore.internalLogger
            registerProfilingCallback(sdkCore.name, this@ProfilingFeature)
        }
        setMinimumSampleRate(appContext, configuration.applicationLaunchSampleRate)
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        ProfilingStorage.addProfilingFlag(appContext, sdkCore.name)
        sdkCore.setEventReceiver(name, this)
        // TODO RUM-13678: we need to update context from the actual profiler start call, not from here
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
        }
        dataWriter = createDataWriter(sdkCore)

        val scheduler = ContinuousProfilingScheduler(
            appContext = appContext,
            profiler = profiler,
            sdkCore = sdkCore,
            sampleRate = configuration.continuousSampleRate
        )
        continuousProfilingScheduler = scheduler
        scheduler.start(launchProfilingActive = profiler.isRunning(sdkCore.name))
    }

    override fun onStop() {
        continuousProfilingScheduler?.stop()
        profiler.apply {
            stop(sdkCore.name)
            unregisterProfilingCallback(sdkCore.name)
            scheduledExecutorService.shutdown()
        }
        sdkCore.removeEventReceiver(name)
    }

    override fun onReceive(event: Any) {
        when (event) {
            is ProfilerStopEvent.TTID -> onTtidEvent(event)
            is RumSessionRenewedEvent -> onRumSessionRenewed(event)
            else -> sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
        }
    }

    override fun onSuccess(result: PerfettoResult) {
        perfettoResult = result
        tryWriteProfilingEvent()
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
        }
    }

    override fun onFailure(tag: String) {
        if (tag == ProfilingStartReason.APPLICATION_LAUNCH.value) {
            // Launch profiling ended with error such as rate limiting error.
            // Unblock the continuous scheduler so it doesn't wait forever.
            continuousProfilingScheduler?.onAppLaunchProfilingComplete()
        }
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
        }
    }

    private fun onTtidEvent(event: ProfilerStopEvent.TTID) {
        if (ttidEventReceived) return // already handled

        ttidEventReceived = true
        ttidRumContext = event.rumContext

        if (continuousProfilingScheduler?.isScheduling != true) {
            // Non-continuous: stop profiler immediately at TTID.
            profiler.stop(sdkCore.name)
            tryWriteProfilingEvent()
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { "Profiling stopped with TTID reason" }
            )
        }
    }

    private fun onRumSessionRenewed(event: RumSessionRenewedEvent) {
        continuousProfilingScheduler?.onRumSessionRenewed(event.sessionSampled)
    }

    private fun setMinimumSampleRate(appContext: Context, sampleRate: Float) {
        val oldValue = ProfilingStorage.getSampleRate(appContext)
        // if old value doesn't exist (we use negative default value in case of absence) or
        // the value is bigger than the sample rate, we update the sample rate.
        if (oldValue !in 0f..sampleRate) {
            ProfilingStorage.setSampleRate(appContext, configuration.applicationLaunchSampleRate)
        }
    }

    @Suppress("ReturnCount")
    private fun tryWriteProfilingEvent() {
        val result = perfettoResult ?: return
        when (result.tag) {
            ProfilingStartReason.APPLICATION_LAUNCH.value -> {
                // Wait until the TTID event has been received before proceeding — both the
                // profiler result and the TTID event are needed. Note: ttidRumContext may be
                // null even after the event arrives (unsampled RUM session), which is why we
                // track receipt separately via ttidEventReceived.
                if (!ttidEventReceived) return
                if (!isTtidProfileSent.getAndSet(true)) {
                    val ttidRumContext = ttidRumContext
                    if (ttidRumContext != null) {
                        dataWriter.write(profilingResult = result, ttidRumContext = ttidRumContext)
                    }
                    continuousProfilingScheduler?.onAppLaunchProfilingComplete()
                }
            }

            ProfilingStartReason.CONTINUOUS.value -> {
                // TODO RUM-15193: write continuous profiling event
            }
        }
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): ProfilingDataWriter {
        return ProfilingDataWriter(sdkCore)
    }

    companion object {
        private const val UNSUPPORTED_EVENT_TYPE =
            "Profiling feature received an event of unsupported type=%s."
        private const val PROFILER_IS_RUNNING = "profiler_is_running"
    }
}

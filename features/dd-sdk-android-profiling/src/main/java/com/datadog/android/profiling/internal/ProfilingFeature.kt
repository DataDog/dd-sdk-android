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
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.rum.TTIDEvent
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver {

    private var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    @Volatile
    private var ttidEvent: TTIDEvent? = null

    @Volatile
    private var perfettoResult: PerfettoResult? = null

    private val isTtidProfileSent: AtomicBoolean = AtomicBoolean(false)

    private lateinit var appContext: Context

    override val requestFactory: RequestFactory = ProfilingRequestFactory(
        configuration.customEndpointUrl
    )

    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.Companion.DEFAULT

    override val name: String
        get() = Feature.PROFILING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        this.appContext = appContext
        profiler.apply {
            this.internalLogger = sdkCore.internalLogger
            registerProfilingCallback(sdkCore.name) { result ->
                perfettoResult = result
                tryWriteProfilingEvent()
                // if profiler stopped before TTID event, still update the status: in such case TTID profiling
                // is incomplete
                sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
                    context[PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
                }
            }
        }
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        ProfilingStorage.addProfilingFlag(appContext, sdkCore.name)
        sdkCore.setEventReceiver(name, this)
        // TODO RUM-13678: we need to update context from the actual profiler start call, not from here
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
        }
        dataWriter = createDataWriter(sdkCore)
    }

    override fun onStop() {
        profiler.apply {
            stop(sdkCore.name)
            unregisterProfilingCallback(sdkCore.name)
        }
        sdkCore.removeEventReceiver(name)
    }

    override fun onReceive(event: Any) {
        if (event !is TTIDEvent) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
            return
        }
        this.ttidEvent = event
        profiler.stop(sdkCore.name)
        tryWriteProfilingEvent()
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { "Profiling stopped with TTID=${event.durationNs}" }
        )
    }

    private fun tryWriteProfilingEvent() {
        val perfettoResult = perfettoResult ?: return
        val ttidEvent = ttidEvent ?: return
        if (!isTtidProfileSent.getAndSet(true)) {
            dataWriter.write(
                profilingResult = perfettoResult,
                ttidEvent = ttidEvent
            )
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

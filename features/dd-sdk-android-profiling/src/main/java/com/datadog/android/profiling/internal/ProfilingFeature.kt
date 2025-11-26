/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import android.os.ProfilingResult
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

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver {

    private var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    private var ttidEvent: TTIDEvent? = null

    private var profilingResult: PerfettoResult? = null

    override val requestFactory: RequestFactory = ProfilingRequestFactory(
        configuration.customEndpointUrl,
        sdkCore.internalLogger
    )

    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.Companion.DEFAULT

    override val name: String
        get() = Feature.Companion.PROFILING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        profiler.apply {
            this.internalLogger = sdkCore.internalLogger
            registerProfilingCallback(sdkCore.name) { result ->
                profilingResult = result
                writeProfilingIfNeeded()
            }
        }
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        ProfilingStorage.addProfilingFlag(appContext, sdkCore.name)
        sdkCore.setEventReceiver(name, this)
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context.put(PROFILER_IS_RUNNING, profiler.isRunning(sdkCore.name))
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

        writeProfilingIfNeeded()

        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { "Profiling stopped with TTID=${event.durationNs}" }
        )
    }

    private fun writeProfilingIfNeeded() {
        val profilingResult = profilingResult ?: return
        val ttidEvent = ttidEvent ?: return

        dataWriter.write(
            profilingResult = profilingResult,
            ttidEvent = ttidEvent
        )
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): ProfilingDataWriter {
        return ProfilingDataWriter(sdkCore)
    }

    companion object {
        private const val UNSUPPORTED_EVENT_TYPE =
            "Profiling feature receive an event of unsupported type=%s."
        private const val PROFILER_IS_RUNNING = "profiler_is_running"
    }
}

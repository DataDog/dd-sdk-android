/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.internal.profiling.LongTaskRumContext
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.TTIDRumContext
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.BUFFER_SIZE_KB
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.PROFILING_SAMPLING_RATE
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.PROFILING_TAG_APPLICATION_LAUNCH
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    private val configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver {

    private var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    @Volatile
    private var ttidRumContext: TTIDRumContext? = null

    @Volatile
    private var perfettoResult: PerfettoResult? = null

    private val isTtidProfileSent: AtomicBoolean = AtomicBoolean(false)

    private lateinit var appContext: Context

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
        setMinimumSampleRate(appContext, configuration.sampleRate)
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
        if (event is ProfilerEvent.TTIDStop) {
            if (ttidRumContext == null) {
                ttidRumContext = event.rumContext
                profiler.stop(sdkCore.name)
                tryWriteProfilingEvent()
                sdkCore.internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Profiling stopped with TTID reason" }
                )
            }
        } else if (event is ProfilerEvent.AddLongTask) {
            anrLongTaskRumContexts += event.longTaskRumContext
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
        }
    }

    // region ANR profiling

    private var anrProfilingStopSignal: CancellationSignal? = null
    private val anrLongTaskRumContexts = CopyOnWriteArrayList<LongTaskRumContext>()

    fun startAnrProfiling() {
        anrProfilingStopSignal = CancellationSignal().apply {
            requestProfiling(
                appContext,
                StackSamplingRequestBuilder()
                    .setCancellationSignal(this)
                    .setTag("ANR/${System.currentTimeMillis()}")
                    .setSamplingFrequencyHz(PROFILING_SAMPLING_RATE)
                    .setBufferSizeKb(BUFFER_SIZE_KB)
                    .build(),
                Executors.newSingleThreadExecutor()
            ) {
                val anrProfilingResult = PerfettoResult(
                    start = it.tag.orEmpty().substringAfter("/").toLong(),
                    end = System.currentTimeMillis(),
                    tag = it.tag.orEmpty(),
                    resultFilePath = it.resultFilePath.orEmpty()
                )
                dataWriter.write(anrProfilingResult, anrLongTaskRumContexts)
            }
        }
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = true
        }
    }

    fun stopAnrProfiling() {
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[PROFILER_IS_RUNNING] = false
        }
        anrProfilingStopSignal?.cancel()
        anrProfilingStopSignal = null
    }

    // endregion

    private fun setMinimumSampleRate(appContext: Context, sampleRate: Float) {
        val oldValue = ProfilingStorage.getSampleRate(appContext)
        // if old value doesn't exist (we use negative default value in case of absence) or
        // the value is bigger than the sample rate, we update the sample rate.
        if (oldValue !in 0f..sampleRate) {
            ProfilingStorage.setSampleRate(appContext, configuration.sampleRate)
        }
    }

    @Suppress("ReturnCount")
    private fun tryWriteProfilingEvent() {
        val perfettoResult = perfettoResult ?: return
        val ttidRumContext = ttidRumContext ?: return
        if (perfettoResult.tag != PerfettoProfiler.PROFILING_TAG_APPLICATION_LAUNCH) return
        if (!isTtidProfileSent.getAndSet(true)) {
            dataWriter.write(
                profilingResult = perfettoResult,
                ttidRumContext = ttidRumContext
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

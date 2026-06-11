/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.core.os.ProfilingRequest
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.anr.AnrListener
import com.datadog.android.profiling.internal.anr.AnrProfilingTriggerRegistrar
import com.datadog.android.profiling.internal.anr.AnrTriggerRegistrar
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetryEvent
import com.datadog.android.profiling.internal.utils.fileSizeSafe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.random.Random

/**
 * Profiler based on Android's [requestProfiling] API to record callstack samples.
 *
 * Supports multiple start reasons including application launch, RUM operations, and continuous profiling.
 *
 * @param timeProvider The time provider to use to get the current time.
 * @param scheduledExecutorService the executor service to run the profiling task on.
 * @param profilingTelemetry shared telemetry helper that buffers metric events until a logger is
 * available and dispatches them through the unified `[Mobile Metric] Profiling Session` envelope.
 * @param anrTriggerRegistrar registrar that owns the system ANR profiling-trigger lifecycle.
 * The profiler passes its internal fan-out listener to it at register time; the listener
 * captures the profiler's `callbackMap` so all SDK instances receive the detection.
 * @param buildSdkVersionProvider Build.VERSION.SDK_INT provider used for the test.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class PerfettoProfiler(
    private val timeProvider: TimeProvider,
    override val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    internal val profilingTelemetry: ProfilingTelemetry = ProfilingTelemetry(),
    internal val anrTriggerRegistrar: AnrTriggerRegistrar =
        AnrProfilingTriggerRegistrar(timeProvider, scheduledExecutorService, profilingTelemetry),
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : Profiler {

    internal var stopSignal: CancellationSignal? = null
    private val resultCallback: Consumer<ProfilingResult>

    // This flag represents which instance of this class is working for.
    private val runningInstances: AtomicReference<Set<String>> = AtomicReference(emptySet())

    @Volatile
    private var profilingStartTime = 0L

    @Volatile
    private var profilingStopTime = 0L

    @Volatile
    private var profilingStartReason: ProfilingStartReason = ProfilingStartReason.UNKNOWN

    @Volatile
    private var profilingAppStartInfo: String? = null

    @Volatile
    private var profilingSamplingRateHz: Int = PROFILING_SAMPLING_RATE_APP_LAUNCH

    @Volatile
    override var internalLogger: InternalLogger? = null
        set(value) {
            field = value
            anrTriggerRegistrar.internalLogger = value
            profilingTelemetry.internalLogger = value
        }

    internal val anrListener = AnrListener { event ->
        callbackMap.values.forEach { callback ->
            callback.onAnrDetected(event)
        }
    }

    @Volatile
    private var extendLaunchSession = false

    // Map of <InstanceName, ProfilerCallback>
    private val callbackMap: MutableMap<String, ProfilerCallback> = ConcurrentHashMap()

    init {
        resultCallback = Consumer<ProfilingResult> { result ->
            val resultCallbackTime = timeProvider.getDeviceTimestampMillis()
            // profilingStopTime is 0L when profiling ended by timeout (stop() was never called).
            // In that case, fall back to resultCallbackTime so duration is still meaningful.
            val effectiveStopTime =
                if (profilingStopTime > 0L) profilingStopTime else resultCallbackTime
            val duration = effectiveStopTime - profilingStartTime
            val resultCallbackDelayMs =
                if (profilingStopTime > 0L) resultCallbackTime - profilingStopTime else 0L
            val startReason = ProfilingStartReason.values().firstOrNull { it.value == result.tag.orEmpty() }
                ?: ProfilingStartReason.UNKNOWN
            if (result.errorCode == ProfilingResult.ERROR_NONE) {
                // TODO RUM-13679: need to delete the file after it is no longer needed
                result.resultFilePath?.let {
                    notifyCallbacks {
                        onSuccess(
                            PerfettoResult(
                                start = profilingStartTime,
                                startReason = startReason,
                                end = resultCallbackTime,
                                resultFilePath = it
                            )
                        )
                    }
                } ?: notifyCallbacks { onFailure(startReason) }
            } else {
                notifyCallbacks { onFailure(startReason) }
            }
            runningInstances.set(emptySet())
            profilingTelemetry.report(
                ProfilingTelemetryEvent.SessionEnd(
                    startReason = profilingStartReason.value,
                    appStartInfo = profilingAppStartInfo,
                    errorCode = result.errorCode,
                    errorMessage = result.errorMessage,
                    fileSize = fileSizeSafe(result.resultFilePath, internalLogger),
                    durationMs = duration,
                    resultCallbackDelayMs = resultCallbackDelayMs,
                    stopReason = resolveStopReason(result.errorCode),
                    bufferSizeKb = BUFFER_SIZE_KB,
                    samplingFrequencyHz = profilingSamplingRateHz
                )
            )
        }
    }

    private fun buildStackSamplingRequest(
        startReason: ProfilingStartReason,
        durationMs: Int
    ): ProfilingRequest {
        val samplingRateHz = getSamplingRateHz(startReason)
        profilingSamplingRateHz = samplingRateHz
        return CancellationSignal().let {
            this.stopSignal = it
            StackSamplingRequestBuilder()
                .setCancellationSignal(it)
                .setTag(startReason.value)
                .setSamplingFrequencyHz(samplingRateHz)
                .setBufferSizeKb(BUFFER_SIZE_KB)
                .setDurationMs(durationMs)
                .build()
        }
    }

    private fun getSamplingRateHz(startReason: ProfilingStartReason): Int {
        return if (startReason == ProfilingStartReason.APPLICATION_LAUNCH) {
            PROFILING_SAMPLING_RATE_APP_LAUNCH
        } else {
            PROFILING_SAMPLING_RATE_CONTINUOUS
        }
    }

    private fun notifyCallbacks(dispatch: ProfilerCallback.() -> Unit) {
        val running = runningInstances.get()
        callbackMap.forEach { (key, callback) ->
            if (running.contains(key)) callback.dispatch()
        }
    }

    override fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        sdkInstanceNames: Set<String>,
        durationMs: Int
    ) {
        val effectiveDurationMs =
            if (durationMs > 0) durationMs else getDefaultDurationMs(startReason)
        // profiling will be launched when no instance is currently running profiling.
        if (runningInstances.compareAndSet(emptySet(), sdkInstanceNames)) {
            profilingStartTime = timeProvider.getDeviceTimestampMillis()
            profilingStopTime = 0L
            profilingStartReason = startReason
            profilingAppStartInfo = additionalAttributes[ProfilingTelemetry.KEY_APP_START_INFO]
            requestProfiling(
                appContext,
                buildStackSamplingRequest(startReason, effectiveDurationMs),
                scheduledExecutorService,
                resultCallback
            )
            if (startReason == ProfilingStartReason.APPLICATION_LAUNCH) {
                scheduledExecutorService.scheduleSafe(
                    operationName = OPERATION_NAME_APP_LAUNCH_PROFILING_SCHEDULE,
                    delay = APP_LAUNCH_PROFILING_MAX_DURATION_MS,
                    unit = TimeUnit.MILLISECONDS,
                    internalLogger = internalLogger ?: InternalLogger.UNBOUND,
                    runnable = {
                        stopSignal?.let { signal ->
                            if (profilingStartReason == ProfilingStartReason.APPLICATION_LAUNCH &&
                                !signal.isCanceled && !extendLaunchSession
                            ) {
                                signal.cancel()
                            }
                        }
                    }
                )
            }
        }
    }

    override fun stop(sdkInstanceName: String) {
        if (runningInstances.get().contains(sdkInstanceName)) {
            // note: if we call this while another request is being built, stopSignal will be
            // overwritten by that time. Probably need to allow a single profiler instance and stop profiler before
            // starting another request.
            stopSignal?.cancel()
            profilingStopTime = timeProvider.getDeviceTimestampMillis()
        }
    }

    override fun isRunning(sdkInstanceName: String): Boolean {
        return runningInstances.get().contains(sdkInstanceName)
    }

    override fun registerProfilingCallback(
        appContext: Context,
        sdkInstanceName: String,
        callback: ProfilerCallback
    ) {
        synchronized(callbackMap) {
            callbackMap[sdkInstanceName] = callback
            if (buildSdkVersionProvider.isAtLeastBaklava) {
                anrTriggerRegistrar.register(appContext, anrListener)
            }
        }
    }

    override fun unregisterProfilingCallback(appContext: Context, sdkInstanceName: String) {
        synchronized(callbackMap) {
            callbackMap.remove(sdkInstanceName)
            // Unregister the ANR triggers only when all the SDK instances have unregistered.
            if (callbackMap.isEmpty() && buildSdkVersionProvider.isAtLeastBaklava) {
                anrTriggerRegistrar.unregister(appContext)
            }
        }
    }

    override fun setExtendLaunchSession(extend: Boolean) {
        this.extendLaunchSession = extend
    }

    private fun resolveStopReason(errorCode: Int): String {
        return if (profilingStopTime > 0L) {
            ProfilingTelemetry.STOPPED_REASON_MANUAL
        } else {
            when (errorCode) {
                ProfilingResult.ERROR_NONE -> ProfilingTelemetry.STOPPED_REASON_TIMEOUT
                else -> ProfilingTelemetry.STOPPED_REASON_ERROR
            }
        }
    }

    private fun getDefaultDurationMs(startReason: ProfilingStartReason): Int {
        // Application launch profiling should always be considered as the first window of
        // continuous profiling by default since the duration is not mutable after requesting,
        // but the effective max duration will be controlled by an external timer if continuous
        // profiling is not enabled by users.
        return if (startReason == ProfilingStartReason.APPLICATION_LAUNCH) {
            // Randomize t1 ∈ (0, CONTINUOUS_WINDOW] to provide phase jitter across sessions,
            // avoiding systematic cooldown gaps at predictable time points.
            @Suppress("UnsafeThirdPartyFunctionCall")
            // Until is always bigger than from.
            Random.nextInt(
                APP_LAUNCH_PROFILING_MAX_DURATION_MS.toInt(),
                PROFILING_MAX_DURATION_MS_CONTINUOUS + 1
            )
        } else {
            PROFILING_MAX_DURATION_MS_CONTINUOUS
        }
    }

    companion object {

        // Duration is based on the current P99 TTID metric.
        internal val APP_LAUNCH_PROFILING_MAX_DURATION_MS = TimeUnit.SECONDS.toMillis(10)

        // Duration for continuous profiling cycles (1-minute active window per cycle).
        private const val PROFILING_MAX_DURATION_MS_CONTINUOUS = 60_000

        // Currently we give an estimated maximum size of profiling result to 5MB, it can be
        // increased or configurable if needed.
        private const val BUFFER_SIZE_KB = 5120 // 5MB

        // 201Hz for app launch: higher accuracy to capture startup behavior.
        internal const val PROFILING_SAMPLING_RATE_APP_LAUNCH = 201

        // 101Hz for continuous profiling: lower overhead for sustained background recording.
        internal const val PROFILING_SAMPLING_RATE_CONTINUOUS = 101

        // Re-exported from ProfilingTelemetry so external callers (e.g. content provider)
        // keep using the same property key when passing app-start info as an additional attribute.
        internal const val TELEMETRY_KEY_APP_START_INFO = ProfilingTelemetry.KEY_APP_START_INFO

        private const val OPERATION_NAME_APP_LAUNCH_PROFILING_SCHEDULE =
            "app_launch_profiling_schedule"
    }
}

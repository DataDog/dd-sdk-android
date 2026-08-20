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
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.anr.ProfilingManagerTriggerRegistrar
import com.datadog.android.profiling.internal.anr.ProfilingTriggerListener
import com.datadog.android.profiling.internal.anr.ProfilingTriggerRegistrar
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetryEvent
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import com.datadog.android.profiling.internal.utils.fileSizeSafe
import com.datadog.android.profiling.internal.utils.getProfilingModuleLongVersionCode
import com.datadog.android.profiling.internal.utils.isProfilingModuleVersionBlocked
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
 * @param buildSdkVersionProvider Build.VERSION.SDK_INT provider used to gate trigger
 * registration by API level (ANR on Baklava+, OOM/Anomaly on CinnamonBun+).
 * @param triggerRegistrar registrar that owns the system profiling-trigger lifecycle
 * (ANR / OOM / Anomaly). The profiler passes its internal listener to it at register time;
 * the listener captures the profiler's `callback` so the registered SDK instance receives
 * the detection.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class PerfettoProfiler(
    override val timeProvider: MutableTimeProvider,
    override val scheduledExecutorService: ScheduledExecutorService,
    internal val profilingTelemetry: ProfilingTelemetry = ProfilingTelemetry(),
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    internal val triggerRegistrar: ProfilingTriggerRegistrar =
        ProfilingManagerTriggerRegistrar(
            timeProvider,
            scheduledExecutorService,
            profilingTelemetry,
            buildSdkVersionProvider
        )
) : Profiler {

    internal var stopSignal: CancellationSignal? = null
    private val resultCallback: Consumer<ProfilingResult>

    // Whether a profiling session is currently running.
    private val isRunning: AtomicBoolean = AtomicBoolean(false)

    // Whether the blocked system package version was already reported (reported once per process).
    private val isBlockedReported: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var isPackageVersionResolved = false

    private val packageVersionLock = Any()

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
            triggerRegistrar.internalLogger = value
            profilingTelemetry.internalLogger = value
        }

    internal val triggerListener = object : ProfilingTriggerListener {
        override fun onAnrDetected(event: ProfilingAnrDetectedEvent) {
            callback?.onAnrDetected(event)
        }

        override fun onOutOfMemoryDetected(detectedAtMs: Long, resultFilePath: String) {
            callback?.onOutOfMemoryDetected(detectedAtMs, resultFilePath)
        }

        override fun onMemoryAnomalyDetected(detectedAtMs: Long, resultFilePath: String) {
            callback?.onMemoryAnomalyDetected(detectedAtMs, resultFilePath)
        }
    }

    @Volatile
    private var extendLaunchSession = false

    @Volatile
    private var callback: ProfilerCallback? = null

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
            isRunning.set(false)
            profilingTelemetry.report(
                ProfilingTelemetryEvent.SessionEnd(
                    startReason = profilingStartReason.value,
                    appStartInfo = profilingAppStartInfo,
                    errorCode = result.errorCode,
                    errorMessage = result.errorMessage,
                    fileSize = fileSizeSafe(result.resultFilePath, internalLogger),
                    durationMs = duration,
                    resultCallbackDelayMs = resultCallbackDelayMs,
                    clientClockDriftMs = timeProvider.getServerOffsetMillis(),
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
        if (isRunning.get()) {
            callback?.dispatch()
        }
    }

    override fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        durationMs: Int
    ) {
        val effectiveDurationMs =
            if (durationMs > 0) durationMs else getDefaultDurationMs(startReason)
        if (isProfilingModuleVersionBlocked(profilingPackageVersionCode(appContext))) {
            if (isBlockedReported.compareAndSet(false, true)) {
                profilingTelemetry.report(ProfilingTelemetryEvent.Blocked(startReason.value))
            }
            callback?.onFailure(startReason)
            return
        }
        // profiling will be launched when no session is currently running.
        if (isRunning.compareAndSet(false, true)) {
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

    override fun stop() {
        if (isRunning.get()) {
            // note: if we call this while another request is being built, stopSignal will be
            // overwritten by that time. Probably need to allow a single profiler instance and stop profiler before
            // starting another request.
            stopSignal?.cancel()
            profilingStopTime = timeProvider.getDeviceTimestampMillis()
        }
    }

    override fun isRunning(): Boolean {
        return isRunning.get()
    }

    override fun registerProfilingCallback(
        appContext: Context,
        callback: ProfilerCallback
    ) {
        synchronized(this) {
            this.callback = callback
            if (buildSdkVersionProvider.isAtLeastBaklava) {
                triggerRegistrar.register(appContext, triggerListener)
            }
        }
    }

    override fun unregisterProfilingCallback(appContext: Context) {
        synchronized(this) {
            callback = null
            if (buildSdkVersionProvider.isAtLeastBaklava) {
                triggerRegistrar.unregister(appContext)
            }
        }
    }

    override fun setExtendLaunchSession(extend: Boolean) {
        this.extendLaunchSession = extend
    }

    override fun resolveProfilingPackageVersionCode(appContext: Context) {
        profilingPackageVersionCode(appContext)
    }

    private fun profilingPackageVersionCode(appContext: Context): Long {
        if (!isPackageVersionResolved) {
            synchronized(packageVersionLock) {
                if (!isPackageVersionResolved) {
                    profilingTelemetry.profilingPackageVersionCode =
                        appContext.packageManager.getProfilingModuleLongVersionCode(
                            internalLogger ?: InternalLogger.UNBOUND
                        )
                    isPackageVersionResolved = true
                }
            }
        }
        return profilingTelemetry.profilingPackageVersionCode
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

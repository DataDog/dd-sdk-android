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
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.Profiler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Profiler based on Android's [requestProfiling] API to record callstack samples during application launch.
 *
 * @param internalLogger the logger instance to use for logging.
 * @param timeProvider The time provider to use to get the current time.
 * @param profilingExecutor the executor service to run the profiling task on.
 * @param onProfilingSuccess a callback to be invoked when profiling completes successfully.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class PerfettoProfiler(
    private val internalLogger: InternalLogger,
    private val timeProvider: TimeProvider,
    private val profilingExecutor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val onProfilingSuccess: (PerfettoResult) -> Unit
) : Profiler {

    private val requestBuilder: StackSamplingRequestBuilder

    private val stopSignal = CancellationSignal()

    private val resultCallback: Consumer<ProfilingResult>

    private var profilingStarted: AtomicBoolean = AtomicBoolean(false)

    private var profilingStartTime = 0L

    init {
        requestBuilder = StackSamplingRequestBuilder()
            .setCancellationSignal(stopSignal)
            .setTag(PROFILING_TAG_APPLICATION_LAUNCH)
            .setSamplingFrequencyHz(PROFILING_SAMPLING_RATE)
            .setBufferSizeKb(BUFFER_SIZE_KB)
            .setDurationMs(PROFILING_MAX_DURATION_MS)

        resultCallback = Consumer<ProfilingResult> { result ->
            val endTime = timeProvider.getDeviceTimestamp()
            val duration = endTime - profilingStartTime
            if (result.errorCode == ProfilingResult.ERROR_NONE) {
                result.resultFilePath?.let {
                    onProfilingSuccess(
                        PerfettoResult(
                            start = profilingStartTime,
                            end = endTime,
                            resultFilePath = it
                        )
                    )
                }
            }
            sendProfilingEndTelemetry(result = result, duration = duration)
        }
    }

    override fun start(appContext: Context) {
        // profiling can start only once, so we never set `profilingStarted` back to false.
        if (profilingStarted.compareAndSet(false, true)) {
            sendProfilingStartTelemetry()
            profilingStartTime = timeProvider.getDeviceTimestamp()
            requestProfiling(appContext, requestBuilder.build(), profilingExecutor, resultCallback)
        }
    }

    override fun stop() {
        stopSignal.cancel()
    }

    private fun sendProfilingStartTelemetry() {
        internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { TELEMETRY_MSG_PROFILING_STARTED },
            throwable = null,
            onlyOnce = true,
            additionalProperties = mapOf(
                TELEMETRY_KEY_PROFILING to mapOf(
                    TELEMETRY_KEY_TAG to PROFILING_TAG_APPLICATION_LAUNCH
                )
            )
        )
    }

    private fun sendProfilingEndTelemetry(result: ProfilingResult, duration: Long) {
        internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { TELEMETRY_MSG_PROFILING_FINISHED },
            throwable = null,
            onlyOnce = true,
            additionalProperties = mapOf(
                TELEMETRY_KEY_PROFILING to mapOf(
                    TELEMETRY_KEY_ERROR_CODE to result.errorCode,
                    TELEMETRY_KEY_TAG to PROFILING_TAG_APPLICATION_LAUNCH,
                    TELEMETRY_KEY_ERROR_MESSAGE to result.errorMessage,
                    TELEMETRY_KEY_DURATION to duration
                )
            )
        )
    }

    companion object {
        private val PROFILING_MAX_DURATION_MS = TimeUnit.MINUTES.toMillis(1).toInt()
        private const val PROFILING_TAG_APPLICATION_LAUNCH = "ApplicationLaunch"

        // Currently we give an estimated maximum size of profiling result to 5MB, it can be
        // increased or configurable if needed.
        private const val BUFFER_SIZE_KB = 5120 // 5MB

        // Currently we give 100HZ frequency to balance the sampling accuracy and performance
        // overhead, it can be updated or configurable if needed.
        private const val PROFILING_SAMPLING_RATE = 100 // 100Hz
        private const val TELEMETRY_MSG_PROFILING_STARTED = "Profiling started."
        private const val TELEMETRY_MSG_PROFILING_FINISHED = "Profiling finished."
        private const val TELEMETRY_KEY_PROFILING = "profiling"
        private const val TELEMETRY_KEY_ERROR_CODE = "error_code"
        private const val TELEMETRY_KEY_TAG = "tag"
        private const val TELEMETRY_KEY_ERROR_MESSAGE = "error_message"
        private const val TELEMETRY_KEY_DURATION = "duration"
    }
}

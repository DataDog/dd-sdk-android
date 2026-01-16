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
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Profiler based on Android's [requestProfiling] API to record callstack samples during application launch.
 *
 * @param timeProvider The time provider to use to get the current time.
 * @param profilingExecutor the executor service to run the profiling task on.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class PerfettoProfiler(
    private val timeProvider: TimeProvider,
    private val profilingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
) : Profiler {

    private var stopSignal: CancellationSignal? = null
    private val resultCallback: Consumer<ProfilingResult>

    // This flag represents which instance of this class is working for.
    private val runningInstances: AtomicReference<Set<String>> = AtomicReference(emptySet())

    @Volatile
    private var profilingStartTime = 0L

    override var internalLogger: InternalLogger? = null

    // Map of <InstanceName, ProfilerCallback>
    private val callbackMap: MutableMap<String, ProfilerCallback> = ConcurrentHashMap()

    init {
        resultCallback = Consumer<ProfilingResult> { result ->
            val endTime = timeProvider.getDeviceTimestampMillis()
            val duration = endTime - profilingStartTime
            if (result.errorCode == ProfilingResult.ERROR_NONE) {
                // TODO RUM-13679: need to delete the file after it is no longer needed
                result.resultFilePath?.let {
                    notifyAllCallbacks(
                        PerfettoResult(
                            start = profilingStartTime,
                            end = endTime,
                            tag = result.tag.orEmpty(),
                            resultFilePath = it
                        )
                    )
                }
            }
            sendProfilingEndTelemetry(result = result, duration = duration)
        }
    }

    private fun buildStackSamplingRequest(): ProfilingRequest {
        return CancellationSignal().let {
            this.stopSignal = it
            StackSamplingRequestBuilder()
                .setCancellationSignal(it)
                .setTag(PROFILING_TAG_APPLICATION_LAUNCH)
                .setSamplingFrequencyHz(PROFILING_SAMPLING_RATE)
                .setBufferSizeKb(BUFFER_SIZE_KB)
                .setDurationMs(PROFILING_MAX_DURATION_MS)
                .build()
        }
    }

    private fun notifyAllCallbacks(result: PerfettoResult) {
        callbackMap.filter { runningInstances.get().contains(it.key) }.forEach { callback ->
            callback.value.onSuccess(result)
        }
        runningInstances.set(emptySet())
    }

    override fun start(appContext: Context, sdkInstanceNames: Set<String>) {
        // profiling will be launched when no instance is currently running profiling.
        if (runningInstances.compareAndSet(emptySet(), sdkInstanceNames)) {
            sendProfilingStartTelemetry()
            profilingStartTime = timeProvider.getDeviceTimestampMillis()
            requestProfiling(
                appContext,
                buildStackSamplingRequest(),
                profilingExecutor,
                resultCallback
            )
        }
    }

    override fun stop(sdkInstanceName: String) {
        if (runningInstances.get().contains(sdkInstanceName)) {
            // note: if we call this while another request is being built, stopSignal will be
            // overwritten by that time. Probably need to allow a single profiler instance and stop profiler before
            // starting another request.
            stopSignal?.cancel()
            stopSignal = null
        }
    }

    override fun isRunning(sdkInstanceName: String): Boolean {
        return runningInstances.get().contains(sdkInstanceName)
    }

    override fun registerProfilingCallback(
        sdkInstanceName: String,
        callback: ProfilerCallback
    ) {
        callbackMap[sdkInstanceName] = callback
    }

    override fun unregisterProfilingCallback(sdkInstanceName: String) {
        callbackMap.remove(sdkInstanceName)
    }

    private fun sendProfilingStartTelemetry() {
        internalLogger?.log(
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
        internalLogger?.log(
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
                    TELEMETRY_KEY_DURATION to duration,
                    TELEMETRY_KEY_FILE_SIZE to getFileSize(result.resultFilePath)
                )
            )
        )
    }

    private fun getFileSize(filePath: String?): Long {
        return internalLogger?.let { logger ->
            filePath?.let {
                val file = File(filePath)
                file.lengthSafe(logger)
            }
        } ?: 0
    }

    companion object {

        // Duration is based on the current P99 TTID metric.
        private val PROFILING_MAX_DURATION_MS = TimeUnit.SECONDS.toMillis(10).toInt()
        internal const val PROFILING_TAG_APPLICATION_LAUNCH = "ApplicationLaunch"

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
        private const val TELEMETRY_KEY_FILE_SIZE = "size_bytes"
    }
}

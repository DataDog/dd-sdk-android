/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetryEvent
import com.datadog.android.profiling.internal.utils.ThreadDumper
import com.datadog.android.profiling.internal.utils.fileSizeSafe
import com.datadog.android.profiling.internal.utils.getFileCreationTimeMs
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * BAKLAVA+ implementation of [ProfilingTriggerRegistrar] backed by the system
 * [ProfilingManager.addProfilingTriggers] /
 * [ProfilingManager.registerForAllProfilingResults] APIs.
 */
internal class ProfilingManagerTriggerRegistrar(
    private val timeProvider: TimeProvider,
    private val executorService: ExecutorService,
    private val profilingTelemetry: ProfilingTelemetry,
    private val buildSdkVersionProvider: BuildSdkVersionProvider
) : ProfilingTriggerRegistrar {

    @Volatile
    internal var threadDumper: ThreadDumper = ThreadDumper()

    @Volatile
    override var internalLogger: InternalLogger? = null
        set(value) {
            field = value
            threadDumper.internalLogger = value
        }

    private val registered = AtomicBoolean(false)

    @Volatile
    private var listener: ProfilingTriggerListener? = null

    // Testable seam
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    internal var triggersFactory: () -> List<ProfilingTrigger> = {
        buildDefaultTriggers()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @Volatile
    private var registeredTriggerTypes: IntArray = intArrayOf(ProfilingTrigger.TRIGGER_TYPE_ANR)

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun buildDefaultTriggers(): List<ProfilingTrigger> {
        // TODO RUM-18175: register each trigger type separately based on the
        // ProfilingConfiguration so users can opt in/out of individual trigger
        // types (e.g. enable OOM but not ANOMALY). Currently all triggers are
        // registered together unconditionally.
        val triggers = mutableListOf<ProfilingTrigger>()
        triggers.add(ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANR).build())
        if (buildSdkVersionProvider.isAtLeastCinnamonBun) {
            triggers.add(
                ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_OOM).build()
            )
            triggers.add(
                ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANOMALY).build()
            )
        }
        return triggers
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private val resultCallback = Consumer<ProfilingResult> { result ->
        handleResult(result)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @Suppress("ReturnCount")
    override fun register(appContext: Context, listener: ProfilingTriggerListener) {
        if (registered.get()) return

        val manager = appContext.getSystemService(ProfilingManager::class.java)
        if (manager == null) {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { LOG_NO_MANAGER }
            )
            return
        }

        if (!registered.compareAndSet(false, true)) return

        this.listener = listener
        val triggers = triggersFactory()
        registeredTriggerTypes = triggers.map { it.triggerType }.toIntArray()
        manager.addProfilingTriggers(triggers)
        manager.registerForAllProfilingResults(executorService, resultCallback)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @Suppress("ReturnCount")
    override fun unregister(appContext: Context) {
        if (!registered.get()) return

        val manager = appContext.getSystemService(ProfilingManager::class.java)
        if (manager == null) {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { LOG_NO_MANAGER }
            )
            return
        }

        if (!registered.compareAndSet(true, false)) return

        manager.removeProfilingTriggersByType(registeredTriggerTypes)
        manager.unregisterForAllProfilingResults(resultCallback)
        listener = null
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun handleResult(result: ProfilingResult) {
        val triggerType = result.triggerType
        if (triggerType != ProfilingTrigger.TRIGGER_TYPE_ANR &&
            triggerType != ProfilingTrigger.TRIGGER_TYPE_OOM &&
            triggerType != ProfilingTrigger.TRIGGER_TYPE_ANOMALY
        ) {
            return
        }
        val currentListener = listener ?: return
        val detectedAtMs = timeProvider.getDeviceTimestampMillis()
        val resultPath = result.resultFilePath
        var callbackDelayMs: Long? = null
        var droppedAsStale = false
        var fileSize = 0L
        if (result.errorCode == ProfilingResult.ERROR_NONE && !resultPath.isNullOrEmpty()) {
            fileSize = fileSizeSafe(resultPath, internalLogger)
            val creationTimeMs = getFileCreationTimeMs(resultPath, internalLogger)
            if (creationTimeMs != null) {
                val delayMs = detectedAtMs - creationTimeMs
                callbackDelayMs = delayMs
                droppedAsStale = delayMs > MAX_CALLBACK_DELAY_MS
            }
            if (callbackDelayMs != null && !droppedAsStale) {
                forwardTriggerResult(triggerType, currentListener, detectedAtMs, resultPath)
                // We currently don't use the result profile, just delete it.
                safeDelete(resultPath)
            } else {
                // Not forwarded (stale, or could not compute staleness): delete to avoid leaking.
                safeDelete(resultPath)
            }
        }
        profilingTelemetry.report(
            ProfilingTelemetryEvent.TriggerResult(
                triggerType = triggerType,
                errorCode = result.errorCode,
                errorMessage = result.errorMessage,
                fileSize = fileSize,
                callbackDelayMs = callbackDelayMs,
                clientClockDriftMs = timeProvider.getServerOffsetMillis(),
                droppedAsStale = droppedAsStale
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun forwardTriggerResult(
        triggerType: Int,
        listener: ProfilingTriggerListener,
        detectedAtMs: Long,
        resultPath: String
    ) {
        when (triggerType) {
            ProfilingTrigger.TRIGGER_TYPE_ANR ->
                listener.onAnrDetected(threadDumper.dump(detectedAtMs))

            ProfilingTrigger.TRIGGER_TYPE_OOM ->
                listener.onOutOfMemoryDetected(
                    PerfettoResult(
                        start = detectedAtMs,
                        startReason = ProfilingStartReason.OUT_OF_MEMORY,
                        end = detectedAtMs,
                        resultFilePath = resultPath
                    )
                )

            ProfilingTrigger.TRIGGER_TYPE_ANOMALY ->
                // TODO RUM-18223: filter in only memory anomaly result by tag
                listener.onMemoryAnomalyDetected(
                    PerfettoResult(
                        start = detectedAtMs,
                        startReason = ProfilingStartReason.MEMORY_ANOMALY,
                        // end is same as start in point-in-time profile
                        end = detectedAtMs,
                        resultFilePath = resultPath
                    )
                )
        }
    }

    private fun safeDelete(path: String) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            val deleted = File(path).delete()
            if (!deleted) {
                internalLogger?.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { LOG_FILE_DELETE_FAILED }
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { LOG_FILE_DELETE_FAILED },
                t
            )
        }
    }

    private companion object {
        const val MAX_CALLBACK_DELAY_MS = 1_000L
        const val LOG_NO_MANAGER =
            "Cannot register profiling trigger: ProfilingManager system service is unavailable."
        const val LOG_FILE_DELETE_FAILED = "Failed to delete ANR trigger trace file."
    }
}

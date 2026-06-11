/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.anr

import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
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
 * BAKLAVA+ implementation of [AnrTriggerRegistrar] backed by the system
 * [ProfilingManager.addProfilingTriggers] /
 * [ProfilingManager.registerForAllProfilingResults] APIs.
 */
internal class AnrProfilingTriggerRegistrar(
    private val timeProvider: TimeProvider,
    private val executorService: ExecutorService,
    private val profilingTelemetry: ProfilingTelemetry
) : AnrTriggerRegistrar {

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
    private var listener: AnrListener? = null

    // Testable seam
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    internal var triggerFactory: () -> ProfilingTrigger = {
        ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANR).build()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private val resultCallback = Consumer<ProfilingResult> { result ->
        handleResult(result)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    @Suppress("ReturnCount")
    override fun register(appContext: Context, listener: AnrListener) {
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
        val trigger = triggerFactory()
        manager.addProfilingTriggers(listOf(trigger))
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

        manager.removeProfilingTriggersByType(intArrayOf(ProfilingTrigger.TRIGGER_TYPE_ANR))
        manager.unregisterForAllProfilingResults(resultCallback)
        listener = null
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun handleResult(result: ProfilingResult) {
        if (result.triggerType != ProfilingTrigger.TRIGGER_TYPE_ANR) return

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
                if (delayMs > MAX_CALLBACK_DELAY_MS) {
                    droppedAsStale = true
                } else {
                    currentListener.onAnrDetected(threadDumper.dump(detectedAtMs))
                }
            }
            // We currently don't use the result profile, just delete it.
            safeDelete(resultPath)
        }
        profilingTelemetry.report(
            ProfilingTelemetryEvent.AnrTriggerResult(
                errorCode = result.errorCode,
                errorMessage = result.errorMessage,
                fileSize = fileSize,
                callbackDelayMs = callbackDelayMs,
                clientClockDriftMs = timeProvider.getServerOffsetMillis(),
                droppedAsStale = droppedAsStale
            )
        )
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
            "Cannot register ANR profiling trigger: ProfilingManager system service is unavailable."
        const val LOG_FILE_DELETE_FAILED = "Failed to delete ANR trigger trace file."
    }
}

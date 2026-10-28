/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.fixtures

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.ProfileType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * Test handle around an internal [ProfilingFeature] for use from integration tests in
 * other modules. Holds a [NoOpProfiler] so no real Perfetto profiling is started, while
 * the surrounding cross-feature wiring (event receiver, context-update receiver,
 * scheduler bootstrap) runs exactly as in production.
 */
@OptIn(ExperimentalProfilingApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProfilingFeatureTestHandle internal constructor(
    private val feature: ProfilingFeature
) {

    val asFeature: Feature get() = feature

    val lastSeenRumSessionId: String? get() = feature.lastSeenRumSessionId

    val schedulerSessionId: String? get() = feature.continuousProfilingScheduler?.currentSessionId

    val isSchedulerSessionSampled: Boolean
        get() = feature.continuousProfilingScheduler?.currentSessionSampled ?: false

    /**
     * Simulates the OS delivering the result of the OOM trigger, with the trace at [resultFilePath].
     */
    fun deliverOomTriggerResult(resultFilePath: String, startMs: Long, endMs: Long) {
        feature.onOutOfMemoryDetected(
            PerfettoResult(
                start = startMs,
                startReason = ProfilingStartReason.OUT_OF_MEMORY,
                end = endMs,
                resultFilePath = resultFilePath,
                profileTypes = listOf(ProfileType.HEAP_HISTOGRAM),
                bootNtpOffsetNs = 0L
            )
        )
    }

    /**
     * A profiler whose OOM trigger counts as registered, and which runs its tasks on the calling
     * thread. Delayed tasks run on a daemon thread and are irrelevant to the OOM handshake.
     */
    private class OomTriggerTestProfiler(
        private val delegate: Profiler = NoOpProfiler()
    ) : Profiler by delegate {
        override val isOomTriggerRegistered: Boolean = true

        override val scheduledExecutorService: ScheduledExecutorService =
            object : ScheduledThreadPoolExecutor(1, { Thread(it).apply { isDaemon = true } }) {
                override fun execute(command: Runnable) = command.run()
            }
    }

    companion object {

        /**
         * Build a [ProfilingFeatureTestHandle] backed by a [NoOpProfiler] and register the
         * feature with [sdkCore], mirroring the production `Profiling.enable` entry point.
         * With [isOomTriggerRegistered], the OOM trigger counts as registered and the profiler's
         * tasks run on the calling thread.
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun create(
            sdkCore: FeatureSdkCore,
            configuration: ProfilingConfiguration = ProfilingConfiguration.DEFAULT,
            isOomTriggerRegistered: Boolean = false
        ): ProfilingFeatureTestHandle {
            val feature = ProfilingFeature(
                sdkCore = sdkCore,
                configuration = configuration,
                profiler = if (isOomTriggerRegistered) OomTriggerTestProfiler() else NoOpProfiler()
            )
            sdkCore.registerFeature(feature)
            return ProfilingFeatureTestHandle(feature)
        }
    }
}

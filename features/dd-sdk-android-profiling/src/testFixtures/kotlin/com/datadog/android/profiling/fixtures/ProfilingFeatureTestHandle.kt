/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.fixtures

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.ProfileType
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Test handle around an internal [ProfilingFeature] for use from integration tests in
 * other modules. Holds a [NoOpProfiler] so no real Perfetto profiling is started, while
 * the surrounding cross-feature wiring (event receiver, context-update receiver,
 * scheduler bootstrap) runs exactly as in production.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProfilingFeatureTestHandle internal constructor(
    private val feature: ProfilingFeature
) {

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
     * A profiler whose OOM trigger counts as registered while triggers are enabled, and which runs its tasks on the calling
     * thread.
     */
    private class OomTriggerTestProfiler(
        private val delegate: Profiler = NoOpProfiler()
    ) : Profiler by delegate {
        private var callback: ProfilerCallback? = null

        override var isOomTriggerRegistered: Boolean = false

        override fun registerProfilingCallback(appContext: Context, callback: ProfilerCallback) {
            this.callback = callback
        }

        override fun setTriggersEnabled(appContext: Context, enabled: Boolean) {
            if (enabled == isOomTriggerRegistered) return
            isOomTriggerRegistered = enabled
            callback?.onOomTriggerRegisteredChanged(enabled)
        }

        override val scheduledExecutorService: ScheduledExecutorService = SameThreadScheduledExecutor()
    }

    /** Runs tasks on the calling thread. Delayed tasks never run: none matters to the OOM handshake. */
    private class SameThreadScheduledExecutor : AbstractExecutorService(), ScheduledExecutorService {
        override fun execute(command: Runnable) = command.run()

        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
            NeverRunFuture<Unit>()

        override fun <V : Any?> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> =
            NeverRunFuture()

        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit
        ): ScheduledFuture<*> = NeverRunFuture<Unit>()

        override fun scheduleWithFixedDelay(
            command: Runnable,
            initialDelay: Long,
            delay: Long,
            unit: TimeUnit
        ): ScheduledFuture<*> = NeverRunFuture<Unit>()

        override fun shutdown() = Unit

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()

        override fun isShutdown(): Boolean = false

        override fun isTerminated(): Boolean = false

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private class NeverRunFuture<V> : ScheduledFuture<V> {
        override fun getDelay(unit: TimeUnit): Long = Long.MAX_VALUE

        override fun compareTo(other: Delayed): Int = 0

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = true

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = false

        override fun get(): V = throw CancellationException()

        override fun get(timeout: Long, unit: TimeUnit): V = throw CancellationException()
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

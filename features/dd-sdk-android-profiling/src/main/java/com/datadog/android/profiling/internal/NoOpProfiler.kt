/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class NoOpProfiler : Profiler {
    override val timeProvider: MutableTimeProvider = MutableTimeProvider.create(DefaultTimeProvider())
    override var internalLogger: InternalLogger? = null
    override val scheduledExecutorService: ScheduledExecutorService = NoOpScheduledExecutorService()

    override fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        durationMs: Int
    ) = Unit

    override fun stop() = Unit

    override fun isRunning(): Boolean = false

    override fun registerProfilingCallback(
        appContext: Context,
        callback: ProfilerCallback
    ) = Unit

    override fun unregisterProfilingCallback(appContext: Context) = Unit

    override fun setExtendLaunchSession(extend: Boolean) = Unit

    override fun setProfilingPackageVersionCode(versionCode: Long) = Unit

    class NoOpScheduledExecutorService : ScheduledExecutorService {
        override fun schedule(
            command: Runnable?,
            delay: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*>? = null

        override fun <V> schedule(
            callable: Callable<V?>?,
            delay: Long,
            unit: TimeUnit?
        ): ScheduledFuture<V?>? = null

        override fun scheduleAtFixedRate(
            command: Runnable?,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*>? = null

        override fun scheduleWithFixedDelay(
            command: Runnable?,
            initialDelay: Long,
            delay: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*>? = null

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit?
        ): Boolean = false

        override fun <T> invokeAll(tasks: Collection<Callable<T?>?>?): List<Future<T?>?>? = null

        override fun <T> invokeAll(
            tasks: Collection<Callable<T?>?>?,
            timeout: Long,
            unit: TimeUnit?
        ): List<Future<T?>?>? = null

        override fun <T> invokeAny(tasks: Collection<Callable<T?>?>?): T? = null

        override fun <T> invokeAny(
            tasks: Collection<Callable<T?>?>?,
            timeout: Long,
            unit: TimeUnit?
        ): T? = null

        override fun isShutdown(): Boolean = false

        override fun isTerminated(): Boolean = false

        override fun shutdown() {}

        override fun shutdownNow(): List<Runnable?>? = null

        override fun submit(task: Runnable?): Future<*>? = null

        override fun <T> submit(
            task: Runnable?,
            result: T?
        ): Future<T?>? = null

        override fun <T> submit(task: Callable<T?>?): Future<T?>? = null

        override fun execute(command: Runnable?) {}
    }
}

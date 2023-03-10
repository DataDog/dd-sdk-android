/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.thread

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class NoOpScheduledExecutorService : ScheduledExecutorService {
    override fun execute(command: Runnable?) {
        // No-op
    }

    override fun shutdown() {
        // No-op
    }

    override fun shutdownNow(): MutableList<Runnable>? {
        return null
    }

    override fun isShutdown(): Boolean {
        return false
    }

    override fun isTerminated(): Boolean {
        return false
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
        return false
    }

    override fun <T : Any?> submit(task: Callable<T>?): Future<T>? {
        return null
    }

    override fun <T : Any?> submit(task: Runnable?, result: T): Future<T>? {
        return null
    }

    override fun submit(task: Runnable?): Future<*>? {
        return null
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>?):
        MutableList<Future<T>>? {
        return null
    }

    override fun <T : Any?> invokeAll(
        tasks: MutableCollection<out Callable<T>>?,
        timeout: Long,
        unit: TimeUnit?
    ): MutableList<Future<T>>? {
        return null
    }

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>?): T? {
        return null
    }

    override fun <T : Any?> invokeAny(
        tasks: MutableCollection<out Callable<T>>?,
        timeout: Long,
        unit: TimeUnit?
    ): T? {
        return null
    }

    override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*>? {
        return null
    }

    override fun <V : Any?> schedule(
        callable: Callable<V>?,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<V>? {
        return null
    }

    override fun scheduleAtFixedRate(
        command: Runnable?,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit?
    ): ScheduledFuture<*>? {
        return null
    }

    override fun scheduleWithFixedDelay(
        command: Runnable?,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<*>? {
        return null
    }
}

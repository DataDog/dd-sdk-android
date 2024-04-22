/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.sign

/**
 * Stubbed version of a [ScheduledExecutorService].
 */
@Suppress("UndocumentedPublicFunction")
class StubScheduledExecutorService(executorContext: String) : ScheduledExecutorService {

    private var isShutdown = false
    private var isTerminated = false

    init {
        println("Stubbing a ScheduledExecutorService with context: $executorContext")
    }

    override fun execute(command: Runnable?) {
        command?.run()
    }

    override fun shutdown() {
        isShutdown = true
        isTerminated = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        isShutdown = true
        isTerminated = true
        return mutableListOf()
    }

    override fun isShutdown(): Boolean {
        return isShutdown
    }

    override fun isTerminated(): Boolean {
        return isTerminated
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
        return false
    }

    override fun <T : Any?> submit(task: Callable<T>?): Future<T> {
        val futureTask = FutureTask(task)
        futureTask.run()
        return futureTask
    }

    override fun <T : Any?> submit(task: Runnable?, result: T): Future<T> {
        val futureTask = FutureTask(task, result)
        futureTask.run()
        return futureTask
    }

    override fun submit(task: Runnable?): Future<*> {
        val futureTask = FutureTask(task, null)
        futureTask.run()
        return futureTask
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>?): MutableList<Future<T>> {
        TODO()
    }

    override fun <T : Any?> invokeAll(
        tasks: MutableCollection<out Callable<T>>?,
        timeout: Long,
        unit: TimeUnit?
    ): MutableList<Future<T>> {
        TODO()
    }

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>?): T {
        TODO()
    }

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>?, timeout: Long, unit: TimeUnit?): T {
        TODO()
    }

    override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
        val futureTask = FutureTask(command, null)
        val delayMs = unit!!.toMillis(delay)
        val triggerTimestamp = System.currentTimeMillis() + delayMs
        val scheduledFutureTask = ScheduledFutureTask(
            futureTask,
            triggerTimestamp
        )
        Thread {
            Thread.sleep(delayMs)
            futureTask.run()
        }.start()
        return scheduledFutureTask
    }

    override fun <V : Any?> schedule(callable: Callable<V>?, delay: Long, unit: TimeUnit?): ScheduledFuture<V> {
        val futureTask = FutureTask(callable)
        val delayMs = unit!!.toMillis(delay)
        val triggerTimestamp = System.currentTimeMillis() + delayMs
        val scheduledFutureTask = ScheduledFutureTask(
            futureTask,
            triggerTimestamp
        )
        Thread {
            Thread.sleep(delayMs)
            futureTask.run()
        }.start()
        return scheduledFutureTask
    }

    override fun scheduleAtFixedRate(
        command: Runnable?,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit?
    ): ScheduledFuture<*> {
        TODO()
    }

    override fun scheduleWithFixedDelay(
        command: Runnable?,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<*> {
        TODO()
    }

    /**
     * Stubbed version of a [RunnableFuture]+[ScheduledFuture].
     * @param V the type of the task's result
     * @property delegate the delegate [FutureTask]
     * @property triggerTimestamp the timestamp at which the task should be triggered
     */
    class ScheduledFutureTask<V>(
        val delegate: FutureTask<V>,
        val triggerTimestamp: Long
    ) : RunnableFuture<V> by delegate, ScheduledFuture<V> {
        override fun compareTo(other: Delayed?): Int {
            val delay = getDelay(TimeUnit.MILLISECONDS)
            val otherDelay = other!!.getDelay(TimeUnit.MILLISECONDS)
            return (delay - otherDelay).sign
        }

        override fun getDelay(unit: TimeUnit?): Long {
            val delayMs = triggerTimestamp - System.currentTimeMillis()
            return unit!!.convert(delayMs, TimeUnit.MILLISECONDS)
        }
    }
}

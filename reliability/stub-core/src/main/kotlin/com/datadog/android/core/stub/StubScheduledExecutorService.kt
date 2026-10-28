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
 * Delayed tasks run only when [runDueTasks] is called after advancing the supplied clock.
 */
@Suppress("UndocumentedPublicFunction")
class StubScheduledExecutorService(
    executorContext: String,
    private val nowProvider: () -> Long
) : ScheduledExecutorService {

    private var isShutdown = false
    private var isTerminated = false

    private val pendingTasks = mutableListOf<ScheduledFutureTask<*>>()

    init {
        println("Stubbing a ScheduledExecutorService with context: $executorContext")
    }

    override fun execute(command: Runnable) {
        command.run()
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

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return false
    }

    override fun <T> submit(task: Callable<T>): Future<T> {
        val futureTask = FutureTask(task)
        futureTask.run()
        return futureTask
    }

    override fun <T> submit(task: Runnable, result: T): Future<T> {
        val futureTask = FutureTask(task, result)
        futureTask.run()
        return futureTask
    }

    override fun submit(task: Runnable): Future<*> {
        val futureTask = FutureTask(task, null)
        futureTask.run()
        return futureTask
    }

    override fun <T> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> {
        TODO()
    }

    override fun <T> invokeAll(
        tasks: MutableCollection<out Callable<T>>,
        timeout: Long,
        unit: TimeUnit
    ): MutableList<Future<T>> {
        TODO()
    }

    override fun <T> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
        TODO()
    }

    override fun <T> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): T {
        TODO()
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduleTask(FutureTask(command, null), delay, unit)
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        return scheduleTask(FutureTask(callable), delay, unit)
    }

    override fun scheduleAtFixedRate(
        command: Runnable,
        initialDelay: Long,
        period: Long,
        unit: TimeUnit
    ): ScheduledFuture<*> {
        return neverTriggeredTask(command, initialDelay, unit)
    }

    override fun scheduleWithFixedDelay(
        command: Runnable,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit
    ): ScheduledFuture<*> {
        return neverTriggeredTask(command, initialDelay, unit)
    }

    /**
     * Runs queued one-shot tasks whose deadlines have passed according to [nowProvider].
     * Tasks with the same deadline run in submission order. Periodic tasks remain disabled.
     */
    fun runDueTasks() {
        while (true) {
            val nextTask = pendingTasks.minByOrNull { it.triggerTimestamp } ?: return
            if (nextTask.getDelay(TimeUnit.MILLISECONDS) > 0L) return
            pendingTasks.remove(nextTask)
            nextTask.run()
        }
    }

    internal fun hasDueTasks(): Boolean {
        return pendingTasks.any { it.getDelay(TimeUnit.MILLISECONDS) <= 0L }
    }

    private fun <V> scheduleTask(futureTask: FutureTask<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        val delayMs = unit.toMillis(delay)
        val scheduledTask = ScheduledFutureTask(futureTask, nowProvider() + delayMs, nowProvider)
        pendingTasks.add(scheduledTask)
        return scheduledTask
    }

    // Periodic tasks are never triggered, to keep the tests deterministic: tests needing their
    // effect have to trigger it explicitly (e.g. by stopping the feature, which flushes).
    private fun neverTriggeredTask(command: Runnable, initialDelay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return ScheduledFutureTask(
            FutureTask(command, null),
            nowProvider() + unit.toMillis(initialDelay),
            nowProvider
        )
    }

    /**
     * Stubbed version of a [RunnableFuture]+[ScheduledFuture].
     * @param V the type of the task's result
     * @property delegate the delegate [FutureTask]
     * @property triggerTimestamp the timestamp at which the task should be triggered
     * @property nowProvider a function that provides the current timestamp in milliseconds
     */
    class ScheduledFutureTask<V>(
        val delegate: FutureTask<V>,
        val triggerTimestamp: Long,
        val nowProvider: () -> Long
    ) : RunnableFuture<V> by delegate, ScheduledFuture<V> {
        override fun compareTo(other: Delayed?): Int {
            val delay = getDelay(TimeUnit.MILLISECONDS)
            val otherDelay = other!!.getDelay(TimeUnit.MILLISECONDS)
            return (delay - otherDelay).sign
        }

        override fun getDelay(unit: TimeUnit): Long {
            val delayMs = triggerTimestamp - nowProvider()
            return unit.convert(delayMs, TimeUnit.MILLISECONDS)
        }
    }
}

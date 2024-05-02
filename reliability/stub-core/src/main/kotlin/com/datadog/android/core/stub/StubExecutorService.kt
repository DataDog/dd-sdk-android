/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Stubbed version of an [ExecutorService].
 */
class StubExecutorService(executorContext: String) : ExecutorService {

    init {
        println("Stubbing an ExecutorService with context: $executorContext")
    }

    private var isShutdown = false
    private var isTerminated = false

    override fun execute(command: Runnable?) {
        check(!isShutdown)
        check(!isTerminated)
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
}

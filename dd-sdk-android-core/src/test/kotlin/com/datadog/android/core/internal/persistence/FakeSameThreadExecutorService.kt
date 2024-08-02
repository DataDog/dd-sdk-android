/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class FakeSameThreadExecutorService : AbstractExecutorService() {

    private var isShutdown = false

    override fun execute(command: Runnable?) {
        if (command is FutureTask<*>) {
            command.run()
            val result = command.get()
            println("Command returned $result")
        } else {
            command?.run()
        }
    }

    override fun shutdown() {
        isShutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        isShutdown = true
        return mutableListOf()
    }

    override fun isShutdown(): Boolean = isShutdown

    override fun isTerminated(): Boolean = isShutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
        return true
    }
}

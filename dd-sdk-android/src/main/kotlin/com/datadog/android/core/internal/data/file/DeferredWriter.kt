/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal class DeferredWriter<T : Any>(
    private val writer: Writer<T>,
    private val executorService: ExecutorService,
    dataMigrator: DataMigrator? = null
) : Writer<T> {

    private val dataMigrated: AtomicBoolean = AtomicBoolean(false)
    private val messagesQueue: LinkedList<Runnable> = LinkedList()

    init {
        if (dataMigrator != null) {
            executorService.submit(Runnable {
                dataMigrator.migrateData()
                dataMigrated.set(true)
                // we make sure we consume everything from the message queue
                synchronized(messagesQueue) {
                    while (messagesQueue.isNotEmpty()) {
                        messagesQueue.remove().run()
                    }
                }
            })
        } else {
            dataMigrated.set(true)
        }
    }

    // region Writer

    override fun write(model: T) {
        handleRunnable(Runnable {
            writer.write(model)
        })
    }

    override fun write(models: List<T>) {
        handleRunnable(Runnable {
            writer.write(models)
        })
    }

    // endregion

    // region internal

    private fun handleRunnable(runnable: Runnable) {
        if (dataMigrated.get()) {
            tryToConsumeQueue()
            executorService.submit(runnable)
        } else {
            addToQueue(runnable)
        }
    }

    private fun addToQueue(runnable: Runnable) {
        synchronized(messagesQueue) {
            messagesQueue.add(runnable)
        }
    }

    private fun tryToConsumeQueue() {
        if (messagesQueue.isNotEmpty()) {
            synchronized(messagesQueue) {
                while (messagesQueue.isNotEmpty()) {
                    executorService.submit(messagesQueue.remove())
                }
            }
        }
    }

    // endregion
}

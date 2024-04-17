/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.tests.utils

import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.DDSpan
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

internal class BlockingWriterWrapper(wrapped: Writer) : Writer {
    private val latches: LinkedList<CountDownLatch> = LinkedList()
    private val currentTraceCount: AtomicInteger = AtomicInteger(0)

    private val wrappedWriter = wrapped

    override fun write(trace: MutableList<DDSpan>?) {
        wrappedWriter.write(trace)
        currentTraceCount.incrementAndGet()
        synchronized(latches) {
            for (latch in latches) {
                latch.countDown()
            }
        }
    }

    override fun start() {
        wrappedWriter.start()
    }

    override fun flush(): Boolean {
        return wrappedWriter.flush()
    }

    override fun close() {
        wrappedWriter.close()
    }

    override fun incrementDropCounts(spanCount: Int) {
        wrappedWriter.incrementDropCounts(spanCount)
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun waitForTracesMax(number: Int, seconds: Int = 10): Boolean {
        val toWait = number - currentTraceCount.get()
        if (toWait <= 0) {
            return true
        }
        val latch = CountDownLatch(toWait)
        synchronized(latches) {
            latches.add(latch)
        }
        return latch.await(seconds.toLong(), TimeUnit.SECONDS)
    }
}

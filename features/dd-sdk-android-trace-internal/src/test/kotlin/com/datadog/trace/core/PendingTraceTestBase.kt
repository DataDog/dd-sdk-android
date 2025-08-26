/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.common.writer.ListWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal abstract class PendingTraceTestBase : DDCoreSpecification() {

    protected val writer = ListWriter()
    protected val tracer = tracerBuilder().writer(writer).build()

    protected lateinit var rootSpan: DDSpan
    protected lateinit var trace: PendingTrace

    @BeforeEach
    override fun setup() {
        super.setup()
        rootSpan = tracer.buildSpan(instrumentationName, "fakeOperation").start() as DDSpan
        trace = rootSpan.context().trace as PendingTrace
        assertThat(trace.size()).isEqualTo(0)
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.isRootSpanWritten).isFalse()
    }

    @AfterEach
    override fun cleanup() {
        super.cleanup()
        tracer.close()
    }

    @Test
    fun `single span gets added to trace and written when finished`() {
        // When
        rootSpan.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(rootSpan)
        assertThat(writer.traceCount.get()).isEqualTo(1)
    }

    @Test
    fun `child finishes before parent`() {
        // Given
        val child = tracer
            .buildSpan(instrumentationName, "child")
            .asChildOf(rootSpan.context())
            .start() as DDSpan

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(2)

        // When
        child.finish()

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans.toList()).containsExactly(child)
        assertThat(writer).isEmpty()

        // When
        rootSpan.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(rootSpan, child)
        assertThat(writer.traceCount.get()).isEqualTo(1)
    }

    @Test
    fun `parent finishes before child which holds up trace`() {
        // Given
        val child = tracer.buildSpan(instrumentationName, "child")
            .asChildOf(rootSpan.context()).start() as DDSpan

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(2)

        // When
        rootSpan.finish()

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans.toList()).containsExactly(rootSpan)
        assertThat(writer).isEmpty()

        // When
        child.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(child, rootSpan)
        assertThat(writer.traceCount.get()).isEqualTo(1)
    }

    @Test
    fun `child spans created after trace written reported separately`() {
        // When
        rootSpan.finish()
        // this shouldn't happen, but it's possible users of the api
        // may incorrectly add spans after the trace is reported.
        // in those cases we should still decrement the pending trace count
        val childSpan = tracer.buildSpan(instrumentationName, "child").asChildOf(rootSpan.context()).start() as DDSpan
        childSpan.finish()
        writer.waitForTraces(2)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(rootSpan)
        assertThat(writer.secondTrace()).containsExactly(childSpan)
    }

    @Test
    fun `test getCurrentTimeNano`() {
        // Generous 5 seconds to execute this test
        assertThat(
            Math.abs(
                TimeUnit.NANOSECONDS.toSeconds(trace.currentTimeNano) -
                    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            )
        ).isLessThan(5)
    }

    @Test
    fun `partial flush`() {
        // Given
        val quickTracer = tracerBuilder().writer(writer).partialFlushMinSpans(1).build()
        val rootSpan = quickTracer.buildSpan(instrumentationName, "root").start() as DDSpan
        val trace = rootSpan.context().trace as PendingTrace
        val child1 = quickTracer.buildSpan(instrumentationName, "child1")
            .asChildOf(rootSpan.context()).start() as DDSpan
        val child2 = quickTracer.buildSpan(instrumentationName, "child2")
            .asChildOf(rootSpan.context()).start() as DDSpan

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(3)

        // When
        child2.finish()

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(2)
        assertThat(trace.spans.toList()).isEqualTo(listOf(child2))
        assertThat(writer).isEmpty()
        assertThat(writer.traceCount.get()).isEqualTo(0)

        // When
        child1.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans.toList()).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(child1, child2)
        assertThat(writer.traceCount.get()).isEqualTo(1)

        // When
        rootSpan.finish()
        writer.waitForTraces(2)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(child1, child2)
        assertThat(writer.secondTrace()).containsExactly(rootSpan)
        assertThat(writer.traceCount.get()).isEqualTo(2)

        // Tear down
        quickTracer.close()
    }

    @Test
    fun `partial flush with root span closed last`() {
        // Given
        val quickTracer = tracerBuilder().writer(writer).partialFlushMinSpans(1).build() as CoreTracer
        val rootSpan = quickTracer.buildSpan(instrumentationName, "root").start() as DDSpan
        val trace = rootSpan.context().trace
        val child1 = quickTracer
            .buildSpan(instrumentationName, "child1")
            .asChildOf(rootSpan.context())
            .start() as DDSpan
        val child2 = quickTracer
            .buildSpan(instrumentationName, "child2")
            .asChildOf(rootSpan.context())
            .start() as DDSpan

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(3)

        // When
        child1.finish()

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(2)
        assertThat(trace.spans.toList()).containsExactly(child1)
        assertThat(writer).isEmpty()
        assertThat(writer.traceCount.get()).isEqualTo(0)

        // When
        child2.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(child2, child1)
        assertThat(writer.traceCount.get()).isEqualTo(1)

        // When
        rootSpan.finish()
        writer.waitForTraces(2)

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(child2, child1)
        assertThat(writer.secondTrace()).containsExactly(rootSpan)
        assertThat(writer.traceCount.get()).isEqualTo(2)

        // Tear down
        quickTracer.close()
    }

    @ParameterizedTest
    @CsvSource(
        "1, 1",
        "2, 1",
        "1, 2",
        "5, 2000",
        "10, 1000",
        "50, 500"
    )
    fun `partial flush concurrency test`(threadCount: Int, spanCount: Int) {
        // Given
        val latch = CountDownLatch(1)
        val rootSpan = tracer.buildSpan("test", "root").start() as DDSpan
        val trace = rootSpan.context().trace as PendingTrace
        val exceptions = mutableListOf<Throwable>()
        val threads = (1..threadCount).map {
            thread(start = true) {
                try {
                    latch.await()
                    val spans = (1..spanCount).map {
                        tracer.startSpan("test", "child", rootSpan.context()) as DDSpan
                    }
                    spans.forEach {
                        it.finish()
                    }
                } catch (ex: Throwable) {
                    exceptions.add(ex)
                }
            }
        }

        // When
        // Finish root span so other spans are queued automatically
        rootSpan.finish()
        writer.waitForTraces(1)
        // resume the threads to finish their spans
        latch.countDown()
        threads.forEach {
            it.join()
        }
        trace.getFieldValue<PendingTraceBuffer, PendingTrace>("pendingTraceBuffer").flush()

        // Then
        assertThat(exceptions).isEmpty()
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        assertThat(writer.sumOf { it.size }).isEqualTo(threadCount * spanCount + 1)
    }
}

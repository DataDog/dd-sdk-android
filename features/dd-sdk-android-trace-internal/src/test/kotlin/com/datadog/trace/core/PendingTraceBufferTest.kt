/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.android.api.InternalLogger
import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.DDSpecification
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.api.time.SystemTimeSource
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopPathwayContext
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource
import com.datadog.trace.context.TraceScope
import com.datadog.trace.core.monitor.HealthMetrics
import com.datadog.trace.core.propagation.PropagationTags
import com.datadog.trace.core.scopemanager.ContinuableScopeManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Timeout(5)
internal class PendingTraceBufferTest : DDSpecification() {

    private val mockInternalLogger: InternalLogger = mock()
    private val buffer = PendingTraceBuffer.delaying(SystemTimeSource.INSTANCE, mock(), null)
    private val bufferSpy = spy(buffer)
    private val tracer = mock<CoreTracer>()
    private val traceConfig = mock<CoreTracer.ConfigSnapshot>()
    private val scopeManager = ContinuableScopeManager(10, true, true, mockInternalLogger)
    private val factory = PendingTrace.Factory(tracer, bufferSpy, SystemTimeSource.INSTANCE, false, HealthMetrics.NO_OP)
    private val continuations = mutableListOf<TraceScope.Continuation>()

    @BeforeEach
    override fun setup() {
        super.setup()
        whenever(tracer.captureTraceConfig()).thenReturn(traceConfig)
        whenever(traceConfig.serviceMapping).thenReturn(emptyMap())
    }

    @AfterEach
    override fun cleanup() {
        buffer.close()
        buffer.worker().join(1000)
        continuations.clear()
    }

    @Test
    fun `test buffer lifecycle`() {
        // Then
        assertThat(buffer.worker().isAlive).isFalse()

        // When
        buffer.start()

        // Then
        assertThat(buffer.worker().isAlive).isTrue()
        assertThat(buffer.worker().isDaemon).isTrue()
        assertThrows<IllegalThreadStateException> {
            buffer.start()
        }
        assertThat(buffer.worker().isAlive).isTrue()
        assertThat(buffer.worker().isDaemon).isTrue()

        // When
        buffer.close()
        buffer.worker().join(1000)

        // Then
        assertThat(buffer.worker().isAlive).isFalse()
    }

    @Test
    fun `continuation buffers root`() {
        // Given
        val trace = factory.create(DDTraceId.ONE)
        val span = newSpanOf(trace)

        // Then
        assertThat(trace.isRootSpanWritten).isFalse()

        // When
        addContinuation(span)
        span.finish() // This should enqueue

        // Then
        assertThat(continuations.size).isEqualTo(1)
        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        verify(bufferSpy, times(2)).longRunningSpansEnabled()
        verify(bufferSpy, times(1)).enqueue(trace)
        verify(tracer, times(1)).partialFlushMinSpans
        verify(tracer, times(2)).getTimeWithNanoTicks(any())

        // When
        continuations[0].cancel()

        // Then
        assertThat(trace.pendingReferenceCount).isEqualTo(0)
        argumentCaptor<List<DDSpan>>() {
            verify(tracer, times(1)).write(capture())
            assertThat(firstValue).containsExactly(span)
        }
        verify(tracer, times(1)).writeTimer()
        verify(tracer, times(2)).partialFlushMinSpans
    }

    private fun addContinuation(span: DDSpan) {
        val scope = scopeManager.activate(span, ScopeSource.INSTRUMENTATION, true)
        continuations.add(scope.capture())
        scope.close()
    }

    private fun newSpanOf(trace: PendingTrace): DDSpan {
        return newSpanOf(trace, PrioritySampling.UNSET.toInt())
    }

    private fun newSpanOf(trace: PendingTrace, samplingPriority: Int): DDSpan {
        val context = DDSpanContext(
            trace.traceId(),
            1,
            DDSpanId.ZERO,
            null,
            "fakeService",
            "fakeOperation",
            "fakeResource",
            samplingPriority,
            null,
            emptyMap(),
            false,
            "fakeType",
            0,
            trace,
            null,
            null,
            NoopPathwayContext.INSTANCE,
            false,
            PropagationTags.factory().empty()
        )
        return DDSpan.create("test", 0, context, null, mockInternalLogger)
    }

    private fun PendingTraceBuffer.worker(): Thread {
        return this.getFieldValue("worker")
    }

    private fun PendingTrace.traceId(): DDTraceId {
        return this.getFieldValue("traceId")
    }
}

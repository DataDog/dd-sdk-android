package com.datadog.trace.core

import com.datadog.tools.unit.setFieldValue
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.core.monitor.HealthMetrics
import com.datadog.trace.core.propagation.PropagationTags
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PendingTraceTest : PendingTraceTestBase() {

    override fun useStrictTraceWrites(): Boolean {
        // This tests the behavior of the relaxed pending trace implementation
        return false
    }

    @Test
    @Timeout(60)
    fun `trace is still reported when unfinished continuation discarded`() {
        val scope = tracer.activateSpan(rootSpan)
        scope.setAsyncPropagation(true)
        scope.capture()
        scope.close()
        rootSpan.finish()

        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans.toList()).containsExactly(rootSpan)
        assertThat(writer).isEmpty()

        writer.waitForTraces(1)

        assertThat(trace.pendingReferenceCount).isEqualTo(1)
        assertThat(trace.spans).isEmpty()
        assertThat(writer.firstTrace()).containsExactly(rootSpan)
        assertThat(writer.traceCount.get()).isEqualTo(1)
    }

    @Test
    fun `verify healthmetrics called`() {
        val tracer = mock<CoreTracer>()
        val traceConfig = mock<CoreTracer.ConfigSnapshot>()
        val buffer = mock<PendingTraceBuffer>()
        val healthMetrics = mock<HealthMetrics>()
        whenever(tracer.captureTraceConfig()).thenReturn(traceConfig)
        whenever(traceConfig.serviceMapping).thenReturn(emptyMap())
        val trace = PendingTrace(tracer, DDTraceId.from(0), buffer, mock(), null, false, healthMetrics)

        rootSpan = createSimpleSpan(trace)
        trace.registerSpan(rootSpan)

        verify(healthMetrics, times(1)).onCreateSpan()

        rootSpan.finish()

        verify(healthMetrics, times(1)).onCreateTrace()
    }

    @Test
    fun `write when writeRunningSpans is disabled only completed spans are written`() {
        val tracer = mock<CoreTracer>()
        val traceConfig = mock<CoreTracer.ConfigSnapshot>()
        val buffer = mock<PendingTraceBuffer>()
        val healthMetrics = mock<HealthMetrics>()
        whenever(tracer.captureTraceConfig()).thenReturn(traceConfig)
        whenever(traceConfig.serviceMapping).thenReturn(emptyMap())
        val trace = PendingTrace(tracer, DDTraceId.from(0), buffer, mock(), null, false, healthMetrics)
        whenever(buffer.longRunningSpansEnabled()).thenReturn(true)

        val span1 = createSimpleSpanWithID(trace, 39)
        span1.setFieldValue("durationNano", 31)
        @Suppress("DEPRECATION")
        span1.samplingPriority = PrioritySampling.USER_KEEP.toInt()
        trace.registerSpan(span1)

        val unfinishedSpan = createSimpleSpanWithID(trace, 191)
        trace.registerSpan(unfinishedSpan)

        val span2 = createSimpleSpanWithID(trace, 9999)
        span2.setFieldValue("durationNano", 9191)
        trace.registerSpan(span2)
        val traceToWrite = ArrayList<DDSpan>(0)

        val completedSpans = trace.enqueueSpansToWrite(traceToWrite, false)

        assertThat(completedSpans).isEqualTo(2)
        assertThat(traceToWrite.size).isEqualTo(2)
        assertThat(traceToWrite).containsExactlyInAnyOrder(span1, span2)
        assertThat(trace.spans.size).isEqualTo(1)
        assertThat(trace.spans.pop()).isEqualTo(unfinishedSpan)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `write when writeRunningSpans is enabled complete and running spans are written`() {
        val mockConfigSnapshot = mock<CoreTracer.ConfigSnapshot> {
            on { serviceMapping } doReturn emptyMap<String, String>()
        }
        val tracer = mock<CoreTracer> {
            on { captureTraceConfig() } doReturn mockConfigSnapshot
        }
        val buffer = mock<PendingTraceBuffer> {
            on { longRunningSpansEnabled() } doReturn true
        }
        val healthMetrics = mock<HealthMetrics>()
        val trace = PendingTrace(tracer, DDTraceId.from(0), buffer, mock(), null, false, healthMetrics)

        val span1 = createSimpleSpanWithID(trace, 39).apply {
            setFieldValue("durationNano", 31)
            samplingPriority = PrioritySampling.USER_KEEP.toInt()
        }
        trace.registerSpan(span1)

        val unfinishedSpan = createSimpleSpanWithID(trace, 191)
        trace.registerSpan(unfinishedSpan)

        val span2 = createSimpleSpanWithID(trace, 9999).apply {
            serviceName = "9191"
            setFieldValue("durationNano", 9191)
        }
        trace.registerSpan(span2)

        val unfinishedSpan2 = createSimpleSpanWithID(trace, 77771)
        trace.registerSpan(unfinishedSpan2)

        val traceToWrite = ArrayList<DDSpan>()

        val completedSpans = trace.enqueueSpansToWrite(traceToWrite, true)

        assertThat(completedSpans).isEqualTo(2)
        assertThat(traceToWrite).hasSize(4)
        assertThat(traceToWrite).containsExactlyInAnyOrder(span1, span2, unfinishedSpan, unfinishedSpan2)
        assertThat(trace.spans).hasSize(2)
        assertThat(trace.spans).containsExactlyInAnyOrder(unfinishedSpan, unfinishedSpan2)
    }

    private fun createSimpleSpan(trace: PendingTrace): DDSpan {
        return createSimpleSpanWithID(trace, 1)
    }

    private fun createSimpleSpanWithID(trace: PendingTrace, id: Long): DDSpan {
        return DDSpan(
            "test", 0L,
            DDSpanContext(
                DDTraceId.from(1),
                id,
                0,
                null,
                "",
                "",
                "",
                PrioritySampling.UNSET.toInt(),
                "",
                emptyMap(),
                false,
                "",
                0,
                trace,
                null,
                null,
                AgentTracer.NoopPathwayContext.INSTANCE,
                false,
                PropagationTags.factory().empty()
            ),
            null
        )
    }
}

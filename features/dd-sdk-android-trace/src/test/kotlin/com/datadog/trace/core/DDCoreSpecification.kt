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
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration
import com.datadog.trace.common.writer.ListWriter
import com.datadog.trace.core.CoreTracer.CoreTracerBuilder
import com.datadog.trace.core.propagation.PropagationTags
import com.datadog.trace.core.tagprocessor.TagsPostProcessorFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock

internal abstract class DDCoreSpecification : DDSpecification() {

    protected open fun useNoopStatsDClient() = true
    protected open fun useStrictTraceWrites() = true

    protected val instrumentationName = "test"

    protected val mockLogger: InternalLogger = mock()

    @BeforeEach
    override fun setup() {
        super.setup()
        TagsPostProcessorFactory.withAddBaseService(false)
    }

    @AfterEach
    override fun cleanup() {
        super.cleanup()
        TagsPostProcessorFactory.reset()
    }

    protected fun tracerBuilder(): CoreTracerBuilder {
        val builder = CoreTracerBuilder(mockLogger)
        return builder.strictTraceWrites(useStrictTraceWrites())
    }

    protected fun buildSpan(timestamp: Long, spanType: CharSequence, tags: Map<String, Any>): DDSpan {
        return buildSpan(
            timestamp,
            spanType,
            PropagationTags.factory().empty(),
            tags,
            PrioritySampling.SAMPLER_KEEP,
            null
        )
    }

    protected fun buildSpan(timestamp: Long, tag: String, value: String, propagationTags: PropagationTags): DDSpan {
        return buildSpan(
            timestamp,
            "fakeType",
            propagationTags,
            mapOf(tag to value),
            PrioritySampling.UNSET,
            null
        )
    }

    protected fun buildSpan(
        timestamp: Long,
        spanType: CharSequence,
        propagationTags: PropagationTags,
        tags: Map<String, Any>,
        prioritySampling: Byte,
        ciVisibilityContextData: Any?
    ): DDSpan {
        val tracer = tracerBuilder().writer(ListWriter()).build()
        val pendingTraceFactory: PendingTrace.Factory = tracer.getFieldValue("pendingTraceFactory")
        val context = DDSpanContext(
            DDTraceId.ONE,
            1,
            DDSpanId.ZERO,
            null,
            "fakeService",
            "fakeOperation",
            "fakeResource",
            prioritySampling.toInt(),
            null,
            emptyMap(),
            false,
            spanType,
            0,
            pendingTraceFactory.create(DDTraceId.ONE),
            null,
            null,
            ciVisibilityContextData,
            AgentTracer.NoopPathwayContext.INSTANCE,
            false,
            propagationTags,
            ProfilingContextIntegration.NoOp.INSTANCE,
            true
        )

        val span = DDSpan.create("test", timestamp, context, null, mockLogger)
        for ((key, value) in tags.entries) {
            span.setTag(key, value)
        }

        tracer.close()
        return span
    }
}

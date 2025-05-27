/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.trace

import com.datadog.android.core.sampling.Sampler
import com.datadog.legacy.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.SpanBuilder
import com.datadog.trace.core.CoreTracer.CoreSpanBuilder
import com.datadog.trace.core.propagation.ExtractedContext
import fr.xgouchet.elmyr.Forge
import okhttp3.Request
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

internal fun AgentPropagation.wheneverInjectThenThrow(throwable: Throwable) {
    doThrow(throwable)
        .whenever(this)
        .inject(any<Context>(), any<Request>(), any())
}

internal fun AgentPropagation.wheneverInjectThenValueToHeaders(key: String, value: String) {
    doAnswer { it.getArgument<Request.Builder>(2).addHeader(key, value) }
        .whenever(this)
        .inject(any<Context>(), any<Request>(), any())
}

internal fun AgentPropagation.wheneverInjectThenContextToHeaders(
    datadogContext: Map<String, String>,
    nonDatadogContextKey: String,
    nonDatadogContextKeyValue: String
) {
    doAnswer { invocation ->
        val carrier = invocation.getArgument<Request.Builder>(2)
        datadogContext.forEach { carrier.addHeader(it.key, it.value) }
        carrier.addHeader(nonDatadogContextKey, nonDatadogContextKeyValue)
    }
        .whenever(this)
        .inject(any<Context>(), any<Request>(), any())
}

internal fun Forge.aDDTraceId(fakeString: String? = null) = DDTraceId.from(
    BigInteger(fakeString ?: aStringMatching("[a-f0-9]{32}"), 16).toLong()
)
internal fun Forge.newTraceSamplerMock(span: Span = newSpanMock()) = mock<Sampler<Span>> {
    on { sample(span) } doReturn true
}

internal fun Forge.newTracerMock(
    spanBuilder: SpanBuilder = newSpanBuilderMock(),
    propagation: AgentPropagation = newAgentPropagationMock()
) = mock<Tracer> {
    on { buildSpan(TracingInterceptor.SPAN_NAME) } doReturn spanBuilder
    on { propagate() } doReturn propagation
}

internal fun newAgentPropagationMock(
    extractedContext: ExtractedContext = mock()
) = mock<AgentPropagation> {
    on { extract(any<Request>(), any()) } doReturn extractedContext
}

internal fun Forge.newSpanContextMock(
    fakeTraceId: DDTraceId = aDDTraceId(),
    fakeSpanId: Long = aLong()
) = mock<SpanContext> {
    on { spanId } doReturn fakeSpanId
    on { traceId } doReturn fakeTraceId
}

internal fun Forge.newSpanMock(
    context: SpanContext = newSpanContextMock()
) = mock<Span>(extraInterfaces = arrayOf(MutableSpan::class)) {
    on { context() } doReturn context
}

internal fun Forge.newSpanBuilderMock(
    localSpan: Span = newSpanMock()
) = mock<CoreSpanBuilder> {
    on { withOrigin(anyOrNull()) } doReturn it
    on { asChildOf(any<Context>()) } doReturn it
    on { start() } doReturn localSpan
}

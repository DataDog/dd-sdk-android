/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.trace

import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.impl.DatadogTracing
import fr.xgouchet.elmyr.Forge
import okhttp3.Request
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun newAgentPropagationMock(
    extractedContext: DatadogSpanContext = mock()
) = mock<DatadogPropagation> {
    on { extract(any<Request>(), any()) } doReturn extractedContext
}
internal fun DatadogPropagation.wheneverInjectThenThrow(throwable: Throwable) {
    doThrow(throwable)
        .whenever(this)
        .inject(any<DatadogSpanContext>(), any<Request.Builder>(), any())
}

internal fun DatadogPropagation.wheneverInjectThenValueToHeaders(key: String, value: String) {
    doAnswer { invocation ->
        val carrier = invocation.getArgument<Request.Builder>(1)
        val setter = invocation.getArgument<(carrier: Request.Builder, key: String, value: String) -> Unit>(2)
        setter.invoke(carrier, key, value)
    }
        .whenever(this)
        .inject(any<DatadogSpanContext>(), any<Request.Builder>(), any())
}

internal fun DatadogPropagation.wheneverInjectCalledPassContextToHeaders(
    datadogContext: Map<String, String>,
    nonDatadogContextKey: String,
    nonDatadogContextKeyValue: String
) {
    doAnswer { invocation ->
        val carrier = invocation.getArgument<Request.Builder>(1)
        val setter = invocation.getArgument<(carrier: Request.Builder, key: String, value: String) -> Unit>(2)
        datadogContext.forEach { setter.invoke(carrier, it.key, it.value) }
        setter.invoke(carrier, nonDatadogContextKey, nonDatadogContextKeyValue)
    }
        .whenever(this)
        .inject(any<DatadogSpanContext>(), any<Request.Builder>(), any())
}

internal fun Forge.aDatadogTraceId(
    fakeString: String? = null
) = DatadogTracing.traceIdFactory.fromHex(fakeString ?: aStringMatching("[a-f0-9]{31}"))

internal fun Forge.newTraceSamplerMock(
    span: DatadogSpan = newSpanMock()
) = mock<Sampler<DatadogSpan>> {
    on { sample(span) } doReturn true
}

internal fun Forge.newTracerMock(
    spanBuilder: DatadogSpanBuilder = newSpanBuilderMock(),
    propagation: DatadogPropagation = newAgentPropagationMock()
) = mock<DatadogTracer> {
    on { buildSpan(TracingInterceptor.SPAN_NAME) } doReturn spanBuilder
    on { propagate() } doReturn propagation
}

internal inline fun <reified T : DatadogSpanContext> Forge.newSpanContextMock(
    fakeTraceId: DatadogTraceId = aDatadogTraceId(),
    fakeSpanId: Long = aLong()
): T = mock<T> {
    on { spanId } doReturn fakeSpanId
    on { traceId } doReturn fakeTraceId
}

internal fun Forge.newSpanMock(
    context: DatadogSpanContext = newSpanContextMock(),
    samplingPriority: Int? = null
) = mock<DatadogSpan> {
    on { context() } doReturn context
    on { this.samplingPriority } doReturn samplingPriority
}

internal fun Forge.newSpanBuilderMock(
    localSpan: DatadogSpan = newSpanMock(),
    context: DatadogSpanContext = newSpanContextMock()
) = mock<DatadogSpanBuilder> {
    on { withOrigin(anyOrNull()) } doReturn it
    on { withParentContext(context) } doReturn it
    on { withParentContext(null as DatadogSpanContext?) } doReturn it
    on { start() } doReturn localSpan
}

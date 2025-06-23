/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("DEPRECATION")

package com.datadog.android.okhttp.trace

import com.datadog.android.core.sampling.Sampler
import com.datadog.legacy.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.SpanBuilder
import com.datadog.trace.core.CoreTracer.CoreSpanBuilder
import fr.xgouchet.elmyr.Forge
import okhttp3.Request
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun AgentPropagation.wheneverInjectThenThrow(throwable: Throwable) {
    doThrow(throwable)
        .whenever(this)
        .inject(any<Context>(), any<Request.Builder>(), any())
}

internal fun AgentPropagation.wheneverInjectThenValueToHeaders(key: String, value: String) {
    doAnswer { invocation ->
        val carrier = invocation.getArgument<Request.Builder>(1)
        val setter = invocation.getArgument<AgentPropagation.Setter<Request.Builder>>(2)
        setter.set(carrier, key, value)
    }
        .whenever(this)
        .inject(any<Context>(), any<Request.Builder>(), any())
}

internal fun AgentPropagation.wheneverInjectCalledPassContextToHeaders(
    datadogContext: Map<String, String>,
    nonDatadogContextKey: String,
    nonDatadogContextKeyValue: String
) {
    doAnswer { invocation ->
        val carrier = invocation.getArgument<Request.Builder>(1)
        val setter = invocation.getArgument<AgentPropagation.Setter<Request.Builder>>(2)
        datadogContext.forEach { setter.set(carrier, it.key, it.value) }
        setter.set(carrier, nonDatadogContextKey, nonDatadogContextKeyValue)
    }
        .whenever(this)
        .inject(any<Context>(), any<Request.Builder>(), any())
}

internal fun Forge.aDDTraceId(fakeString: String? = null) = DDTraceId.fromHex(
    fakeString ?: aStringMatching("[a-f0-9]{31}")
)
internal fun Forge.newTraceSamplerMock(span: AgentSpan = newSpanMock()) = mock<Sampler<AgentSpan>> {
    on { sample(span) } doReturn true
}

internal fun Forge.newTracerMock(
    spanBuilder: SpanBuilder = newSpanBuilderMock(),
    propagation: AgentPropagation = newAgentPropagationMock()
) = mock<AgentTracer.TracerAPI> {
    on { buildSpan(TracingInterceptor.SPAN_NAME) } doReturn spanBuilder
    on { propagate() } doReturn propagation
}

internal fun newAgentPropagationMock(
    extractedContext: Context.Extracted = mock()
) = mock<AgentPropagation> {
    on { extract(any<Request>(), any()) } doReturn extractedContext
}

internal inline fun <reified T : Context> Forge.newSpanContextMock(
    fakeTraceId: DDTraceId = aDDTraceId(),
    fakeSpanId: Long = aLong()
): T = mock<T> {
    on { spanId } doReturn fakeSpanId
    on { traceId } doReturn fakeTraceId
}

internal fun Forge.newSpanMock(
    context: AgentSpan.Context = newSpanContextMock()
) = mock<AgentSpan>(extraInterfaces = arrayOf(MutableSpan::class)) {
    on { context() } doReturn context
}

internal fun Forge.newSpanBuilderMock(
    localSpan: AgentSpan = newSpanMock(),
    context: Context = newSpanContextMock()
) = mock<CoreSpanBuilder> {
    on { withOrigin(anyOrNull()) } doReturn it
    on { asChildOf(context) } doReturn it
    on { asChildOf(null as Context?) } doReturn it
    on { start() } doReturn localSpan
}

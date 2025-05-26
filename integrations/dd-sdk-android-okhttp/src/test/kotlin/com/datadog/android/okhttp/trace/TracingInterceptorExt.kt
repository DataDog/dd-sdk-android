/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.trace

import com.datadog.legacy.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context
import com.datadog.trace.core.CoreTracer.CoreSpanBuilder
import okhttp3.Request
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

internal fun newFakeTraceId(hexString: String): DDTraceId {
    return DDTraceId.from(BigInteger(hexString, 16).toLong())
}

internal fun AgentPropagation.wheneverInjectThrow(throwable: Throwable) {
    doThrow(throwable)
        .whenever(this)
        .inject(any<Context>(), any<Request>(), any())
}

internal fun AgentPropagation.wheneverInjectPassKeyValueToHeaders(key: String, value: String) {
    doAnswer { it.getArgument<Request.Builder>(2).addHeader(key, value) }
        .whenever(this)
        .inject(any<Context>(), any<Request>(), any())
}

internal fun AgentPropagation.wheneverInjectPassContextToHeaders(
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

internal fun newSpanBuilderMock(
    localSpan: Span = mock(extraInterfaces = arrayOf(MutableSpan::class))
) = mock<CoreSpanBuilder> {
    on { withOrigin(anyOrNull()) } doReturn it
    on { asChildOf(any<Context>()) } doReturn it
    on { start() } doReturn localSpan
}


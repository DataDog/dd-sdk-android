/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.otel

import com.datadog.android.okhttp.TraceContext
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.trace.api.sampling.PrioritySampling
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class OkHttpExtTest {

    @StringForgery(regex = "[a-d0-9]{16}")
    internal lateinit var fakeSpanId: String

    @StringForgery(regex = "[a-d0-9]{32}")
    internal lateinit var fakeTraceId: String

    @BoolForgery
    internal var fakeIsSampled: Boolean = false

    @StringForgery(regex = "http://[a-z0-9_]{8}\\.[a-z]{3}/")
    internal lateinit var fakeUrl: String

    @Mock
    internal lateinit var mockSpan: Span

    private var expectedPrioritySampling: Int = 0

    @BeforeEach
    fun `set up`() {
        val spanContext: SpanContext = mock {
            on { spanId }.thenReturn(fakeSpanId)
            on { traceId }.thenReturn(fakeTraceId)
            on { isSampled }.thenReturn(fakeIsSampled)
        }
        expectedPrioritySampling =
            if (fakeIsSampled) PrioritySampling.USER_KEEP.toInt() else PrioritySampling.UNSET.toInt()
        whenever(mockSpan.spanContext).thenReturn(spanContext)
    }

    @Test
    fun `M set the parentSpan through the Request builder W addParentSpan`() {
        // When
        val request = Request.Builder().url(fakeUrl).addParentSpan(mockSpan).build()

        // Then
        val taggedContext = request.tag(TraceContext::class.java)
        assertThat(taggedContext).isNotNull()
        assertThat(taggedContext?.spanId).isEqualTo(fakeSpanId)
        assertThat(taggedContext?.traceId).isEqualTo(fakeTraceId)
        assertThat(taggedContext?.samplingPriority).isEqualTo(expectedPrioritySampling)
    }
}

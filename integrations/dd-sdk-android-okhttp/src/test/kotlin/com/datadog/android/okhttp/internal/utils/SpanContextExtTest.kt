/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.utils

import com.datadog.opentracing.DDSpanContext
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
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
import java.math.BigInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class SpanContextExtTest {

    @Mock
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockDDSpanContext: DDSpanContext

    @StringForgery(regex = "([a-f0-9]{32})")
    lateinit var expectedFakeTraceId: String

    private lateinit var fakeTraceIdAsBigInteger: BigInteger

    @BeforeEach
    fun `set up`() {
        fakeTraceIdAsBigInteger = BigInteger(expectedFakeTraceId, 16)
    }

    @Test
    fun `M return empty string W traceIdAsHexString() { spanContext is not DDSpanContext }`() {
        // When
        whenever(mockSpan.context()).thenReturn(mock<SpanContext>())

        // When
        val result = mockSpan.context().traceIdAsHexString()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty string W traceIdAsHexString() { ddSpanContext id is null}`() {
        // When
        whenever(mockSpan.context()).thenReturn(mockDDSpanContext)
        whenever(mockDDSpanContext.traceId).thenReturn(null)

        // When
        val result = mockSpan.context().traceIdAsHexString()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return the expected hexa padded string W traceIdAsHexString()`() {
        // When
        whenever(mockSpan.context()).thenReturn(mockDDSpanContext)
        whenever(mockDDSpanContext.traceId).thenReturn(fakeTraceIdAsBigInteger)

        // When
        val result = mockSpan.context().traceIdAsHexString()

        // Then
        assertThat(result).isEqualTo(expectedFakeTraceId)
    }
}

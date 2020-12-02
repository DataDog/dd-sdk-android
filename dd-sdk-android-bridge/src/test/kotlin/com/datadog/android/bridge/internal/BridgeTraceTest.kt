/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdTrace
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class BridgeTraceTest {

    lateinit var testedTrace: DdTrace

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpanContext: SpanContext

    @Mock
    lateinit var mockSpan: Span

    @StringForgery
    lateinit var fakeOperation: String

    @LongForgery(1000000000000, 2000000000000)
    var fakeTimestamp: Long = 0L

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery()]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.HEXADECIMAL)])
    )
    lateinit var fakeContext: Map<String, String>

    @BeforeEach
    fun `set up`() {
        whenever(mockTracer.buildSpan(fakeOperation)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.withStartTimestamp(fakeTimestamp * 1000)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId

        GlobalTracer.registerIfAbsent(mockTracer)

        testedTrace = BridgeTrace()
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer.get().setFieldValue("isRegistered", false)
        GlobalTracer::class.java.setStaticValue("tracer", NoopTracerFactory.create())
    }

    @Test
    fun `M start a span W startSpan() `() {
        // When
        val id = testedTrace.startSpan(fakeOperation, fakeTimestamp, emptyMap())

        // Then
        assertThat(id).isEqualTo(fakeSpanId)
    }

    @Test
    fun `M start and stop span W startSpan() + finishSpan()`(
        @LongForgery(100, 2000) duration: Long,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery()]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.HEXADECIMAL)])
        ) context: Map<String, String>
    ) {
        // Given
        val endTimestamp = fakeTimestamp + duration

        // When
        val id = testedTrace.startSpan(fakeOperation, fakeTimestamp, emptyMap())
        testedTrace.finishSpan(id, endTimestamp, emptyMap())

        // Then
        assertThat(id).isEqualTo(fakeSpanId)
        verify(mockSpan).finish(endTimestamp * 1000)
    }

    @Test
    fun `M do nothing W startSpan() + finishSpan() with unknown id`(
        @LongForgery(100, 2000) duration: Long,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) otherSpanId: String
    ) {
        // Given
        assumeTrue(otherSpanId != fakeSpanId)
        val endTimestamp = fakeTimestamp + duration

        // When
        val id = testedTrace.startSpan(fakeOperation, fakeTimestamp, emptyMap())
        testedTrace.finishSpan(otherSpanId, endTimestamp, emptyMap())

        // Then
        assertThat(id).isEqualTo(fakeSpanId)
        verify(mockSpan, never()).finish(any())
    }

    @Test
    fun `M start and stop span with context on start W startSpan() + finishSpan()`(
        @LongForgery(100, 2000) duration: Long
    ) {
        // Given
        val endTimestamp = fakeTimestamp + duration

        // When
        val id = testedTrace.startSpan(fakeOperation, fakeTimestamp, fakeContext)
        testedTrace.finishSpan(id, endTimestamp, emptyMap())

        // Then
        assertThat(id).isEqualTo(fakeSpanId)
        verify(mockSpan).context()
        verify(mockSpan).finish(endTimestamp * 1000)
        fakeContext.forEach {
            verify(mockSpan).setTag(it.key, it.value)
        }
        verifyNoMoreInteractions(mockSpan)
    }

    @Test
    fun `M start and stop span with context on finish W startSpan() + finishSpan()`(
        @LongForgery(100, 2000) duration: Long
    ) {
        // Given
        val endTimestamp = fakeTimestamp + duration

        // When
        val id = testedTrace.startSpan(fakeOperation, fakeTimestamp, emptyMap())
        testedTrace.finishSpan(id, endTimestamp, fakeContext)

        // Then
        assertThat(id).isEqualTo(fakeSpanId)
        verify(mockSpan).context()
        verify(mockSpan).finish(endTimestamp * 1000)
        fakeContext.forEach {
            verify(mockSpan).setTag(it.key, it.value)
        }
        verifyNoMoreInteractions(mockSpan)
    }
}

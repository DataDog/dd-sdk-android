/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.core.DDSpan
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogSpanAdapterTest {

    @Mock
    lateinit var mockAgentSpan: AgentSpan

    @Mock
    lateinit var mockSpanLogger: DatadogSpanLogger

    @Mock
    lateinit var mockDDTraceId: DDTraceId

    @Mock
    lateinit var mockDDSpan: DDSpan

    @Mock
    lateinit var mockSpanContext: AgentSpan.Context

    @IntForgery
    private var fakeInt: Int = 0

    @BoolForgery
    private var fakeBool: Boolean = false

    @StringForgery
    lateinit var fakeString: String

    @LongForgery
    private var fakeLong: Long = 0L

    @Forgery
    lateinit var fakeThrowable: Throwable

    private lateinit var testedSpanAdapter: DatadogSpanAdapter
    private lateinit var testedSpanAdapterWithDDSpanDelegate: DatadogSpanAdapter

    @BeforeEach
    fun `set up`() {
        whenever(mockAgentSpan.context()).thenReturn(mockSpanContext)
        whenever(mockAgentSpan.traceId).thenReturn(mockDDTraceId)
        testedSpanAdapter = DatadogSpanAdapter(mockAgentSpan, mockSpanLogger)
        testedSpanAdapterWithDDSpanDelegate = DatadogSpanAdapter(mockDDSpan, mockSpanLogger)
    }

    @Test
    fun `M delegate drop W drop is called`() {
        // When
        testedSpanAdapter.drop()

        // Then
        verify(mockAgentSpan).drop()
    }

    @Test
    fun `M delegate finish W finish() is called`() {
        // When
        testedSpanAdapter.finish()

        // Then
        verify(mockAgentSpan).finish()
    }

    @Test
    fun `M delegate finish W finish(millis) is called`() {
        // When
        testedSpanAdapter.finish(fakeLong)

        // Then
        verify(mockAgentSpan).finish(fakeLong)
    }

    @Test
    fun `M delegate setTag(String, Number) W drop is called`() {
        // When
        testedSpanAdapter.setTag(fakeString, fakeLong)

        // Then
        verify(mockAgentSpan).setTag(fakeString, fakeLong as? Number)
    }

    @Test
    fun `M delegate setTag(String, String) W drop is called`() {
        // When
        testedSpanAdapter.setTag(fakeString, fakeString)

        // Then
        verify(mockAgentSpan).setTag(fakeString, fakeString)
    }

    @Test
    fun `M delegate setTag(String, Object) W drop is called`() {
        // Given
        val value = Any()

        // When
        testedSpanAdapter.setTag(fakeString, value)

        // Then
        verify(mockAgentSpan).setTag(fakeString, value)
    }

    @Test
    fun `M delegate setMetric(String, Int) W drop is called`() {
        // When
        testedSpanAdapter.setMetric(fakeString, fakeInt)

        // Then
        verify(mockAgentSpan).setMetric(fakeString, fakeInt)
    }

    @Test
    fun `M delegate setErrorMessage(String) W drop is called`() {
        // When
        testedSpanAdapter.setErrorMessage(fakeString)

        // Then
        verify(mockAgentSpan).setErrorMessage(fakeString)
    }

    @Test
    fun `M delegate addThrowable(Throwable) W drop is called`() {
        // When
        testedSpanAdapter.addThrowable(fakeThrowable)

        // Then
        verify(mockAgentSpan).addThrowable(fakeThrowable)
    }

    @Test
    fun `M delegate addThrowable(Throwable, Byte) W drop is called`() {
        // Given
        val errorPriority = fakeInt.toByte()

        // When
        testedSpanAdapter.addThrowable(fakeThrowable, errorPriority)

        // Then
        verify(mockAgentSpan).addThrowable(fakeThrowable, errorPriority)
    }

    @Test
    fun `M return delegate#isError W isError is set`() {
        // When
        testedSpanAdapter.isError = fakeBool

        // Then
        verify(mockAgentSpan).setError(fakeBool)
    }

    @Test
    fun `M return delegate#isError W isError is get`() {
        // Given
        whenever(mockAgentSpan.isError) doReturn fakeBool

        // When
        val error = testedSpanAdapter.isError

        // Then
        assertThat(error).isEqualTo(fakeBool)
    }

    @Test
    fun `M return false W isRootSpan {delegate is not DDSpan}`() {
        // When
        val rootSpan = testedSpanAdapter.isRootSpan

        // Then
        assertThat(rootSpan).isFalse()
    }

    @Test
    fun `M return return delegate#isRootSpan W isRootSpan {delegate is DDSpan}`() {
        // Given
        whenever(testedSpanAdapterWithDDSpanDelegate.isRootSpan).thenReturn(fakeBool)

        // When
        val actual = testedSpanAdapterWithDDSpanDelegate.isRootSpan

        // Then
        assertThat(actual).isEqualTo(fakeBool)
    }

    @Test
    fun `M return DatadogContext instance W context() is called`() {
        // When
        val context = testedSpanAdapter.context()

        // Then
        assertThat(context).isInstanceOf(DatadogSpanContext::class.java)
    }

    @Test
    fun `M return DatadogTraceId instance W traceId is called`() {
        // When
        val traceId = testedSpanAdapter.traceId

        // Then
        assertThat(traceId).isInstanceOf(DatadogTraceId::class.java)
    }

    @Test
    fun `M return delegate#parentSpanId W parentSpanId is called`() {
        // Given
        whenever(mockDDSpan.parentId).thenReturn(fakeLong)

        // When
        val actual = testedSpanAdapterWithDDSpanDelegate.parentSpanId

        // Then
        assertThat(actual).isEqualTo(fakeLong)
    }

    @Test
    fun `M return delegate#samplingPriority W samplingPriority is called`() {
        // Given
        whenever(mockAgentSpan.samplingPriority).thenReturn(fakeInt)

        // When
        val actual = testedSpanAdapter.samplingPriority

        // Then
        assertThat(actual).isEqualTo(fakeInt)
    }

    @Test
    fun `M return delegate#durationNano W durationNano is called`() {
        // Given
        whenever(mockAgentSpan.durationNano).thenReturn(fakeLong)

        // When
        val actual = testedSpanAdapter.durationNano

        // Then
        assertThat(actual).isEqualTo(fakeLong)
    }

    @Test
    fun `M return delegate#startTimeNano W startTimeNano is called`() {
        // Given
        whenever(mockAgentSpan.startTime).thenReturn(fakeLong)

        // When
        val startTimeNano = testedSpanAdapter.startTimeNanos

        // Then
        assertThat(startTimeNano).isEqualTo(fakeLong)
    }

    @Test
    fun `M return DatadogContext instance W localRootSpan is called`() {
        // Given
        val expected = mock<AgentSpan>()
        whenever(mockAgentSpan.localRootSpan).thenReturn(expected)

        // When
        val actual = testedSpanAdapter.localRootSpan

        // Then
        assertThat(actual).isInstanceOf(DatadogSpan::class.java)
    }
}

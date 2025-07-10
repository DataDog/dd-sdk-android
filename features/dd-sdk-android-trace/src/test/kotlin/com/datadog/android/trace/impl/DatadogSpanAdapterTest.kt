/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.impl.internal.DatadogSpanAdapter
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
class DatadogSpanAdapterTest {

    @Mock
    lateinit var mockAgentSpan: AgentSpan

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
        testedSpanAdapter = DatadogSpanAdapter(mockAgentSpan)
        testedSpanAdapterWithDDSpanDelegate = DatadogSpanAdapter(mockDDSpan)
    }

    @Test
    fun `M delegate drop W drop is called`() {
        testedSpanAdapter.drop()

        verify(mockAgentSpan).drop()
    }

    @Test
    fun `M delegate finish W finish() is called`() {
        testedSpanAdapter.finish()

        verify(mockAgentSpan).finish()
    }

    @Test
    fun `M delegate finish W finish(millis) is called`() {
        testedSpanAdapter.finish(fakeLong)

        verify(mockAgentSpan).finish(fakeLong)
    }

    @Test
    fun `M delegate setTag(String, Number) W drop is called`() {
        testedSpanAdapter.setTag(fakeString, fakeLong)

        verify(mockAgentSpan).setTag(fakeString as String?, fakeLong as Number?)
    }

    @Test
    fun `M delegate setTag(String, String) W drop is called`() {
        testedSpanAdapter.setTag(fakeString, fakeString)

        verify(mockAgentSpan).setTag(fakeString, fakeString)
    }

    @Test
    fun `M delegate setTag(String, Object) W drop is called`() {
        val value = Any()
        testedSpanAdapter.setTag(fakeString, value)

        verify(mockAgentSpan).setTag(fakeString, value)
    }

    @Test
    fun `M delegate setMetric(String, Int) W drop is called`() {
        testedSpanAdapter.setMetric(fakeString, fakeInt)

        verify(mockAgentSpan).setMetric(fakeString as CharSequence, fakeInt)
    }

    @Test
    fun `M delegate setErrorMessage(String) W drop is called`() {
        testedSpanAdapter.setErrorMessage(fakeString)

        verify(mockAgentSpan).setErrorMessage(fakeString)
    }

    @Test
    fun `M delegate addThrowable(Throwable) W drop is called`() {
        testedSpanAdapter.addThrowable(fakeThrowable)

        verify(mockAgentSpan).addThrowable(fakeThrowable)
    }

    @Test
    fun `M delegate addThrowable(Throwable, Byte) W drop is called`() {
        val errorPriority = fakeInt.toByte()

        testedSpanAdapter.addThrowable(fakeThrowable, errorPriority)

        verify(mockAgentSpan).addThrowable(fakeThrowable, errorPriority)
    }

    @Test
    fun `M return delegate#isError W isError is set`() {
        testedSpanAdapter.isError = fakeBool

        verify(mockAgentSpan).setError(fakeBool)
    }

    @Test
    fun `M return delegate#isError W isError is get`() {
        whenever(mockAgentSpan.isError) doReturn fakeBool

        assertThat(testedSpanAdapter.isError).isEqualTo(fakeBool)
    }

    @Test
    fun `M return false W isRootSpan {delegate is not DDSpan}`() {
        assertThat(testedSpanAdapter.isRootSpan).isFalse()
    }

    @Test
    fun `M return return delegate#isRootSpan W isRootSpan {delegate is DDSpan}`() {
        whenever(testedSpanAdapterWithDDSpanDelegate.isRootSpan).thenReturn(fakeBool)

        assertThat(testedSpanAdapterWithDDSpanDelegate.isRootSpan).isEqualTo(fakeBool)
    }

    @Test
    fun `M return DatadogContext instance W context() is called`() {
        val context = testedSpanAdapter.context()

        assertThat(context).isInstanceOf(DatadogSpanContext::class.java)
    }

    @Test
    fun `M return DatadogTraceId instance W traceId is called`() {
        assertThat(testedSpanAdapter.traceId).isInstanceOf(DatadogTraceId::class.java)
    }

    @Test
    fun `M return delegate#parentSpanId W parentSpanId is called`() {
        whenever(mockDDSpan.parentId).thenReturn(fakeLong)

        assertThat(testedSpanAdapterWithDDSpanDelegate.parentSpanId).isEqualTo(fakeLong)
    }

    @Test
    fun `M return delegate#samplingPriority W samplingPriority is called`() {
        whenever(mockAgentSpan.samplingPriority).thenReturn(fakeInt)

        assertThat(testedSpanAdapter.samplingPriority).isEqualTo(fakeInt)
    }

    @Test
    fun `M return delegate#durationNano W durationNano is called`() {
        whenever(mockAgentSpan.durationNano).thenReturn(fakeLong)

        assertThat(testedSpanAdapter.durationNano).isEqualTo(fakeLong)
    }

    @Test
    fun `M return delegate#startTimeNano W startTimeNano is called`() {
        whenever(mockAgentSpan.startTime).thenReturn(fakeLong)

        assertThat(testedSpanAdapter.startTimeNano).isEqualTo(fakeLong)
    }

    @Test
    fun `M return DatadogContext instance W localRootSpan is called`() {
        val expected = mock<AgentSpan>()
        whenever(mockAgentSpan.localRootSpan).thenReturn(expected)

        assertThat(testedSpanAdapter.localRootSpan).isInstanceOf(DatadogSpan::class.java)
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.clear
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
class SpanExtTest {

    @Mock
    lateinit var mockTracer: DatadogTracer

    @Mock
    lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Mock
    lateinit var mockParentSpan: DatadogSpan

    @Mock
    lateinit var mockScope: DatadogScope

    @StringForgery
    lateinit var fakeOperationName: String

    @BeforeEach
    fun `set up`() {
        GlobalDatadogTracerHolder.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(fakeOperationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracerHolder.clear()
    }

    @Test
    fun `M create span around lambda W withinSpan(name){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        val callResult = withinSpan(fakeOperationName) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span and scope around lambda W withinSpan(name, parent){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false

        val callResult = withinSpan(fakeOperationName, mockParentSpan) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span and scope around lambda W withinSpan(name, parent){} throwing error`(
        @Forgery throwable: Throwable
    ) {
        var lambdaCalled = false

        val thrown = assertThrows<Throwable> {
            withinSpan(fakeOperationName, mockParentSpan) {
                lambdaCalled = true
                throw throwable
            }
        }

        assertThat(thrown).isEqualTo(throwable)
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).logThrowable(throwable)
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around lambda W withinSpan(name, parent, false){}`(
        @LongForgery result: Long
    ) {
        var lambdaCalled = false

        val callResult = withinSpan(fakeOperationName, mockParentSpan, false) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan) {
            verify(mockSpan).finish()
        }
        verify(mockTracer, never()).activateSpan(mockSpan)
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.coroutines

import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.clear
import com.datadog.android.trace.api.tracer.DatadogTracer
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
class CoroutineExtTest {

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
        whenever(mockSpanBuilder.start()) doReturn mockSpan
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracerHolder.clear()
    }

    @Test
    fun `M create span around launched coroutine W launchTraced()`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        MainScope().launchTraced(
            fakeOperationName,
            Dispatchers.IO
        ) {
            lambdaCalled = true
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around launched coroutine W launchTraced() without parents`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        // When
        MainScope().launchTraced(
            fakeOperationName,
            Dispatchers.IO
        ) {
            lambdaCalled = true
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around launched coroutine W launchTraced() with Unconfined dispatcher`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        MainScope().launchTraced(
            fakeOperationName,
            Dispatchers.Unconfined
        ) {
            lambdaCalled = true
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W withContextTraced()`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            withContextTraced(
                fakeOperationName,
                Dispatchers.IO
            ) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W withContextTraced() without parents`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        // When
        runBlocking {
            withContextTraced(
                fakeOperationName,
                Dispatchers.IO
            ) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W withContextTraced() with Unconfined dispatcher`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            withContextTraced(
                fakeOperationName,
                Dispatchers.Unconfined
            ) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W runBlockingTraced()`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlockingTraced(fakeOperationName, Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W runBlockingTraced() without parents`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        // When
        runBlockingTraced(fakeOperationName, Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W runBlockingTraced() with Unconfined dispatcher`() {
        // Given
        var lambdaCalled = false
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlockingTraced(fakeOperationName, Dispatchers.Unconfined) {
            withContext(Dispatchers.Default) {
                lambdaCalled = true
            }
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W asyncTraced()`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = asyncTraced(
                fakeOperationName,
                Dispatchers.IO
            ) {
                lambdaCalled = true
                data
            }
            result = deferred.await()
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W asyncTraced() without parents`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = asyncTraced(
                fakeOperationName,
                Dispatchers.IO
            ) {
                lambdaCalled = true
                data
            }
            result = deferred.await()
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M create span around coroutine W asyncTraced() with Unconfined dispatcher`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = asyncTraced(
                fakeOperationName,
                Dispatchers.Unconfined
            ) {
                lambdaCalled = true
                data
            }

            result = deferred.await()
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W awaitTraced()`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = async(Dispatchers.IO) {
                lambdaCalled = true
                data
            }
            result = deferred.awaitTraced(fakeOperationName)
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W awaitTraced() without parents`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.withParentSpan(null as DatadogSpan?)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = async(Dispatchers.IO) {
                lambdaCalled = true
                data
            }
            result = deferred.awaitTraced(fakeOperationName)
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(null as DatadogSpan?)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }

    @Test
    fun `M create span around coroutine W awaitTraced() with Unconfined dispatcher`(
        @StringForgery data: String
    ) {
        // Given
        var lambdaCalled = false
        var result: String? = null
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.withParentSpan(mockParentSpan)) doReturn mockSpanBuilder

        // When
        runBlocking {
            val deferred = async(Dispatchers.Unconfined) {
                lambdaCalled = true
                data
            }

            result = deferred.awaitTraced(fakeOperationName)
        }
        Thread.sleep(100)

        // Then
        assertThat(lambdaCalled).isTrue()
        assertThat(result).isEqualTo(data)
        verify(mockSpanBuilder).withParentSpan(mockParentSpan)
        verify(mockTracer, never()).activateSpan(mockSpan)
        verify(mockSpan).finish()
    }
}

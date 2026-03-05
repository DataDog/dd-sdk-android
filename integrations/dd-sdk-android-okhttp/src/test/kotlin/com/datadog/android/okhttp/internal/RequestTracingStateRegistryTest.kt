/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.tag
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Request
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RequestTracingStateRegistryTest {

    private lateinit var testedRegistry: RequestTracingStateRegistry

    @Mock
    lateinit var mockCall: Call

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        whenever(mockCall.request()) doReturn fakeRequest
        testedRegistry = RequestTracingStateRegistry(mockInternalLogger)
    }

    @Test
    fun `M return registered info W register() + get()`() {
        // When
        testedRegistry.register(mockCall)
        val result = testedRegistry.get(mockCall)

        // Then
        val requestInfo = checkNotNull(result).createRequestInfo()
        assertThat(requestInfo).isInstanceOf(OkHttpRequestInfo::class.java)
        assertThat(requestInfo.url).isEqualTo(fakeUrl)
    }

    @Test
    fun `M add UUID tag W register()`() {
        // When
        testedRegistry.register(mockCall)
        val result = testedRegistry.get(mockCall)

        // Then
        val requestInfo = checkNotNull(result).createRequestInfo()
        assertThat(requestInfo.tag(UUID::class.java)).isNotNull
    }

    @Test
    fun `M return null W get() { not registered }`() {
        // When
        val result = testedRegistry.get(mockCall)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return info and remove W remove()`() {
        // Given
        testedRegistry.register(mockCall)

        // When
        val result = testedRegistry.remove(mockCall)

        // Then
        val requestInfo = checkNotNull(result).createRequestInfo()
        assertThat(requestInfo.url).isEqualTo(fakeUrl)
        assertThat(testedRegistry.get(mockCall)).isNull()
    }

    @Test
    fun `M return null W remove() { not registered }`() {
        // When
        val result = testedRegistry.remove(mockCall)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M handle multiple calls W register() + get() { different calls }`() {
        // Given
        val fakeUrl2 = "https://other.com/path"
        val fakeRequest2 = Request.Builder().url(fakeUrl2).build()
        val mockCall2 = mock<Call> {
            on { request() } doReturn fakeRequest2
        }

        // When
        testedRegistry.register(mockCall)
        testedRegistry.register(mockCall2)

        // Then
        val requestInfo1 = checkNotNull(testedRegistry.get(mockCall)).createRequestInfo()
        assertThat(requestInfo1.url).isEqualTo(fakeUrl)

        val requestInfo2 = checkNotNull(testedRegistry.get(mockCall2)).createRequestInfo()
        assertThat(requestInfo2.url).isEqualTo(fakeUrl2)
    }

    @Test
    fun `M not affect other calls W remove()`() {
        // Given
        val fakeUrl2 = "https://other.com/path"
        val fakeRequest2 = Request.Builder().url(fakeUrl2).build()
        val mockCall2 = mock<Call> {
            on { request() } doReturn fakeRequest2
        }
        testedRegistry.register(mockCall)
        testedRegistry.register(mockCall2)

        // When
        testedRegistry.remove(mockCall)

        // Then
        assertThat(testedRegistry.get(mockCall)).isNull()
        assertThat(testedRegistry.get(mockCall2)).isNotNull
    }

    @Test
    fun `M return merged state W setTracingState() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val newRequestBuilder = OkHttpRequestInfoBuilder(
            Request.Builder().url(fakeUrl)
                .addHeader("x-trace", "123")
        )
        val newState = RequestTracingState(
            requestInfoBuilder = newRequestBuilder,
            isSampled = true
        )

        // When
        val result = testedRegistry.setTracingState(mockCall, newState)

        // Then
        assertThat(result).isNotNull
    }

    @Test
    fun `M return null W setTracingState() { call not registered }`() {
        // Given
        val newState = RequestTracingState(
            requestInfoBuilder = OkHttpRequestInfoBuilder(
                Request.Builder().url(fakeUrl)
            ),
            isSampled = true
        )

        // When
        val result = testedRegistry.setTracingState(mockCall, newState)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M preserve UUID tag W setTracingState() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val originalUuid = testedRegistry.get(mockCall)!!
            .createRequestInfo().tag(UUID::class.java)

        val newRequestBuilder = OkHttpRequestInfoBuilder(
            Request.Builder().url(fakeUrl).addHeader("x-trace", "123")
        )
        val newState = RequestTracingState(
            requestInfoBuilder = newRequestBuilder,
            isSampled = true
        )

        // When
        val result = testedRegistry.setTracingState(mockCall, newState)

        // Then
        val resultUuid = result!!.createRequestInfo().tag(UUID::class.java)
        assertThat(resultUuid).isEqualTo(originalUuid)
    }

    @Test
    fun `M update stored state W setTracingState() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val newRequestBuilder = OkHttpRequestInfoBuilder(
            Request.Builder().url(fakeUrl).addHeader("x-trace", "123")
        )
        val newState = RequestTracingState(
            requestInfoBuilder = newRequestBuilder,
            isSampled = true
        )

        // When
        testedRegistry.setTracingState(mockCall, newState)

        // Then
        val stored = testedRegistry.get(mockCall)
        assertThat(stored).isNotNull
        assertThat(stored).isNotSameAs(newState)
    }

    @Test
    fun `M return tagged request W restoreUUIDTag() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val newRequest = Request.Builder().url(fakeUrl)
            .addHeader("x-custom", "value")
            .build()

        // When
        val result = testedRegistry.restoreUUIDTag(mockCall, newRequest)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.header("x-custom")).isEqualTo("value")
        assertThat(result.tag(UUID::class.java)).isNotNull
    }

    @Test
    fun `M return null W restoreUUIDTag() { call not registered }`() {
        // When
        val result = testedRegistry.restoreUUIDTag(
            mockCall,
            Request.Builder().url(fakeUrl).build()
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M log warning and not register W register() { max tracked calls reached }`() {
        // Given
        repeat(RequestTracingStateRegistry.MAX_TRACKED_CALLS) { index ->
            val request = Request.Builder().url("https://host$index.com/path").build()
            val call = mock<Call> {
                on { request() } doReturn request
            }
            testedRegistry.register(call)
        }

        // When
        testedRegistry.register(mockCall)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RequestTracingStateRegistry.WARNING_MAX_TRACKED_CALLS,
            onlyOnce = true
        )
        assertThat(testedRegistry.get(mockCall)).isNull()
    }

    @Test
    fun `M not log warning W register() { below max tracked calls }`() {
        // When
        testedRegistry.register(mockCall)

        // Then
        verifyNoInteractions(mockInternalLogger)
        assertThat(testedRegistry.get(mockCall)).isNotNull
    }

    @Test
    fun `M preserve span and sampling fields W setTracingState() { call registered }`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        testedRegistry.register(mockCall)
        val originalUuid = checkNotNull(testedRegistry.get(mockCall))
            .createRequestInfo().tag(UUID::class.java)

        val mockSpan = mock<DatadogSpan>()
        val newRequestBuilder = OkHttpRequestInfoBuilder(
            Request.Builder()
                .url(fakeUrl)
                .addHeader("x-trace", "123")
        )
        val newState = RequestTracingState(
            requestInfoBuilder = newRequestBuilder,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate
        )

        // When
        val result = testedRegistry.setTracingState(mockCall, newState)

        // Then
        checkNotNull(result).apply {
            assertThat(span).isSameAs(mockSpan)
            assertThat(isSampled).isTrue()
            assertThat(sampleRate).isEqualTo(fakeSampleRate)
            assertThat(createRequestInfo().tag(UUID::class.java)).isEqualTo(originalUuid)
        }

        checkNotNull(testedRegistry.get(mockCall)).apply {
            assertThat(span).isSameAs(mockSpan)
            assertThat(isSampled).isTrue()
            assertThat(sampleRate).isEqualTo(fakeSampleRate)
            assertThat(createRequestInfo().tag(UUID::class.java)).isEqualTo(originalUuid)
        }
    }
}

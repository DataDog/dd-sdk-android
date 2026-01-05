/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.tag
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.tools.unit.forge.BaseConfigurator
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RumInstrumentationHttpRequestInfoRegistryTest {

    private lateinit var testedRegistry: RequestTracingStateRegistry

    @Mock
    lateinit var mockCall: Call

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    @BeforeEach
    fun `set up`() {
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        whenever(mockCall.request()) doReturn fakeRequest
        testedRegistry = RequestTracingStateRegistry()
    }

    @Test
    fun `M return registered info W register() + get()`() {
        // When
        testedRegistry.register(mockCall)
        val result = testedRegistry.get(mockCall)

        // Then
        val requestInfo = checkNotNull(result).createModifiedRequestInfo()
        assertThat(requestInfo).isInstanceOf(OkHttpRequestInfo::class.java)
        assertThat(requestInfo.url).isEqualTo(fakeUrl)
    }

    @Test
    fun `M add UUID tag W register()`() {
        // When
        testedRegistry.register(mockCall)
        val result = testedRegistry.get(mockCall)

        // Then
        assertThat(checkNotNull(result).createModifiedRequestInfo().tag(UUID::class.java)).isNotNull
    }

    @Test
    fun `M return null W get() { not registered }`() {
        // When
        val result = testedRegistry.get(mockCall)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return info and remove W unregister()`() {
        // Given
        testedRegistry.register(mockCall)

        // When
        val result = testedRegistry.remove(mockCall)

        // Then
        assertThat(checkNotNull(result).createModifiedRequestInfo().url).isEqualTo(fakeUrl)
        assertThat(testedRegistry.get(mockCall)).isNull()
    }

    @Test
    fun `M return null W unregister() { not registered }`() {
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
        assertThat(checkNotNull(testedRegistry.get(mockCall)).createModifiedRequestInfo().url).isEqualTo(fakeUrl)
        assertThat(checkNotNull(testedRegistry.get(mockCall2)).createModifiedRequestInfo().url).isEqualTo(fakeUrl2)
    }

    @Test
    fun `M not affect other calls W unregister()`() {
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
    fun `M apply block and return result W update() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val mockRequestBuilder = mock<HttpRequestInfoBuilder>()
        val updatedState = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )

        // When
        val result = testedRegistry.update(mockCall) { _, _ -> updatedState }

        // Then
        assertThat(result).isSameAs(updatedState)
    }

    @Test
    fun `M return null W update() { call not registered }`() {
        // When
        val result = testedRegistry.update(mockCall) { _, _ ->
            RequestTracingState(
                tracedRequestInfoBuilder = mock(),
                isSampled = true
            )
        }

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M receive current state in block W update() { call registered }`() {
        // Given
        testedRegistry.register(mockCall)
        val originalState = testedRegistry.get(mockCall)
        var receivedState: RequestTracingState? = null

        // When
        testedRegistry.update(mockCall) { _, state ->
            receivedState = state
            state
        }

        // Then
        assertThat(receivedState).isSameAs(originalState)
    }

    @Test
    fun `M update stored state W update() { block returns new state }`() {
        // Given
        testedRegistry.register(mockCall)
        val mockRequestBuilder = mock<HttpRequestInfoBuilder>()
        val updatedState = RequestTracingState(
            tracedRequestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )

        // When
        testedRegistry.update(mockCall) { _, _ -> updatedState }

        // Then
        assertThat(testedRegistry.get(mockCall)).isSameAs(updatedState)
    }

    @Test
    fun `M remove entry W update() { block returns null }`() {
        // Given
        testedRegistry.register(mockCall)

        // When
        val result = testedRegistry.update(mockCall) { _, _ -> null }

        // Then
        assertThat(result).isNull()
        assertThat(testedRegistry.get(mockCall)).isNull()
    }
}

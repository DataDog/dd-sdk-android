/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.api.instrumentation.network.RequestInfoAssertions
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogUrlRequestBuilderTest {

    @Mock
    lateinit var mockRequest: UrlRequest

    @Mock
    lateinit var mockDelegate: UrlRequest.Builder

    @Mock
    lateinit var mockRumResourceInstrumentation: RumResourceInstrumentation

    lateinit var fakeUrl: String

    lateinit var testedBuilder: DatadogUrlRequestBuilder

    @BeforeEach
    fun setup(forge: Forge) {
        fakeUrl = forge.aStringMatching("https://[a-z0-9]+\\.com")
        testedBuilder = DatadogUrlRequestBuilder(
            url = fakeUrl,
            delegate = mockDelegate,
            rumResourceInstrumentation = mockRumResourceInstrumentation
        )

        whenever(mockDelegate.build()).thenReturn(mockRequest)
    }

    @Test
    fun `M delegate to builder W setHttpMethod()`(
        @StringForgery method: String
    ) {
        // Given
        whenever(mockDelegate.setHttpMethod(method)).thenReturn(mockDelegate)

        // When
        testedBuilder.setHttpMethod(method)

        // Then
        verify(mockDelegate).setHttpMethod(method)
    }

    @Test
    fun `M delegate to builder W addHeader()`(
        @StringForgery header: String,
        @StringForgery value: String
    ) {
        // Given
        whenever(mockDelegate.addHeader(header, value)).thenReturn(mockDelegate)

        // When
        testedBuilder.addHeader(header, value)

        // Then
        verify(mockDelegate).addHeader(header, value)
    }

    @Test
    fun `M delegate to builder W disableCache()`() {
        // Given
        whenever(mockDelegate.disableCache()).thenReturn(mockDelegate)

        // When
        testedBuilder.disableCache()

        // Then
        verify(mockDelegate).disableCache()
    }

    @Test
    fun `M delegate to builder W setPriority()`(
        @IntForgery priority: Int
    ) {
        // Given
        whenever(mockDelegate.setPriority(priority)).thenReturn(mockDelegate)

        // When
        testedBuilder.setPriority(priority)

        // Then
        verify(mockDelegate).setPriority(priority)
    }

    @Test
    fun `M delegate to builder W setUploadDataProvider()`() {
        // Given
        val mockUploadDataProvider = mock<UploadDataProvider>()
        val mockExecutor = mock<Executor>()
        whenever(mockDelegate.setUploadDataProvider(mockUploadDataProvider, mockExecutor))
            .thenReturn(mockDelegate)

        // When
        testedBuilder.setUploadDataProvider(mockUploadDataProvider, mockExecutor)

        // Then
        verify(mockDelegate).setUploadDataProvider(mockUploadDataProvider, mockExecutor)
    }

    @Test
    fun `M delegate to builder W allowDirectExecutor()`() {
        // Given
        whenever(mockDelegate.allowDirectExecutor()).thenReturn(mockDelegate)

        // When
        testedBuilder.allowDirectExecutor()

        // Then
        verify(mockDelegate).allowDirectExecutor()
    }

    @Test
    fun `M delegate to builder W addRequestAnnotation()`() {
        // Given
        val mockAnnotation = mock<Any>()
        whenever(mockDelegate.addRequestAnnotation(mockAnnotation)).thenReturn(mockDelegate)

        // When
        testedBuilder.addRequestAnnotation(mockAnnotation)

        // Then
        verify(mockDelegate).addRequestAnnotation(mockAnnotation)
    }

    @Test
    fun `M delegate to builder W bindToNetwork()`(
        @LongForgery networkHandle: Long
    ) {
        // Given
        whenever(mockDelegate.bindToNetwork(networkHandle)).thenReturn(mockDelegate)

        // When
        testedBuilder.bindToNetwork(networkHandle)

        // Then
        verify(mockDelegate).bindToNetwork(networkHandle)
    }

    @Test
    fun `M delegate to builder W setTrafficStatsTag()`(
        @IntForgery tag: Int
    ) {
        // Given
        whenever(mockDelegate.setTrafficStatsTag(tag)).thenReturn(mockDelegate)

        // When
        testedBuilder.setTrafficStatsTag(tag)

        // Then
        verify(mockDelegate).setTrafficStatsTag(tag)
    }

    @Test
    fun `M delegate to builder W setTrafficStatsUid()`(
        @IntForgery uid: Int
    ) {
        // Given
        whenever(mockDelegate.setTrafficStatsUid(uid)).thenReturn(mockDelegate)

        // When
        testedBuilder.setTrafficStatsUid(uid)

        // Then
        verify(mockDelegate).setTrafficStatsUid(uid)
    }

    @Test
    fun `M delegate to builder W setRequestFinishedListener()`() {
        // Given
        val mockListener = mock<RequestFinishedInfo.Listener>()
        whenever(mockDelegate.setRequestFinishedListener(mockListener)).thenReturn(mockDelegate)

        // When
        testedBuilder.setRequestFinishedListener(mockListener)

        // Then
        verify(mockDelegate).setRequestFinishedListener(mockListener)
    }

    @Test
    fun `M delegate to builder W setRawCompressionDictionary()`(
        @StringForgery dictionaryId: String,
        forge: Forge
    ) {
        // Given
        val hash = ByteArray(5) { forge.anInt().toByte() }
        val mockDictionary = mock<ByteBuffer>()
        whenever(mockDelegate.setRawCompressionDictionary(hash, mockDictionary, dictionaryId))
            .thenReturn(mockDelegate)

        // When
        testedBuilder.setRawCompressionDictionary(hash, mockDictionary, dictionaryId)

        // Then
        verify(mockDelegate).setRawCompressionDictionary(hash, mockDictionary, dictionaryId)
    }

    @Test
    fun `M return DatadogUrlRequest W build()`() {
        // Given
        val mockUrlRequest = mock<UrlRequest>()
        whenever(mockDelegate.build()).thenReturn(mockUrlRequest)

        // When
        val result = testedBuilder.build()

        // Then
        verify(mockDelegate).build()
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M addAnnotation W build`(
        @StringForgery key: String,
        @StringForgery value: String,
        @StringForgery tag: String,
    ) {
        // When
        testedBuilder.setHttpMethod(HttpSpec.Method.POST)
            .addHeader(key, value)
            .addHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_GRPC_JSON)
            .addRequestAnnotation(tag)
            .build()

        // Then
        verify(mockDelegate).addRequestAnnotation(any<RequestInfo>())
        argumentCaptor<RequestInfo> {
            verify(mockDelegate).addRequestAnnotation(capture())
            RequestInfoAssertions.assertThat(firstValue)
                .hasUrl(fakeUrl)
                .hasHeader(key, value)
                .hasHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_GRPC_JSON)
                .hasContentType(HttpSpec.ContentType.APPLICATION_GRPC_JSON)
                .hasTag(String::class.java, tag)
                .hasMethod(HttpSpec.Method.POST)
        }
    }
}

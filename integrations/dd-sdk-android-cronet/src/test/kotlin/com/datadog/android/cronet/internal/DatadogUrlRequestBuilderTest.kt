/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.RequestInfoAssert
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.cronet.DatadogCronetEngine
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
import org.mockito.kotlin.mock
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
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: DatadogRequestCallback

    @Mock
    lateinit var mockExecutor: Executor

    lateinit var fakeUrl: String

    lateinit var requestContext: DatadogCronetRequestContext

    lateinit var testedBuilder: DatadogUrlRequestBuilder

    @BeforeEach
    fun setup(forge: Forge) {
        fakeUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/[a-z]+")
        requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        )
        testedBuilder = DatadogUrlRequestBuilder(
            cronetInstrumentationStateHolder = mockCallback,
            requestContext = requestContext
        )

        whenever(mockDelegate.build()).thenReturn(mockRequest)
    }

    @Test
    fun `M store method locally W setHttpMethod()`(
        @StringForgery method: String
    ) {
        // When
        testedBuilder.setHttpMethod(method)

        // Then
        val requestInfo = requestContext.buildRequestInfo()
        assertThat(requestInfo.method).isEqualTo(method)
    }

    @Test
    fun `M store header locally W addHeader()`(
        @StringForgery header: String,
        @StringForgery value: String
    ) {
        // When
        testedBuilder.addHeader(header, value)

        // Then
        val requestInfo = requestContext.buildRequestInfo()
        assertThat(requestInfo.headers[header]).contains(value)
    }

    @Test
    fun `M return DatadogUrlRequest W build()`() {
        // When
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M create request info with annotations W build`(
        @StringForgery key: String,
        @StringForgery value: String,
        @StringForgery tag: String
    ) {
        // When
        testedBuilder.setHttpMethod(HttpSpec.Method.POST)
            .addHeader(key, value)
            .addHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_GRPC_JSON)
            .addRequestAnnotation(tag)

        val requestInfo = requestContext.buildRequestInfo()

        // Then
        RequestInfoAssert.assertThat(requestInfo)
            .hasUrl(fakeUrl)
            .hasHeader(key, value)
            .hasHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_GRPC_JSON)
            .hasContentType(HttpSpec.ContentType.APPLICATION_GRPC_JSON)
            .hasTag(String::class.java, tag)
            .hasMethod(HttpSpec.Method.POST)
    }

    @Test
    fun `M store priority W setPriority()`(
        @IntForgery priority: Int
    ) {
        // When
        testedBuilder.setPriority(priority)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store uploadDataProvider W setUploadDataProvider()`() {
        // Given
        val mockUploadDataProvider = mock<UploadDataProvider>()
        val mockUploadExecutor = mock<Executor>()

        // When
        testedBuilder.setUploadDataProvider(mockUploadDataProvider, mockUploadExecutor)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store disableCache W disableCache()`() {
        // When
        testedBuilder.disableCache()
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store allowDirectExecutor W allowDirectExecutor()`() {
        // When
        testedBuilder.allowDirectExecutor()
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store annotation W addRequestAnnotation()`() {
        // Given
        val mockAnnotation = mock<Any>()

        // When
        testedBuilder.addRequestAnnotation(mockAnnotation)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store networkHandle W bindToNetwork()`(
        @LongForgery networkHandle: Long
    ) {
        // When
        testedBuilder.bindToNetwork(networkHandle)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store trafficStatsTag W setTrafficStatsTag()`(
        @IntForgery tag: Int
    ) {
        // When
        testedBuilder.setTrafficStatsTag(tag)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store trafficStatsUid W setTrafficStatsUid()`(
        @IntForgery uid: Int
    ) {
        // When
        testedBuilder.setTrafficStatsUid(uid)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store requestFinishedListener W setRequestFinishedListener()`() {
        // Given
        val mockListener = mock<RequestFinishedInfo.Listener>()

        // When
        testedBuilder.setRequestFinishedListener(mockListener)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }

    @Test
    fun `M store rawCompressionDictionary W setRawCompressionDictionary()`(
        @StringForgery dictionaryId: String,
        forge: Forge
    ) {
        // Given
        val hash = ByteArray(5) { forge.anInt().toByte() }
        val mockDictionary = mock<ByteBuffer>()

        // When
        testedBuilder.setRawCompressionDictionary(hash, mockDictionary, dictionaryId)
        val result = testedBuilder.build()

        // Then
        assertThat(result).isInstanceOf(DatadogUrlRequest::class.java)
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.cronet.DatadogCronetEngine
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.internal.net.RequestTraceState
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
import org.mockito.kotlin.doReturn
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
@ForgeConfiguration(Configurator::class)
internal class DatadogCronetRequestContextTest {

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: DatadogRequestCallback

    @Mock
    lateinit var mockExecutor: Executor

    @Mock
    lateinit var mockDelegateBuilder: UrlRequest.Builder

    @Mock
    lateinit var mockUrlRequest: UrlRequest

    @Mock
    lateinit var mockTracingState: RequestTraceState

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    lateinit var testedContext: DatadogCronetRequestContext

    @BeforeEach
    fun `set up`() {
        whenever(mockEngine.newDelegateUrlRequestBuilder(any(), any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.build()) doReturn mockUrlRequest
        whenever(mockDelegateBuilder.setHttpMethod(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.addHeader(any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.disableCache()) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.allowDirectExecutor()) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setPriority(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.bindToNetwork(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setTrafficStatsTag(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setTrafficStatsUid(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setUploadDataProvider(any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setRequestFinishedListener(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setRawCompressionDictionary(any(), any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.addRequestAnnotation(any())) doReturn mockDelegateBuilder

        testedContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        )
    }

    @Test
    fun `M store url W initialization`() {
        // When/Then
        assertThat(testedContext.url).isEqualTo(fakeUrl)
    }

    @Test
    fun `M use GET as default method W initialization`() {
        // Then
        assertThat(testedContext.method).isEqualTo(HttpSpec.Method.GET)
    }

    @Test
    fun `M update method W setHttpMethod()`() {
        // Given
        val newMethod = HttpSpec.Method.POST

        // When
        testedContext.setHttpMethod(newMethod)

        // Then
        assertThat(testedContext.method).isEqualTo(newMethod)
    }

    @Test
    fun `M add single header W addHeader() { single value }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String
    ) {
        // When
        testedContext.addHeader(headerKey, headerValue)

        // Then
        assertThat(testedContext.headers[headerKey]).containsExactly(headerValue)
    }

    @Test
    fun `M add multiple values W addHeader() { multiple values }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue1: String,
        @StringForgery headerValue2: String,
        @StringForgery headerValue3: String
    ) {
        // When
        testedContext.addHeader(headerKey, headerValue1, headerValue2, headerValue3)

        // Then
        assertThat(testedContext.headers[headerKey])
            .containsExactly(headerValue1, headerValue2, headerValue3)
    }

    @Test
    fun `M append values W addHeader() { called multiple times }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue1: String,
        @StringForgery headerValue2: String
    ) {
        // When
        testedContext.addHeader(headerKey, headerValue1)
        testedContext.addHeader(headerKey, headerValue2)

        // Then
        assertThat(testedContext.headers[headerKey]).containsExactly(headerValue1, headerValue2)
    }

    @Test
    fun `M remove header W removeHeader()`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String
    ) {
        // Given
        testedContext.addHeader(headerKey, headerValue)

        // When
        testedContext.removeHeader(headerKey)

        // Then
        assertThat(testedContext.headers[headerKey]).isNull()
    }

    @Test
    fun `M not affect other headers W removeHeader()`(
        @StringForgery headerKey1: String,
        @StringForgery headerValue1: String,
        @StringForgery headerKey2: String,
        @StringForgery headerValue2: String
    ) {
        // Given
        testedContext.addHeader(headerKey1, headerValue1)
        testedContext.addHeader(headerKey2, headerValue2)

        // When
        testedContext.removeHeader(headerKey1)

        // Then
        assertThat(testedContext.headers[headerKey1]).isNull()
        assertThat(testedContext.headers[headerKey2]).containsExactly(headerValue2)
    }

    @Test
    fun `M return empty map W headers { no headers added }`() {
        // Then
        assertThat(testedContext.headers).isEmpty()
    }

    @Test
    fun `M store tag W setTag()`(
        @StringForgery fakeTag: String
    ) {
        // When
        testedContext.setTag(String::class.java, fakeTag)

        // Then
        assertThat(testedContext.annotations).contains(fakeTag)
    }

    @Test
    fun `M remove tag W setTag() { null value }`(
        @StringForgery fakeTag: String
    ) {
        // Given
        testedContext.setTag(String::class.java, fakeTag)

        // When
        testedContext.setTag(String::class.java, null)

        // Then
        assertThat(testedContext.annotations).doesNotContain(fakeTag)
    }

    @Test
    fun `M store multiple tags W setTag() { different types }`(
        @StringForgery fakeStringTag: String,
        @IntForgery fakeIntTag: Int
    ) {
        // When
        testedContext.setTag(String::class.java, fakeStringTag)
        testedContext.setTag(Int::class.java, fakeIntTag)

        // Then
        assertThat(testedContext.annotations).contains(fakeStringTag)
        assertThat(testedContext.annotations).contains(fakeIntTag)
    }

    @Test
    fun `M return empty list W annotations { no tags added }`() {
        // Then
        assertThat(testedContext.annotations).isEmpty()
    }

    @Test
    fun `M add annotation W addRequestAnnotation()`(
        @StringForgery fakeAnnotation: String
    ) {
        // When
        testedContext.addRequestAnnotation(fakeAnnotation)

        // Then
        assertThat(testedContext.annotations).contains(fakeAnnotation)
    }

    @Test
    fun `M store uploadDataProvider W setUploadDataProvider()`() {
        // Given
        val mockUploadProvider = mock<UploadDataProvider>()
        val mockUploadExecutor = mock<Executor>()

        // When
        testedContext.setUploadDataProvider(mockUploadProvider, mockUploadExecutor)

        // Then
        assertThat(testedContext.uploadDataProvider).isSameAs(mockUploadProvider)
    }

    @Test
    fun `M return null W uploadDataProvider { not set }`() {
        // Then
        assertThat(testedContext.uploadDataProvider).isNull()
    }

    @Test
    fun `M create independent copy W copy()`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String,
        @StringForgery tagValue: String
    ) {
        // Given
        testedContext.addHeader(headerKey, headerValue)
        testedContext.setTag(String::class.java, tagValue)

        // When
        val copiedContext = testedContext.copy()

        // Then
        assertThat(copiedContext.url).isEqualTo(testedContext.url)
        assertThat(copiedContext.method).isEqualTo(testedContext.method)
        assertThat(copiedContext.headers).isEqualTo(testedContext.headers)
    }

    @Test
    fun `M not affect original W copy() + modify copy`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String,
        @StringForgery newHeaderKey: String,
        @StringForgery newHeaderValue: String
    ) {
        // Given
        testedContext.addHeader(headerKey, headerValue)
        val copiedContext = testedContext.copy()

        // When
        copiedContext.addHeader(newHeaderKey, newHeaderValue)

        // Then
        assertThat(testedContext.headers[newHeaderKey]).isNull()
        assertThat(copiedContext.headers[newHeaderKey]).containsExactly(newHeaderValue)
    }

    @Test
    fun `M build CronetHttpRequestInfo W buildRequestInfo()`() {
        // When
        val requestInfo = testedContext.buildRequestInfo()

        // Then
        assertThat(requestInfo).isInstanceOf(CronetHttpRequestInfo::class.java)
        assertThat(requestInfo.url).isEqualTo(fakeUrl)
        assertThat(requestInfo.method).isEqualTo(HttpSpec.Method.GET)
    }

    @Test
    fun `M build UrlRequest W buildCronetRequest()`() {
        // Given
        val requestInfo = testedContext.buildRequestInfo()

        // When
        val request = testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockEngine).newDelegateUrlRequestBuilder(fakeUrl, mockCallback, mockExecutor)
        verify(mockDelegateBuilder).build()
        assertThat(request).isSameAs(mockUrlRequest)
    }

    @Test
    fun `M add requestInfo as annotation W buildCronetRequest()`() {
        // Given
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).addRequestAnnotation(requestInfo)
    }

    @Test
    fun `M add tracingState as annotation W buildCronetRequest() { tracingState not null }`() {
        // Given
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).addRequestAnnotation(mockTracingState)
    }

    @Test
    fun `M apply disableCache W buildCronetRequest() { cache disabled }`() {
        // Given
        testedContext.disableCache()
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).disableCache()
    }

    @Test
    fun `M apply allowDirectExecutor W buildCronetRequest() { direct executor allowed }`() {
        // Given
        testedContext.allowDirectExecutor()
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).allowDirectExecutor()
    }

    @Test
    fun `M apply priority W buildCronetRequest() { priority set }`(
        @IntForgery fakePriority: Int
    ) {
        // Given
        testedContext.setPriority(fakePriority)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setPriority(fakePriority)
    }

    @Test
    fun `M apply networkHandle W buildCronetRequest() { network bound }`(
        @LongForgery fakeNetworkHandle: Long
    ) {
        // Given
        testedContext.bindToNetwork(fakeNetworkHandle)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).bindToNetwork(fakeNetworkHandle)
    }

    @Test
    fun `M apply trafficStatsTag W buildCronetRequest() { traffic stats tag set }`(
        @IntForgery fakeTag: Int
    ) {
        // Given
        testedContext.setTrafficStatsTag(fakeTag)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setTrafficStatsTag(fakeTag)
    }

    @Test
    fun `M apply trafficStatsUid W buildCronetRequest() { traffic stats uid set }`(
        @IntForgery fakeUid: Int
    ) {
        // Given
        testedContext.setTrafficStatsUid(fakeUid)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setTrafficStatsUid(fakeUid)
    }

    @Test
    fun `M apply uploadDataProvider W buildCronetRequest() { upload data provider set }`() {
        // Given
        val mockUploadProvider = mock<UploadDataProvider>()
        val mockUploadExecutor = mock<Executor>()
        testedContext.setUploadDataProvider(mockUploadProvider, mockUploadExecutor)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setUploadDataProvider(mockUploadProvider, mockUploadExecutor)
    }

    @Test
    fun `M apply requestFinishedListener W buildCronetRequest() { listener set }`() {
        // Given
        val mockListener = mock<RequestFinishedInfo.Listener>()
        testedContext.setRequestFinishedListener(mockListener)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setRequestFinishedListener(mockListener)
    }

    @Test
    fun `M apply rawCompressionDictionary W buildCronetRequest() { dictionary set }`(
        @StringForgery fakeDictionaryId: String,
        forge: Forge
    ) {
        // Given
        val fakeHash = ByteArray(5) { forge.anInt().toByte() }
        val mockDictionary = mock<ByteBuffer>()
        testedContext.setRawCompressionDictionary(fakeHash, mockDictionary, fakeDictionaryId)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).setRawCompressionDictionary(fakeHash, mockDictionary, fakeDictionaryId)
    }

    @Test
    fun `M return networkTracingInstrumentation W networkTracingInstrumentation { tracing enabled }`() {
        // Given
        val mockTracingInstrumentation = mock<NetworkTracingInstrumentation>()
        whenever(mockEngine.networkTracingInstrumentation) doReturn mockTracingInstrumentation

        // When
        val result = testedContext.networkTracingInstrumentation

        // Then
        assertThat(result).isSameAs(mockTracingInstrumentation)
    }

    @Test
    fun `M return null W networkTracingInstrumentation { tracing disabled }`() {
        // Given
        whenever(mockEngine.networkTracingInstrumentation) doReturn null

        // When
        val result = testedContext.networkTracingInstrumentation

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return rumResourceInstrumentation W rumResourceInstrumentation { rum enabled }`() {
        // Given
        val mockRumInstrumentation = mock<RumResourceInstrumentation>()
        whenever(mockEngine.rumResourceInstrumentation) doReturn mockRumInstrumentation

        // When
        val result = testedContext.rumResourceInstrumentation

        // Then
        assertThat(result).isSameAs(mockRumInstrumentation)
    }

    @Test
    fun `M return null W rumResourceInstrumentation { rum disabled }`() {
        // Given
        whenever(mockEngine.rumResourceInstrumentation) doReturn null

        // When
        val result = testedContext.rumResourceInstrumentation

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M update url W url setter`(
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") newUrl: String
    ) {
        // When
        testedContext.url = newUrl

        // Then
        assertThat(testedContext.url).isEqualTo(newUrl)
    }

    @Test
    fun `M apply additional annotations W buildCronetRequest() { annotations added }`(
        @StringForgery fakeAnnotation: String
    ) {
        // Given
        testedContext.addRequestAnnotation(fakeAnnotation)
        val requestInfo = testedContext.buildRequestInfo()

        // When
        testedContext.buildCronetRequest(requestInfo, mockTracingState)

        // Then
        verify(mockDelegateBuilder).addRequestAnnotation(fakeAnnotation)
    }
}

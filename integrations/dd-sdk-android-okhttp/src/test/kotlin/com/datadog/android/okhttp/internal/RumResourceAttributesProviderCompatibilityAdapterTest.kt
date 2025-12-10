/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.core.SdkReference
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@Suppress("DEPRECATION")
@ForgeConfiguration(BaseConfigurator::class)
internal class RumResourceAttributesProviderCompatibilityAdapterTest {

    @Mock
    private lateinit var mockDelegate: RumResourceAttributesProvider

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockOkHttpRequest: Request

    @Mock
    private lateinit var mockOkHttpResponse: Response

    private lateinit var requestInfo: HttpRequestInfo

    private lateinit var responseInfo: HttpResponseInfo

    @Mock
    private lateinit var mockSdkReference: SdkReference

    private lateinit var fakeAttributes: Map<String, Any?>

    private var fakeThrowable: Throwable? = null

    private lateinit var testedProvider: RumResourceAttributesProviderCompatibilityAdapter

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAttributes = forge.exhaustiveAttributes()
        fakeThrowable = forge.aNullable { forge.aThrowable() }
        testedProvider = RumResourceAttributesProviderCompatibilityAdapter(mockDelegate, mockSdkReference)
        requestInfo = OkHttpHttpRequestInfo(mockOkHttpRequest)
        responseInfo = OkHttpHttpResponseInfo(mockOkHttpResponse, mockInternalLogger)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `M return attributes from deprecated method W onProvideAttributes(OkHttp) { deprecated returns attributes }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(fakeAttributes)

        // When
        val attributes = testedProvider.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)

        // Then
        assertThat(attributes).isEqualTo(fakeAttributes)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M return attributes from new method W onProvideAttributes(OkHttp) { deprecated - empty, new - no empty }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())
        whenever(
            mockDelegate.onProvideAttributes(
                any<OkHttpHttpRequestInfo>(),
                anyOrNull<OkHttpHttpResponseInfo>(),
                anyOrNull<Throwable>()
            )
        ).thenReturn(fakeAttributes)

        // When
        val attributes = testedProvider.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)

        // Then
        assertThat(attributes).isEqualTo(fakeAttributes)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verify(mockDelegate).onProvideAttributes(
            any<OkHttpHttpRequestInfo>(),
            anyOrNull<OkHttpHttpResponseInfo>(),
            anyOrNull<Throwable>()
        )
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M return empty attributes W onProvideAttributes(OkHttp) { deprecated returns empty, new returns empty }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())

        whenever(
            mockDelegate.onProvideAttributes(
                any<OkHttpHttpRequestInfo>(),
                anyOrNull<OkHttpHttpResponseInfo>(),
                anyOrNull<Throwable>()
            )
        ).thenReturn(emptyMap())

        // When
        val attributes = testedProvider.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)

        // Then
        assertThat(attributes).isEmpty()
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verify(mockDelegate).onProvideAttributes(
            any<OkHttpHttpRequestInfo>(),
            anyOrNull<OkHttpHttpResponseInfo>(),
            anyOrNull<Throwable>()
        )
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `M return attributes from new method W onProvideAttributes(RequestInfo) { new returns attributes }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(requestInfo, responseInfo, fakeThrowable))
            .thenReturn(fakeAttributes)

        // When
        val attributes = testedProvider.onProvideAttributes(
            requestInfo,
            responseInfo,
            fakeThrowable
        )

        // Then
        assertThat(attributes).isEqualTo(fakeAttributes)
        verify(mockDelegate).onProvideAttributes(requestInfo, responseInfo, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M return attributes from old method W onProvideAttributes(RequestInfo){ new - empty, old - attributes }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(requestInfo, responseInfo, fakeThrowable))
            .thenReturn(emptyMap())
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(fakeAttributes)

        // When
        val attributes = testedProvider.onProvideAttributes(
            requestInfo,
            responseInfo,
            fakeThrowable
        )

        // Then
        assertThat(attributes).isEqualTo(fakeAttributes)
        verify(mockDelegate).onProvideAttributes(requestInfo, responseInfo, fakeThrowable)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M empty attributes W onProvideAttributes(RequestInfo) { new returns empty, old returns empty }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(requestInfo, responseInfo, fakeThrowable))
            .thenReturn(emptyMap())
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())

        // When
        val attributes = testedProvider.onProvideAttributes(
            requestInfo,
            responseInfo,
            fakeThrowable
        )

        // Then
        assertThat(attributes).isEmpty()
        verify(mockDelegate).onProvideAttributes(requestInfo, responseInfo, fakeThrowable)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M use internalLogger from sdkReference W onProvideAttributes(OkHttp) { reference is ready }`() {
        // Given
        val mockFeatureSdkCore = mock<FeatureSdkCore> {
            on { internalLogger } doReturn mockInternalLogger
        }
        whenever(mockSdkReference.get()).thenReturn(mockFeatureSdkCore)
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())

        argumentCaptor<OkHttpHttpResponseInfo> {
            // When
            testedProvider.onProvideAttributes(
                mockOkHttpRequest,
                mockOkHttpResponse,
                fakeThrowable
            )

            // Then
            verify(mockDelegate).onProvideAttributes(
                any<HttpRequestInfo>(),
                capture(),
                anyOrNull<Throwable>()
            )

            assertThat(firstValue.internalLogger).isSameAs(mockInternalLogger)
        }
    }

    @Test
    fun `M use UNBOUND logger W onProvideAttributes(OkHttp) { reference is not ready }`() {
        // Given
        whenever(mockSdkReference.get()).thenReturn(null)
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())

        argumentCaptor<OkHttpHttpResponseInfo> {
            // When
            testedProvider.onProvideAttributes(
                mockOkHttpRequest,
                mockOkHttpResponse,
                fakeThrowable
            )

            // Then
            verify(mockDelegate).onProvideAttributes(
                any<HttpRequestInfo>(),
                capture(),
                anyOrNull<Throwable>()
            )
            assertThat(firstValue.internalLogger).isSameAs(InternalLogger.UNBOUND)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `M not construct HttpHttpResponseInfo if response is null W onProvideAttributes(OkHttp)`() {
        // Given
        whenever(mockSdkReference.get()).thenReturn(null)
        whenever(mockDelegate.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable))
            .thenReturn(emptyMap())

        // When
        testedProvider.onProvideAttributes(
            mockOkHttpRequest,
            null,
            fakeThrowable
        )

        // Then
        verify(mockDelegate).onProvideAttributes(
            any<HttpRequestInfo>(),
            eq(null),
            anyOrNull<Throwable>()
        )
    }
}

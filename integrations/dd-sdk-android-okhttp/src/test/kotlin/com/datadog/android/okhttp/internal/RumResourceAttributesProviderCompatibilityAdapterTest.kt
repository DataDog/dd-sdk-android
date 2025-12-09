/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.api.instrumentation.network.ResponseInfo
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
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
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
    private lateinit var mockOkHttpRequest: Request

    @Mock
    private lateinit var mockOkHttpResponse: Response

    @Mock
    private lateinit var mockRequestInfo: RequestInfo

    @Mock
    private lateinit var mockResponseInfo: ResponseInfo

    private var fakeThrowable: Throwable? = null

    private lateinit var testedProvider: RumResourceAttributesProviderCompatibilityAdapter

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeThrowable = forge.aNullable { forge.aThrowable() }
        testedProvider = RumResourceAttributesProviderCompatibilityAdapter(mockDelegate)
    }

    @Test
    fun `M delegate W onProvideAttributes { OkHttp }`(forge: Forge) {
        // Given
        val expectedAttributes = forge.exhaustiveAttributes()
        whenever(
            mockDelegate.onProvideAttributes(
                mockOkHttpRequest,
                mockOkHttpResponse,
                fakeThrowable
            )
        )
            .doReturn(expectedAttributes)

        // When
        val result = testedProvider.onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)

        // Then
        Assertions.assertThat(result).isEqualTo(expectedAttributes)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M call delegate first then calls deprecated implementation  W onProvideAttributes { RequestInfo }`(
        forge: Forge
    ) {
        // Given
        val okHttpRequestInfo = OkHttpRequestInfo(mockOkHttpRequest)
        val okHttpResponseInfo = OkHttpResponseInfo(mockOkHttpResponse)
        val expectedAttributes = forge.exhaustiveAttributes()

        whenever(
            mockDelegate.onProvideAttributes(
                okHttpRequestInfo,
                okHttpResponseInfo,
                fakeThrowable
            )
        )
            .doReturn(emptyMap())

        whenever(
            mockDelegate.onProvideAttributes(
                mockOkHttpRequest,
                mockOkHttpResponse,
                fakeThrowable
            )
        )
            .doReturn(expectedAttributes)

        // When
        val result = testedProvider.onProvideAttributes(okHttpRequestInfo, okHttpResponseInfo, fakeThrowable)

        // Then
        Assertions.assertThat(result).isEqualTo(expectedAttributes)
        verify(mockDelegate).onProvideAttributes(okHttpRequestInfo, okHttpResponseInfo, fakeThrowable)
        verify(mockDelegate).onProvideAttributes(mockOkHttpRequest, mockOkHttpResponse, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M return delegate result W onProvideAttributes { RequestInfo, delegate returns non-empty }`(forge: Forge) {
        // Given
        val okHttpRequestInfo = OkHttpRequestInfo(mockOkHttpRequest)
        val okHttpResponseInfo = OkHttpResponseInfo(mockOkHttpResponse)
        val expectedAttributes = forge.exhaustiveAttributes()
        whenever(
            mockDelegate.onProvideAttributes(
                okHttpRequestInfo,
                okHttpResponseInfo,
                fakeThrowable
            )
        )
            .doReturn(expectedAttributes)

        // When
        val result = testedProvider.onProvideAttributes(okHttpRequestInfo, okHttpResponseInfo, fakeThrowable)

        // Then
        Assertions.assertThat(result).isEqualTo(expectedAttributes)
        verify(mockDelegate).onProvideAttributes(okHttpRequestInfo, okHttpResponseInfo, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }

    @Test
    fun `M return empty map if RequestInfo in not OkHttpRequestInfo W onProvideAttributes { RequestInfo }`() {
        // Given
        whenever(mockDelegate.onProvideAttributes(mockRequestInfo, mockResponseInfo, fakeThrowable))
            .doReturn(emptyMap())

        // When
        val result = testedProvider.onProvideAttributes(mockRequestInfo, mockResponseInfo, fakeThrowable)

        // Then
        Assertions.assertThat(result).isEmpty()
        verify(mockDelegate).onProvideAttributes(mockRequestInfo, mockResponseInfo, fakeThrowable)
        verifyNoMoreInteractions(mockDelegate)
    }
}

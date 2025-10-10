/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.net.RequestFactory.Companion.HEADER_API_KEY
import com.datadog.android.api.net.RequestFactory.Companion.HEADER_EVP_ORIGIN
import com.datadog.android.api.net.RequestFactory.Companion.HEADER_EVP_ORIGIN_VERSION
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.net.ResourcesRequestFactory.Companion.APPLICATION_ID
import com.datadog.android.sessionreplay.internal.net.ResourcesRequestFactory.Companion.UPLOAD_DESCRIPTION
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesRequestFactoryTest {
    private lateinit var testedRequestFactory: ResourcesRequestFactory

    @Mock
    lateinit var mockResourceRequestBodyFactory: ResourceRequestBodyFactory

    private lateinit var fakeRawBatchEvents: List<RawBatchEvent>

    @Mock
    lateinit var mockRequestBody: RequestBody

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeMediaType: MediaType

    @Mock
    lateinit var mockDatadogSite: DatadogSite

    @StringForgery
    lateinit var fakeApplicationId: String

    @Mock
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeExecutionContext: RequestExecutionContext

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeRumFeature = mapOf(APPLICATION_ID to fakeApplicationId)
        whenever(mockDatadogSite.intakeEndpoint).thenReturn(DatadogSite.US1.toString())
        whenever(fakeDatadogContext.site).thenReturn(mockDatadogSite)
        val fakeFeaturesContext = mapOf(Feature.RUM_FEATURE_NAME to fakeRumFeature)
        whenever(fakeDatadogContext.featuresContext).thenReturn(fakeFeaturesContext)

        fakeRawBatchEvents = forge.aList { forge.getForgery() }
        whenever(mockResourceRequestBodyFactory.create(fakeRawBatchEvents))
            .thenReturn(mockRequestBody)

        fakeMediaType = forge.anElementFrom(
            listOf(
                MultipartBody.FORM,
                MultipartBody.ALTERNATIVE,
                MultipartBody.MIXED,
                MultipartBody.PARALLEL
            )
        )
        whenever(mockRequestBody.contentType()).thenReturn(fakeMediaType)

        testedRequestFactory = ResourcesRequestFactory(
            customEndpointUrl = null,
            internalLogger = mockInternalLogger,
            resourceRequestBodyFactory = mockResourceRequestBodyFactory
        )
    }

    @Test
    fun `M return valid request W create()`() {
        // When
        val request = testedRequestFactory.create(
            fakeDatadogContext,
            fakeExecutionContext,
            fakeRawBatchEvents,
            null
        )

        // Then
        assertThat(request).isInstanceOf(Request::class.java)
        requireNotNull(request)
        val requestHeaders = request.headers
        val requestBody = request.body
        assertThat(requestHeaders[HEADER_API_KEY])
            .isEqualTo(fakeDatadogContext.clientToken)
        assertThat(requestHeaders[HEADER_EVP_ORIGIN])
            .isEqualTo(fakeDatadogContext.source)
        assertThat(requestHeaders[HEADER_EVP_ORIGIN_VERSION])
            .isEqualTo(fakeDatadogContext.sdkVersion)
        assertThat(requestBody).isEqualTo(mockRequestBody.toByteArray())

        assertThat(request.description).isEqualTo(UPLOAD_DESCRIPTION)
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/replay")
    }

    @Test
    fun `M  return valid request W create() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeEndpoint: String
    ) {
        // Given
        testedRequestFactory = ResourcesRequestFactory(
            customEndpointUrl = fakeEndpoint,
            internalLogger = mockInternalLogger,
            resourceRequestBodyFactory = mockResourceRequestBodyFactory
        )

        // When
        val request = testedRequestFactory.create(
            fakeDatadogContext,
            fakeExecutionContext,
            fakeRawBatchEvents,
            null
        )

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(fakeEndpoint)
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo(UPLOAD_DESCRIPTION)
        assertThat(request.body).isEqualTo(mockRequestBody.toByteArray())
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}

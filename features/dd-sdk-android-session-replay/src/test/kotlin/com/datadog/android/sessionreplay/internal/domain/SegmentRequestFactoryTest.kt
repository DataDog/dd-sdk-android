/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.BatchesToSegmentsMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonObject
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
import org.assertj.core.api.Assertions.assertThatThrownBy
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
internal class SegmentRequestFactoryTest {

    private lateinit var testedRequestFactory: SegmentRequestFactory

    @Mock
    lateinit var mockBatchesToSegmentsMapper: BatchesToSegmentsMapper

    @Mock
    lateinit var mockSegmentRequestBodyFactory: SegmentRequestBodyFactory

    @Forgery
    lateinit var fakeSegment: MobileSegment

    @Forgery
    lateinit var fakeSerializedSegment: JsonObject

    private lateinit var fakeCompressedSegment: ByteArray

    @Forgery
    lateinit var fakeBatchData: List<RawBatchEvent>

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockRequestBody: RequestBody

    private lateinit var fakeMediaType: MediaType

    private var fakeBatchMetadata: ByteArray? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMediaType = forge.anElementFrom(
            listOf(
                MultipartBody.FORM,
                MultipartBody.ALTERNATIVE,
                MultipartBody.MIXED,
                MultipartBody.PARALLEL
            )
        )
        whenever(mockRequestBody.contentType()).thenReturn(fakeMediaType)
        fakeCompressedSegment = forge.aString().toByteArray()
        fakeBatchMetadata = forge.aNullable { forge.aString().toByteArray() }
        whenever(mockSegmentRequestBodyFactory.create(fakeSegment, fakeSerializedSegment))
            .thenReturn(mockRequestBody)
        whenever(mockBatchesToSegmentsMapper.map(fakeBatchData.map { it.data }))
            .thenReturn(Pair(fakeSegment, fakeSerializedSegment))
        testedRequestFactory = SegmentRequestFactory(
            customEndpointUrl = null,
            mockBatchesToSegmentsMapper,
            mockSegmentRequestBodyFactory
        )
    }

    // region Request

    @Test
    fun `M return a valid Request W create`() {
        // When
        val request = testedRequestFactory.create(
            fakeDatadogContext,
            fakeBatchData,
            fakeBatchMetadata
        )

        // Then
        assertThat(request.url).isEqualTo(expectedUrl(fakeDatadogContext.site.intakeEndpoint))
        assertThat(request.contentType).isEqualTo(fakeMediaType.toString())
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Session Replay Segment Upload Request")
        assertThat(request.body).isEqualTo(mockRequestBody.toByteArray())
    }

    @Test
    fun `M return a valid Request W create { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com") fakeEndpoint: String
    ) {
        // When
        testedRequestFactory = SegmentRequestFactory(
            customEndpointUrl = fakeEndpoint,
            mockBatchesToSegmentsMapper,
            mockSegmentRequestBodyFactory
        )
        val request = testedRequestFactory.create(
            fakeDatadogContext,
            fakeBatchData,
            fakeBatchMetadata
        )

        // Then
        assertThat(request.url).isEqualTo(expectedUrl(fakeEndpoint))
        assertThat(request.contentType).isEqualTo(fakeMediaType.toString())
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Session Replay Segment Upload Request")
        assertThat(request.body).isEqualTo(mockRequestBody.toByteArray())
    }

    @Test
    fun `M throw exception W create(){ payload is broken }`() {
        // Given
        whenever(mockBatchesToSegmentsMapper.map(fakeBatchData.map { it.data }))
            .thenReturn(null)

        // When
        assertThatThrownBy {
            testedRequestFactory.create(fakeDatadogContext, fakeBatchData, fakeBatchMetadata)
        }
            .isInstanceOf(InvalidPayloadFormatException::class.java)
            .hasMessage(
                "The payload format was broken and " +
                    "an upload request could not be created"
            )
    }

    // endregion

    // region Internal

    private fun expectedUrl(endpointUrl: String): String {
        return "$endpointUrl/api/v2/replay"
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }

    // endregion
}

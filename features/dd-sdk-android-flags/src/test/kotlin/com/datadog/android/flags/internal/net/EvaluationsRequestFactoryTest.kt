/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.utils.join
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationsRequestFactoryTest {

    private lateinit var testedFactory: EvaluationsRequestFactory

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedFactory = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = null
        )
    }

    @Test
    fun `M create a proper request W create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val metaData = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, metaData)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/flagevaluations")
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_TEXT_UTF8)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Evaluation Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }.join(
                separator = "\n".toByteArray(Charsets.UTF_8),
                internalLogger = mockInternalLogger
            )
        )
    }

    @Test
    fun `M generate unique request IDs W create() { multiple calls }`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val metadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request1 = testedFactory.create(fakeDatadogContext, executionContext, batchData, metadata)
        val request2 = testedFactory.create(fakeDatadogContext, executionContext, batchData, metadata)

        // Then
        assertThat(request1.id).isNotEqualTo(request2.id)
        assertThat(request1.headers[RequestFactory.HEADER_REQUEST_ID])
            .isNotEqualTo(request2.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request1.id).isEqualTo(request1.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request2.id).isEqualTo(request2.headers[RequestFactory.HEADER_REQUEST_ID])
    }

    @Test
    fun `M build NDJSON payload W create() { multiple JSON events }`(
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        val json1 = """{"flag":{"key":"feature-a"},"timestamp":1000,"evaluation_count":5}"""
        val json2 = """{"flag":{"key":"feature-b"},"timestamp":2000,"evaluation_count":3}"""
        val json3 = """{"flag":{"key":"feature-c"},"timestamp":3000,"evaluation_count":1}"""
        val batchData = listOf(
            RawBatchEvent(data = json1.toByteArray()),
            RawBatchEvent(data = json2.toByteArray()),
            RawBatchEvent(data = json3.toByteArray())
        )

        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, batchMetadata.toByteArray())

        val expectedBody = "$json1\n$json2\n$json3"
        assertThat(request.body).isEqualTo(expectedBody.toByteArray())
    }

    @Test
    fun `M use custom endpoint W create() { custom endpoint provided }`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customEndpoint: String,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val factoryWithCustomEndpoint = EvaluationsRequestFactory(
            internalLogger = mockInternalLogger,
            customEvaluationEndpoint = customEndpoint
        )
        val metadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = factoryWithCustomEndpoint.create(fakeDatadogContext, executionContext, batchData, metadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(customEndpoint)
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_TEXT_UTF8)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Evaluation Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }.join(
                separator = "\n".toByteArray(Charsets.UTF_8),
                internalLogger = mockInternalLogger
            )
        )
    }
}

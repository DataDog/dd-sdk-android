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
internal class FlagsRequestFactoryTest {

    private lateinit var testedFactory: FlagsRequestFactory

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedFactory = FlagsRequestFactory(
            internalLogger = mockInternalLogger
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
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/exposures")
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
        assertThat(request.description).isEqualTo("Exposure Request")
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
    fun `M create request with valid UUID format W create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val metadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, metadata)

        // Then
        val uuidRegex = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        assertThat(request.id).matches(uuidRegex)
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).matches(uuidRegex)
    }

    @Test
    fun `M preserve batch data order W create() { multiple batch events }`(
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val event1 = RawBatchEvent(data = "event1".toByteArray())
        val event2 = RawBatchEvent(data = "event2".toByteArray())
        val event3 = RawBatchEvent(data = "event3".toByteArray())
        val batchData = listOf(event1, event2, event3)
        val metadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, metadata)

        // Then
        val expectedBody = "event1\nevent2\nevent3".toByteArray()
        assertThat(request.body).isEqualTo(expectedBody)
    }
}

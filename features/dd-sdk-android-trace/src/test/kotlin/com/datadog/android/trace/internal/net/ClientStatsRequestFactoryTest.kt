/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ClientStatsRequestFactoryTest {

    private lateinit var testedFactory: ClientStatsRequestFactory

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        testedFactory = ClientStatsRequestFactory(mockInternalLogger, customStatsEndpointUrl = null)
    }

    @Test
    fun `M create a proper request W create()`(
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeMetadata: String,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val fakeBatchMetadata = forge.aNullable { fakeMetadata.toByteArray() }
        val fakeSingleBatchData = listOf(fakeBatchEvent)

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, fakeSingleBatchData, fakeBatchMetadata)

        // Then
        checkNotNull(request)
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v0.2/stats")
        assertThat(request.contentType).isEqualTo("application/msgpack")
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion,
                "Content-Encoding" to "gzip"
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Client Stats Request")
        assertThat(request.body).isEqualTo(fakeSingleBatchData.first().data)
    }

    @Test
    fun `M create a proper request W create() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeEndpoint: String,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeMetadata: String,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        testedFactory = ClientStatsRequestFactory(mockInternalLogger, customStatsEndpointUrl = fakeEndpoint)
        val fakeBatchMetadata = forge.aNullable { fakeMetadata.toByteArray() }
        val fakeSingleBatchData = listOf(fakeBatchEvent)

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, fakeSingleBatchData, fakeBatchMetadata)

        // Then
        checkNotNull(request)
        assertThat(request.url).isEqualTo(fakeEndpoint)
    }

    @Test
    fun `M return null W create() { empty batch }`(
        @StringForgery fakeMetadata: String,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val fakeBatchMetadata = forge.aNullable { fakeMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, emptyList(), fakeBatchMetadata)

        // Then
        assertThat(request).isNull()
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.utils.join
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsRequestFactoryTest {

    private lateinit var testedFactory: LogsRequestFactory

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        testedFactory = LogsRequestFactory(
            customEndpointUrl = null,
            internalLogger = InternalLogger.UNBOUND
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `𝕄 create a proper request 𝕎 create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(
            "${fakeDatadogContext.site.intakeEndpoint}/api/v2/logs?" +
                "ddsource=${fakeDatadogContext.source}"
        )
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Logs Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }
                .join(
                    separator = ",".toByteArray(),
                    prefix = "[".toByteArray(),
                    suffix = "]".toByteArray(),
                    internalLogger = InternalLogger.UNBOUND
                )
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `𝕄 create a proper request 𝕎 create() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com") fakeEndpoint: String,
        @Forgery batchData: List<RawBatchEvent>,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        testedFactory = LogsRequestFactory(
            customEndpointUrl = fakeEndpoint,
            internalLogger = InternalLogger.UNBOUND
        )
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(
            "$fakeEndpoint/api/v2/logs?" +
                "ddsource=${fakeDatadogContext.source}"
        )
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Logs Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }
                .join(
                    separator = ",".toByteArray(),
                    prefix = "[".toByteArray(),
                    suffix = "]".toByteArray(),
                    internalLogger = InternalLogger.UNBOUND
                )
        )
    }
}

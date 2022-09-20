/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.tracing.internal.net

import com.datadog.android.core.internal.utils.join
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
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
internal class TracesRequestFactoryTest {

    private lateinit var testedFactory: TracesRequestFactory

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpoint: String

    @BeforeEach
    fun `set up`() {
        testedFactory = TracesRequestFactory(
            endpointUrl = fakeEndpoint
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `𝕄 create a proper request 𝕎 create()`(
        @StringForgery batchData: List<String>,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val batchData = batchData.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, batchData, batchMetadata)

        // Then
        assertThat(request.url).isEqualTo("$fakeEndpoint/api/v2/spans")
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
        assertThat(request.description).isEqualTo("Traces Request")
        assertThat(request.body).isEqualTo(
            batchData.join(
                separator = "\n".toByteArray()
            )
        )
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.utils.join
import com.datadog.android.rum.internal.domain.event.RumViewEventFilter
import com.datadog.android.rum.utils.forge.Configurator
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumRequestFactoryTest {

    private lateinit var testedFactory: RumRequestFactory

    @Mock
    lateinit var mockViewEventFilter: RumViewEventFilter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeExecutionContext: RequestExecutionContext

    @BeforeEach
    fun `set up`() {
        whenever(mockViewEventFilter.filterOutRedundantViewEvents(any())) doAnswer {
            it.getArgument(0)
        }

        testedFactory = RumRequestFactory(
            customEndpointUrl = null,
            viewEventFilter = mockViewEventFilter,
            internalLogger = InternalLogger.UNBOUND
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `M create a proper request W create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        val expectedUrl = expectedUrl(fakeDatadogContext.site.intakeEndpoint + "/api/v2/rum")
        assertThat(request.url).isEqualTo(expectedUrl)
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_TEXT_UTF8)
        assertThat(
            request.headers.minus(
                listOf(
                    RequestFactory.HEADER_REQUEST_ID,
                    RequestFactory.DD_IDEMPOTENCY_KEY
                )
            )
        ).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.headers[RequestFactory.DD_IDEMPOTENCY_KEY]).matches("[a-f0-9]{40}")
        assertThat(request.description).isEqualTo("RUM Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }.join(
                separator = "\n".toByteArray(),
                internalLogger = InternalLogger.UNBOUND
            )
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `M create a proper request W create() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeEndpoint: String,
        @Forgery batchData: List<RawBatchEvent>,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        testedFactory = RumRequestFactory(
            customEndpointUrl = fakeEndpoint,
            viewEventFilter = mockViewEventFilter,
            internalLogger = InternalLogger.UNBOUND
        )
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeDatadogContext, fakeExecutionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(expectedUrl(fakeEndpoint))
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_TEXT_UTF8)
        assertThat(
            request.headers.minus(
                listOf(
                    RequestFactory.HEADER_REQUEST_ID,
                    RequestFactory.DD_IDEMPOTENCY_KEY
                )
            )
        ).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.headers[RequestFactory.DD_IDEMPOTENCY_KEY]).matches("[a-f0-9]{40}")
        assertThat(request.description).isEqualTo("RUM Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }.join(
                separator = "\n".toByteArray(),
                internalLogger = InternalLogger.UNBOUND
            )
        )
    }

    private fun expectedUrl(endpointUrl: String): String {
        val queryTags = mutableListOf<String>()

        if (fakeExecutionContext.previousResponseCode != null) {
            queryTags.add("${RumRequestFactory.RETRY_COUNT_KEY}:${fakeExecutionContext.attemptNumber}")
            queryTags.add("${RumRequestFactory.LAST_FAILURE_STATUS_KEY}:${fakeExecutionContext.previousResponseCode}")
        }

        return buildString {
            append("$endpointUrl?ddsource=${fakeDatadogContext.source}")
            if (queryTags.isNotEmpty()) {
                append("&ddtags=${queryTags.joinToString(",")}")
            }
        }
    }
}

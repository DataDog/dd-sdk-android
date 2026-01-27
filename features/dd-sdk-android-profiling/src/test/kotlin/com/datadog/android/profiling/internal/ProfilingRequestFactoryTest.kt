/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.profiling.forge.Configurator
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
class ProfilingRequestFactoryTest {

    @StringForgery
    private lateinit var fakeEndpoint: String

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var testedFactory: ProfilingRequestFactory

    @BeforeEach
    fun `set up`() {
        testedFactory = ProfilingRequestFactory(
            customEndpointUrl = null
        )
    }

    @Test
    fun `M create a proper request W create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val batchMetadata = forge.aNullable { forge.aString().toByteArray() }

        // When
        val request =
            testedFactory.create(fakeDatadogContext, executionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo("${fakeDatadogContext.site.intakeEndpoint}/api/v2/profile")
        assertThat(request.contentType).contains("multipart/form-data;")
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Profiling Request")
        assertThat(request.body).isNotEmpty()
        batchData.forEach { event ->
            assertThat(request.body.containsSubsequence(event.metadata)).isTrue()
            assertThat(request.body.containsSubsequence(event.data)).isTrue()
        }
    }

    @Test
    fun `M create a proper request W create() { custom endpoint }`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        testedFactory = ProfilingRequestFactory(
            customEndpointUrl = fakeEndpoint
        )
        val batchMetadata = forge.aNullable { forge.aString().toByteArray() }

        // When
        val request =
            testedFactory.create(fakeDatadogContext, executionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(fakeEndpoint)
        assertThat(request.contentType).contains("multipart/form-data;")
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeDatadogContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeDatadogContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeDatadogContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Profiling Request")
        assertThat(request.body).isNotEmpty()
        batchData.forEach { event ->
            assertThat(request.body.containsSubsequence(event.metadata)).isTrue()
            assertThat(request.body.containsSubsequence(event.data)).isTrue()
        }
    }

    @Suppress("ReturnCount")
    // Simple subsequence search for ByteArray to avoid AssertJ overload issues in Kotlin
    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        if (this.isEmpty()) return false
        outer@ for (i in 0..this.size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}

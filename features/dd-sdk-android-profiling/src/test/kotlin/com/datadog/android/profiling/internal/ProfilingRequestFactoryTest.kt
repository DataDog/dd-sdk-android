/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
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

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedFactory: ProfilingRequestFactory

    @BeforeEach
    fun `set up`() {
        testedFactory = ProfilingRequestFactory(
            customEndpointUrl = null,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M create a proper request W create()`(
        @Forgery fakeEvent: RawBatchEvent,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        val batchData = listOf(fakeEvent)
        val batchMetadata = forge.aNullable { forge.aString().toByteArray() }

        // When
        val request =
            testedFactory.create(fakeDatadogContext, executionContext, batchData, batchMetadata)

        // Then
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
        assertThat(request.body.containsSubsequence(fakeEvent.data)).isTrue()
    }

    @Test
    fun `M create a proper request W create() { custom endpoint }`(
        @Forgery fakeEvent: RawBatchEvent,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        testedFactory = ProfilingRequestFactory(
            customEndpointUrl = fakeEndpoint,
            internalLogger = mockInternalLogger
        )
        val batchData = listOf(fakeEvent)
        val batchMetadata = forge.aNullable { forge.aString().toByteArray() }

        // When
        val request =
            testedFactory.create(fakeDatadogContext, executionContext, batchData, batchMetadata)

        // Then
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
        assertThat(request.body.containsSubsequence(fakeEvent.data)).isTrue()
    }

    @Test
    fun `M use raw metadata bytes W buildRequestBody {launch profile event}`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given — a launch profile: metadata is raw perfetto bytes (no DDCP magic prefix)
        val fakePerfettoBytes = forge.aString().toByteArray()
        val safeMetadata = byteArrayOf(0x00) + fakePerfettoBytes
        val fakeEventData = forge.aString().toByteArray()
        val batchData = listOf(RawBatchEvent(data = fakeEventData, metadata = safeMetadata))

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, null)

        // Then
        assertThat(request.body.containsSubsequence(safeMetadata)).isTrue()
        assertThat(request.body.containsSubsequence(fakeEventData)).isTrue()
    }

    @Test
    fun `M use perfetto bytes from metadata W buildRequestBody {continuous profile event}`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given — a continuous profile: metadata is a ProfilingBatchMetadata blob
        val fakePerfettoBytes = forge.aString().toByteArray()
        val fakeRumEventsBytes = forge.aString().toByteArray()
        val metadata = ProfilingBatchMetadata(fakePerfettoBytes, fakeRumEventsBytes).toBytes()
        val fakeEventData = forge.aString().toByteArray()
        val batchData = listOf(RawBatchEvent(data = fakeEventData, metadata = metadata))

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, null)

        // Then
        assertThat(request.body.containsSubsequence(fakePerfettoBytes)).isTrue()
        assertThat(request.body.containsSubsequence(fakeEventData)).isTrue()
        assertThat(request.body.containsSubsequence(metadata)).isFalse()
    }

    @Test
    fun `M fallback to raw metadata bytes W buildRequestBody {continuous profile event with corrupt metadata}`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given — DDCP magic prefix present but perfettoLength exceeds the remaining bytes
        val actualPayloadSize = forge.anInt(min = 0, max = 10)
        val claimedLength = actualPayloadSize + forge.anInt(min = 1, max = 100)
        val corruptMetadata = ProfilingBatchMetadata.MAGIC +
            byteArrayOf(
                (claimedLength shr 24).toByte(),
                (claimedLength shr 16).toByte(),
                (claimedLength shr 8).toByte(),
                claimedLength.toByte()
            ) + ByteArray(actualPayloadSize)
        val fakeEventData = forge.aString().toByteArray()
        val batchData = listOf(RawBatchEvent(data = fakeEventData, metadata = corruptMetadata))

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, null)

        // Then — falls back to sending raw metadata bytes (same as launch profile path)
        assertThat(request.body.containsSubsequence(corruptMetadata)).isTrue()
        assertThat(request.body.containsSubsequence(fakeEventData)).isTrue()
    }

    @Test
    fun `M throw exception W create() { empty batchData }`(
        @Forgery executionContext: RequestExecutionContext
    ) {
        assertThatThrownBy {
            testedFactory.create(fakeDatadogContext, executionContext, emptyList(), null)
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage(ProfilingRequestFactory.EMPTY_BATCH_DATA_ERROR_MESSAGE)
    }

    @Test
    fun `M log warning and use first event W create() { multiple batchData events }`(
        @Forgery fakeEvent: RawBatchEvent,
        @Forgery extraEvent: RawBatchEvent,
        @Forgery executionContext: RequestExecutionContext
    ) {
        // Given
        val batchData = listOf(fakeEvent, extraEvent)

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, null)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                ProfilingRequestFactory.MULTIPLE_BATCH_EVENTS_WARNING_MESSAGE.format(batchData.size)
            )
        }
        assertThat(request.body.containsSubsequence(fakeEvent.data)).isTrue()
        assertThat(request.body.containsSubsequence(extraEvent.data)).isFalse()
    }

    @Suppress("ReturnCount")
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

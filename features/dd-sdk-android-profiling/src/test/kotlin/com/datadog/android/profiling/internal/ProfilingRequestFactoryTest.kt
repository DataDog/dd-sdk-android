/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.github.luben.zstd.Zstd
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MultipartReader
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.io.ByteArrayInputStream
import java.util.Locale

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
    fun `M use perfetto bytes from metadata W buildRequestBody`(
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given — a continuous profile: metadata is a ProfilingBatchMetadata blob
        val fakePerfettoBytes = Zstd.compress(forge.aString().toByteArray())
        val fakeRumEventsBytes = forge.aString().toByteArray()
        val metadata = checkNotNull(
            ProfilingBatchMetadata.encode(
                ByteArrayInputStream(fakePerfettoBytes),
                fakePerfettoBytes.size,
                fakeRumEventsBytes
            )
        )
        val fakeEventData = forge.aString().toByteArray()
        val batchData = listOf(RawBatchEvent(data = fakeEventData, metadata = metadata))

        // When
        val request = testedFactory.create(fakeDatadogContext, executionContext, batchData, null)

        // Then
        val parts = request.parts()
        assertThat(parts.keys).containsExactly(EVENT_DISPOSITION, PERFETTO_DISPOSITION, RUM_EVENTS_DISPOSITION)
        assertThat(parts.getValue(EVENT_DISPOSITION)).isEqualTo(fakeEventData)
        assertThat(parts.getValue(PERFETTO_DISPOSITION)).isEqualTo(fakePerfettoBytes)
        assertThat(parts.getValue(RUM_EVENTS_DISPOSITION)).isEqualTo(fakeRumEventsBytes)
    }

    @Test
    fun `M fallback to raw metadata bytes W buildRequestBody {corrupt metadata}`(
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

        // Then
        val parts = request.parts()
        assertThat(parts.keys).containsExactly(EVENT_DISPOSITION, PERFETTO_DISPOSITION)
        assertThat(parts.getValue(EVENT_DISPOSITION)).isEqualTo(fakeEventData)
        assertThat(parts.getValue(PERFETTO_DISPOSITION)).isEqualTo(corruptMetadata)
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
                ProfilingRequestFactory.MULTIPLE_BATCH_EVENTS_WARNING_MESSAGE.format(Locale.US, batchData.size)
            )
        }
        assertThat(request.body.containsSubsequence(fakeEvent.data)).isTrue()
        assertThat(request.body.containsSubsequence(extraEvent.data)).isFalse()
    }

    private fun Request.parts(): Map<String, ByteArray> {
        val contentType = checkNotNull(contentType) { "Missing request content type" }
        val boundary = contentType.substringAfter("boundary=")
        val parts = linkedMapOf<String, ByteArray>()
        MultipartReader(Buffer().write(body), boundary).use { reader ->
            while (true) {
                val part = reader.nextPart() ?: break
                val disposition = checkNotNull(part.headers["Content-Disposition"]) { "Missing Content-Disposition" }
                parts[disposition] = part.body.readByteArray()
            }
        }
        return parts
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

    companion object {
        private const val EVENT_DISPOSITION = "form-data; name=\"event\"; filename=\"event.json\""
        private const val PERFETTO_DISPOSITION = "form-data; name=\"perfetto.proto\"; filename=\"perfetto.proto\""
        private const val RUM_EVENTS_DISPOSITION =
            "form-data; name=\"rum-mobile-events.json\"; filename=\"rum-mobile-events.json\""
    }
}

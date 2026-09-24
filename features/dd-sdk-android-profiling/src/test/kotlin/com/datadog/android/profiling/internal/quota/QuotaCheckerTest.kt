/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.threads.FakeSameThreadExecutorService
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class QuotaCheckerTest {

    @Mock
    private lateinit var mockCallFactory: Call.Factory

    @Mock
    private lateinit var mockCall: Call

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Forgery
    private lateinit var fakeDatadogContext: DatadogContext

    @StringForgery
    private lateinit var fakeSessionId: String

    private val executor = FakeSameThreadExecutorService()

    private val capturedResults = mutableListOf<QuotaResult>()

    private lateinit var testedChecker: QuotaChecker

    @BeforeEach
    fun `set up`() {
        fakeDatadogContext = fakeDatadogContext.copy(site = DatadogSite.US1)
        whenever(mockCallFactory.newCall(any<Request>())) doReturn mockCall
        testedChecker = ProfilingQuotaChecker(
            callFactory = mockCallFactory,
            executor = executor,
            internalLogger = mockInternalLogger,
            onResult = { capturedResults.add(it) }
        )
    }

    // region decision parsing

    @Test
    fun `M return ALLOWED W quota_ok decision in response`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.ALLOWED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.QUOTA_OK)
    }

    @Test
    fun `M return DENIED W quota_ko decision in response`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"quota_exceeded"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.QUOTA_EXCEEDED)
    }

    @Test
    fun `M return UNDEFINED reason W unknown reason string in response`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"some_future_reason"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.UNDEFINED)
    }

    // endregion

    // region HTTP error handling

    @Test
    fun `M return API_ERROR W non-200 response`() {
        // Given
        whenever(mockCall.execute()) doReturn makeResponse(500, "Internal Server Error")

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last()).isEqualTo(QuotaResult.API_ERROR)
    }

    @Test
    fun `M return DENIED W 429 response`() {
        // Given
        whenever(mockCall.execute()) doReturn makeResponse(ProfilingQuotaChecker.HTTP_TOO_MANY_REQUESTS, "")

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.QUOTA_EXCEEDED)
    }

    @Test
    fun `M return API_ERROR W network IOException`() {
        // Given
        whenever(mockCall.execute()) doThrow IOException("connection refused")

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last()).isEqualTo(QuotaResult.API_ERROR)
    }

    @Test
    fun `M return API_ERROR W malformed JSON body`() {
        // Given
        whenever(mockCall.execute()) doReturn makeResponse(200, "not json {{{")

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last()).isEqualTo(QuotaResult.API_ERROR)
    }

    // endregion

    // region onResult callback

    @Test
    fun `M invoke onResult callback W check completes`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults).hasSize(1)
        assertThat(capturedResults[0].decision).isEqualTo(QuotaResult.Decision.ALLOWED)
    }

    // endregion

    // region request building

    @Test
    fun `M use quota subdomain and session_id param W building request`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)
        val requestCaptor = argumentCaptor<Request>()
        whenever(mockCallFactory.newCall(requestCaptor.capture())) doReturn mockCall

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        val capturedRequest = requestCaptor.firstValue
        val expectedUrl = ProfilingQuotaChecker.QUOTA_URL_TEMPLATE.format(
            Locale.US,
            fakeDatadogContext.site.intakeEndpoint.removePrefix("https://"),
            fakeSessionId
        )
        assertThat(capturedRequest.url.toString()).isEqualTo(expectedUrl)
        assertThat(capturedRequest.header(ProfilingQuotaChecker.HEADER_CLIENT_TOKEN))
            .isEqualTo(fakeDatadogContext.clientToken)
    }

    // endregion

    // region missing fields

    @Test
    fun `M return ALLOWED W decision field absent in response`() {
        // Given
        whenever(mockCall.execute()) doReturn makeResponse(200, """{"data":{"attributes":{"reason":"quota_ok"}}}""")

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.ALLOWED)
    }

    // endregion

    // region reason normalization

    @Test
    fun `M return DENIED W backend_unavailable reason and admitted false`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"backend_unavailable"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then — admitted field is authoritative; reason is preserved for telemetry
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.BACKEND_UNAVAILABLE)
    }

    @Test
    fun `M normalize to BACKEND_UNAVAILABLE W backend_client_not_initialized reason`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"backend_client_not_initialized"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.ALLOWED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.BACKEND_UNAVAILABLE)
    }

    @Test
    fun `M return DENIED W backend_client_not_initialized reason and admitted false`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"backend_client_not_initialized"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then — admitted field is authoritative; reason is normalised to BACKEND_UNAVAILABLE for telemetry
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.BACKEND_UNAVAILABLE)
    }

    @Test
    fun `M return DENIED W org_disabled reason and admitted false`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"org_disabled"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.ORG_DISABLED)
    }

    @Test
    fun `M return UNDEFINED reason W reason field absent in response`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(capturedResults.last().decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(capturedResults.last().reason).isEqualTo(QuotaReason.UNDEFINED)
    }

    // endregion

    // region Accept header

    @Test
    fun `M include Accept header W building request`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)
        val requestCaptor = argumentCaptor<Request>()
        whenever(mockCallFactory.newCall(requestCaptor.capture())) doReturn mockCall

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(requestCaptor.firstValue.header(ProfilingQuotaChecker.HEADER_ACCEPT))
            .isEqualTo(ProfilingQuotaChecker.MEDIA_TYPE_JSON_API)
    }

    // endregion

    // region session deduplication

    @Test
    fun `M not fire second request W checkAsync called twice with same sessionId`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext) // same session — should be a no-op

        // Then
        verify(mockCallFactory, times(1)).newCall(any())
    }

    @Test
    fun `M fire new request W checkAsync called with different sessionId`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)
        val secondSessionId = fakeSessionId + "_new"

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        testedChecker.checkAsync(secondSessionId, fakeDatadogContext)

        // Then
        verify(mockCallFactory, times(2)).newCall(any())
    }

    @Test
    fun `M fire new request W reset then checkAsync with same sessionId`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":true,"reason":"quota_ok"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        testedChecker.reset()
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext) // same session, but reset was called

        // Then
        verify(mockCallFactory, times(2)).newCall(any())
    }

    // endregion

    // region lastResult

    @Test
    fun `M return null W lastResult queried before any check`() {
        assertThat(testedChecker.lastResult).isNull()
    }

    @Test
    fun `M expose last result W checkAsync completes`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"quota_exceeded"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)

        // When
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        // Then
        assertThat(testedChecker.lastResult).isEqualTo(QuotaResult.QUOTA_EXCEEDED)
    }

    @Test
    fun `M clear last result W reset called`() {
        // Given
        val body = """{"data":{"attributes":{"admitted":false,"reason":"quota_exceeded"}}}"""
        whenever(mockCall.execute()) doReturn makeResponse(200, body)
        testedChecker.checkAsync(fakeSessionId, fakeDatadogContext)

        assertThat(testedChecker.lastResult).isNotNull()

        // When
        testedChecker.reset()

        // Then
        assertThat(testedChecker.lastResult).isNull()
    }

    // endregion

    // region helpers

    private fun makeResponse(code: Int, body: String): Response =
        Response.Builder()
            .request(
                Request.Builder()
                    .url(
                        ProfilingQuotaChecker.QUOTA_URL_TEMPLATE.format(
                            Locale.US,
                            fakeDatadogContext.site.intakeEndpoint.removePrefix("https://"),
                            fakeSessionId
                        )
                    )
                    .build()
            )
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    // endregion
}

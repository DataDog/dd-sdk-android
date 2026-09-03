/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrecomputedAssignmentsDownloaderRetryTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRequestFactory: PrecomputedAssignmentsRequestFactory

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockTimeout: Timeout

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumApplicationId: UUID

    private lateinit var fakeEvaluationContext: EvaluationContext
    private lateinit var fakeRequest: Request
    private lateinit var testedDownloader: PrecomputedAssignmentsDownloader

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext +
                mapOf(Feature.RUM_FEATURE_NAME to mapOf("application_id" to fakeRumApplicationId.toString()))
        )
        fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("plan" to "premium")
        )
        fakeRequest = Request.Builder().url(FAKE_URL).build()
        testedDownloader = createDownloader(requestRetryCount = 1)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeDatadogContext)).doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.timeout()).doReturn(mockTimeout)
    }

    @Test
    fun `M retry and return response W readPrecomputedFlags() { transient response }`() {
        // Given
        val calls = queueCalls(
            callReturning(createPrecomputedUnsuccessfulResponse(500, FAKE_URL)),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M close failed response before creating retry call W readPrecomputedFlags()`() {
        // Given
        val failedResponseBody = mock<ResponseBody>()
        val firstCall = callReturning(createPrecomputedResponse(500, FAKE_URL, failedResponseBody))
        val secondCall = callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        queueCalls(
            firstCall,
            secondCall
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        val ordered = inOrder(mockCallFactory, firstCall, failedResponseBody, secondCall)
        ordered.verify(mockCallFactory).newCall(fakeRequest)
        ordered.verify(firstCall).execute()
        ordered.verify(failedResponseBody).close()
        ordered.verify(mockCallFactory).newCall(fakeRequest)
        ordered.verify(secondCall).execute()
    }

    @ParameterizedTest
    @ValueSource(ints = [408, 500, 599])
    fun `M retry transient status W readPrecomputedFlags()`(statusCode: Int) {
        // Given
        val calls = queueCalls(
            callReturning(createPrecomputedUnsuccessfulResponse(statusCode, FAKE_URL)),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M not retry W readPrecomputedFlags() { rate limited response }`() {
        // Given
        whenever(mockCall.execute()).doReturn(createPrecomputedUnsuccessfulResponse(429, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
    }

    @Test
    fun `M make one attempt W readPrecomputedFlags() { retries disabled }`() {
        // Given
        testedDownloader = createDownloader(requestRetryCount = 0)
        whenever(mockCall.execute()).doThrow(IOException("request failed"))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
        verify(mockCall).execute()
    }

    @Test
    fun `M use custom request limits W readPrecomputedFlags() { transient failures }`() {
        // Given
        testedDownloader = createDownloader(requestTimeoutMs = 2_500L, requestRetryCount = 2)
        val callsAndTimeouts = List(3) {
            val timeout = mock<Timeout>()
            whenever(timeout.timeout(2_500L, TimeUnit.MILLISECONDS)).doReturn(timeout)
            callThrowing(IOException("timeout"), timeout) to timeout
        }
        queueCalls(*callsAndTimeouts.map { it.first }.toTypedArray())

        // When
        testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        verify(mockCallFactory, times(3)).newCall(fakeRequest)
        callsAndTimeouts.forEach { (call, timeout) ->
            verify(call).execute()
            verify(timeout).timeout(2_500L, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `M preserve call timeout W readPrecomputedFlags() { SDK timeout disabled }`() {
        // Given
        testedDownloader = createDownloader(requestTimeoutMs = 0L)
        whenever(mockCall.execute()).doReturn(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockCall, never()).timeout()
    }

    @Test
    fun `M create and time out a new call for every attempt W readPrecomputedFlags()`() {
        // Given
        val firstCall = mock<Call>()
        val secondCall = mock<Call>()
        val firstTimeout = mock<Timeout>()
        val secondTimeout = mock<Timeout>()
        testedDownloader = createDownloader(
            requestTimeoutMs = EXPLICIT_TIMEOUT_MS,
            requestRetryCount = 1
        )
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(firstCall, secondCall)
        whenever(firstCall.timeout()).doReturn(firstTimeout)
        whenever(secondCall.timeout()).doReturn(secondTimeout)
        whenever(firstTimeout.timeout(EXPLICIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).doReturn(firstTimeout)
        whenever(secondTimeout.timeout(EXPLICIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).doReturn(secondTimeout)
        whenever(firstCall.execute()).doThrow(IOException("transient"))
        whenever(secondCall.execute()).doReturn(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verify(firstTimeout).timeout(EXPLICIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        verify(secondTimeout).timeout(EXPLICIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        verify(firstCall, times(1)).execute()
        verify(secondCall, times(1)).execute()
    }

    @Test
    fun `M retry body read failure W readPrecomputedFlags()`() {
        // Given
        val failedBody = mock<ResponseBody>()
        whenever(failedBody.string()).doThrow(IOException("body read failed"))
        val calls = queueCalls(
            callReturning(createPrecomputedResponse(200, FAKE_URL, failedBody)),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(failedBody).close()
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M include response body in call timeout W readPrecomputedFlags()`() {
        // Given
        val mockResponseBody = mock<ResponseBody>()
        testedDownloader = createDownloader(requestTimeoutMs = EXPLICIT_TIMEOUT_MS)
        whenever(mockResponseBody.string()).doReturn(RESPONSE_BODY)
        whenever(mockCall.execute()).doReturn(createPrecomputedResponse(200, FAKE_URL, mockResponseBody))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        val ordered = inOrder(mockTimeout, mockCall, mockResponseBody)
        ordered.verify(mockTimeout).timeout(EXPLICIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        ordered.verify(mockCall).execute()
        ordered.verify(mockResponseBody).string()
        ordered.verify(mockResponseBody).close()
    }

    private fun createDownloader(
        requestTimeoutMs: Long = 0L,
        requestRetryCount: Int = 0
    ) = PrecomputedAssignmentsDownloader(
        callFactory = mockCallFactory,
        internalLogger = mockInternalLogger,
        requestFactory = mockRequestFactory,
        requestTimeoutMs = requestTimeoutMs,
        requestRetryCount = requestRetryCount
    )

    private fun callReturning(response: Response, timeout: Timeout = mock()): Call = mock<Call>().also { call ->
        whenever(call.timeout()).doReturn(timeout)
        whenever(call.execute()).doReturn(response)
    }

    private fun callThrowing(throwable: IOException, timeout: Timeout = mock()): Call = mock<Call>().also { call ->
        whenever(call.timeout()).doReturn(timeout)
        whenever(call.execute()).doThrow(throwable)
    }

    private fun queueCalls(vararg calls: Call): List<Call> {
        check(calls.isNotEmpty())
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(calls.first(), *calls.drop(1).toTypedArray())
        return calls.toList()
    }

    private fun verifyExecutedOnce(calls: List<Call>) {
        calls.forEach { verify(it, times(1)).execute() }
    }

    private companion object {
        const val FAKE_URL = "https://example.com/flags"
        const val RESPONSE_BODY = "{\"flags\":{}}"
        const val EXPLICIT_TIMEOUT_MS = 1_000L
    }
}

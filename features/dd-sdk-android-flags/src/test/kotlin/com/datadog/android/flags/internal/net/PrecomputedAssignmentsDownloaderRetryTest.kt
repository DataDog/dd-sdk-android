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
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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
    private val scheduledRetries = mutableListOf<Pair<Int, String?>>()

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

    @Test
    fun `M retry when failed response cleanup throws W readPrecomputedFlags()`() {
        // Given
        val failedResponseBody = mock<ResponseBody>()
        doThrow(IllegalStateException("close failed")).whenever(failedResponseBody).close()
        val calls = queueCalls(
            callReturning(createPrecomputedResponse(503, FAKE_URL, failedResponseBody)),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(failedResponseBody).close()
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M retry transient status W readPrecomputedFlags() { response has no body }`() {
        // Given
        val calls = queueCalls(
            callReturning(createPrecomputedResponse(503, FAKE_URL, null)),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockCallFactory, times(2)).newCall(fakeRequest)
        verifyExecutedOnce(calls)
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
    fun `M pass Retry-After to scheduler W readPrecomputedFlags() { service unavailable }`() {
        // Given
        val firstResponse = createPrecomputedUnsuccessfulResponse(503, FAKE_URL)
            .newBuilder()
            .header("Retry-After", "15")
            .build()
        val calls = queueCalls(
            callReturning(firstResponse),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        assertThat(scheduledRetries).containsExactly(0 to "15")
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M ignore Retry-After W readPrecomputedFlags() { response is not service unavailable }`() {
        // Given
        val firstResponse = createPrecomputedUnsuccessfulResponse(500, FAKE_URL)
            .newBuilder()
            .header("Retry-After", "15")
            .build()
        queueCalls(
            callReturning(firstResponse),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(scheduledRetries).containsExactly(0 to null)
    }

    @Test
    fun `M stop retries W readPrecomputedFlags() { scheduler rejects server delay }`() {
        // Given
        testedDownloader = createDownloader(
            requestRetryCount = 1,
            retryScheduler = AssignmentRequestRetryScheduler { _, _ -> false }
        )
        whenever(mockCall.execute()).doReturn(
            createPrecomputedUnsuccessfulResponse(503, FAKE_URL)
                .newBuilder()
                .header("Retry-After", "31")
                .build()
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
        verify(mockCall).execute()
    }

    @Test
    fun `M stop retries and restore interruption W readPrecomputedFlags() { retry wait interrupted }`() {
        // Given
        testedDownloader = createDownloader(
            requestRetryCount = 1,
            retryScheduler = AssignmentRequestRetryScheduler { _, _ -> throw InterruptedException() }
        )
        whenever(mockCall.execute()).doReturn(createPrecomputedUnsuccessfulResponse(500, FAKE_URL))

        try {
            // When
            val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

            // Then
            assertThat(result).isNull()
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            verify(mockCallFactory).newCall(fakeRequest)
            verify(mockCall).execute()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `M stop retries and preserve interruption W readPrecomputedFlags() { interrupted before zero delay }`() {
        // Given
        testedDownloader = createDownloader(
            requestRetryCount = 1,
            retryScheduler = RandomizedAssignmentRequestRetryScheduler(randomLong = { 0L })
        )
        whenever(mockCall.execute()).doReturn(createPrecomputedUnsuccessfulResponse(500, FAKE_URL))

        try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            Thread.currentThread().interrupt()

            // When
            val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

            // Then
            assertThat(result).isNull()
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            verify(mockCallFactory).newCall(fakeRequest)
            verify(mockCall).execute()
        } finally {
            @Suppress("UnsafeThirdPartyFunctionCall")
            Thread.interrupted()
        }
    }

    @Test
    fun `M stop retries and preserve interruption W readPrecomputedFlags() { scheduler interrupts }`() {
        // Given
        testedDownloader = createDownloader(
            requestRetryCount = 1,
            retryScheduler = AssignmentRequestRetryScheduler { _, _ ->
                @Suppress("UnsafeThirdPartyFunctionCall")
                Thread.currentThread().interrupt()
                true
            }
        )
        whenever(mockCall.execute()).doReturn(createPrecomputedUnsuccessfulResponse(500, FAKE_URL))

        try {
            // When
            val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

            // Then
            assertThat(result).isNull()
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            verify(mockCallFactory).newCall(fakeRequest)
            verify(mockCall).execute()
        } finally {
            @Suppress("UnsafeThirdPartyFunctionCall")
            Thread.interrupted()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 429, 499, 600])
    fun `M not retry non-transient status W readPrecomputedFlags()`(statusCode: Int) {
        // Given
        testedDownloader = createDownloader(requestRetryCount = 2)
        whenever(mockCall.execute()).doReturn(createPrecomputedUnsuccessfulResponse(statusCode, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
        verify(mockCall).execute()
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
    fun `M not retry W readPrecomputedFlags() { call was cancelled }`() {
        // Given
        testedDownloader = createDownloader(requestRetryCount = 2)
        whenever(mockCall.isCanceled()).doReturn(true)
        whenever(mockCall.execute()).doThrow(IOException("Canceled"))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
        verify(mockCall).execute()
    }

    @Test
    fun `M retry W readPrecomputedFlags() { OkHttp call timeout cancelled call }`() {
        // Given
        val timedOutCall = callThrowing(InterruptedIOException("timeout"))
        whenever(timedOutCall.isCanceled()).doReturn(true)
        val calls = queueCalls(
            timedOutCall,
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verifyExecutedOnce(calls)
    }

    @Test
    fun `M retry W readPrecomputedFlags() { socket timeout cancelled call }`() {
        // Given
        val timedOutCall = callThrowing(SocketTimeoutException("read timed out"))
        whenever(timedOutCall.isCanceled()).doReturn(true)
        val calls = queueCalls(
            timedOutCall,
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verifyExecutedOnce(calls)
    }

    @ParameterizedTest
    @MethodSource("retryableNetworkErrors")
    fun `M retry W readPrecomputedFlags() { retryable network error }`(error: IOException) {
        // Given
        val calls = queueCalls(
            callThrowing(error),
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verifyExecutedOnce(calls)
    }

    @ParameterizedTest
    @MethodSource("retryableNetworkErrorsBlockedByCancellation")
    fun `M not retry W readPrecomputedFlags() { cancelled call reports retryable error }`(error: IOException) {
        // Given
        testedDownloader = createDownloader(requestRetryCount = 2)
        val cancelledCall = callThrowing(error)
        whenever(cancelledCall.isCanceled()).doReturn(true)
        queueCalls(
            cancelledCall,
            callReturning(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))
        )

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory).newCall(fakeRequest)
        verify(cancelledCall).execute()
    }

    @ParameterizedTest
    @MethodSource("nonRetryableIOExceptions")
    fun `M not retry W readPrecomputedFlags() { non-retryable IO error }`(error: IOException) {
        // Given
        whenever(mockCall.execute()).doThrow(error)

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
            callThrowing(SocketTimeoutException("timeout"), timeout) to timeout
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
    fun `M preserve shorter call timeout W readPrecomputedFlags()`() {
        // Given
        testedDownloader = createDownloader(requestTimeoutMs = 2_500L)
        whenever(mockTimeout.timeoutNanos()).doReturn(TimeUnit.MILLISECONDS.toNanos(1_000L))
        whenever(mockCall.execute()).doReturn(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockTimeout, never()).timeout(2_500L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M apply shorter SDK timeout W readPrecomputedFlags()`() {
        // Given
        testedDownloader = createDownloader(requestTimeoutMs = 2_500L)
        whenever(mockTimeout.timeoutNanos()).doReturn(TimeUnit.MILLISECONDS.toNanos(5_000L))
        whenever(mockTimeout.timeout(2_500L, TimeUnit.MILLISECONDS)).doReturn(mockTimeout)
        whenever(mockCall.execute()).doReturn(createPrecomputedSuccessfulResponse(RESPONSE_BODY, FAKE_URL))

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        verify(mockTimeout).timeout(2_500L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M reject call W readPrecomputedFlags() { SDK timeout enabled and call returns Timeout NONE }`() {
        // Given
        testedDownloader = createDownloader(
            requestTimeoutMs = 2_500L,
            requestRetryCount = 1
        )
        whenever(mockCall.timeout()).doReturn(Timeout.NONE)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext, fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        verify(mockCallFactory, times(1)).newCall(fakeRequest)
        verify(mockCall, never()).execute()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
                capture(),
                eq(null),
                eq(true),
                eq(null)
            )
            assertThat(firstValue.invoke())
                .isEqualTo("A custom assignment request call must provide a configurable timeout")
        }
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
        whenever(firstCall.execute()).doThrow(SocketTimeoutException("transient"))
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
        whenever(failedBody.string()).doThrow(EOFException("body read failed"))
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
        requestRetryCount: Int = 0,
        retryScheduler: AssignmentRequestRetryScheduler = AssignmentRequestRetryScheduler { attempt, retryAfter ->
            scheduledRetries += attempt to retryAfter
            true
        }
    ) = PrecomputedAssignmentsDownloader(
        callFactory = mockCallFactory,
        internalLogger = mockInternalLogger,
        requestFactory = mockRequestFactory,
        requestTimeoutMs = requestTimeoutMs,
        requestRetryCount = requestRetryCount,
        retryScheduler = retryScheduler
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

        @JvmStatic
        fun retryableNetworkErrors(): List<IOException> = listOf(
            SocketTimeoutException("read timed out"),
            UnknownHostException("host not found"),
            ConnectException("connection refused"),
            NoRouteToHostException("no route"),
            SocketException("connection reset"),
            EOFException("unexpected end of stream")
        )

        @JvmStatic
        fun retryableNetworkErrorsBlockedByCancellation(): List<IOException> = retryableNetworkErrors()
            .filterNot { it is SocketTimeoutException }

        @JvmStatic
        fun nonRetryableIOExceptions(): List<IOException> = listOf(
            IOException("generic failure"),
            ProtocolException("invalid protocol"),
            SSLHandshakeException("handshake failed"),
            SSLPeerUnverifiedException("peer not verified"),
            InterruptedIOException("interrupted")
        )
    }
}

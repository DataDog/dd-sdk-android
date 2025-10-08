/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.UnknownHostException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrecomputedAssignmentsDownloaderTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRequestFactory: PrecomputedAssignmentsRequestFactory

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    private lateinit var testedDownloader: PrecomputedAssignmentsDownloader
    private lateinit var fakeFlagsContext: FlagsContext
    private lateinit var fakeEvaluationContext: EvaluationContext

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeFlagsContext = FlagsContext(
            clientToken = forge.anAlphabeticalString(),
            applicationId = forge.anAlphabeticalString(),
            site = DatadogSite.US1,
            env = "test"
        )

        fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("plan" to "premium")
        )

        testedDownloader = PrecomputedAssignmentsDownloader(
            callFactory = mockCallFactory,
            internalLogger = mockInternalLogger,
            flagsContext = fakeFlagsContext,
            requestFactory = mockRequestFactory
        )
    }

    // region readPrecomputedFlags() - Success cases

    @Test
    fun `M return response body W readPrecomputedFlags() { successful request }`(
        @StringForgery fakeResponseBody: String,
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val fakeResponse = createSuccessfulResponse(fakeResponseBody, fakeUrl)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isEqualTo(fakeResponseBody)
        verify(mockRequestFactory).create(fakeEvaluationContext, fakeFlagsContext)
    }

    @Test
    fun `M call factory with correct parameters W readPrecomputedFlags()`(
        @StringForgery fakeResponseBody: String,
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val fakeResponse = createSuccessfulResponse(fakeResponseBody, fakeUrl)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        argumentCaptor<EvaluationContext> {
            verify(mockRequestFactory).create(capture(), eq(fakeFlagsContext))
            assertThat(lastValue).isEqualTo(fakeEvaluationContext)
        }
    }

    // endregion

    // region readPrecomputedFlags() - Factory errors

    @Test
    fun `M return null and log error W readPrecomputedFlags() { factory returns null }`() {
        // Given
        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(null)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region readPrecomputedFlags() - Network errors

    @Test
    fun `M return null and log error W readPrecomputedFlags() { UnknownHostException }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String,
        @StringForgery fakeExceptionMessage: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val exception = UnknownHostException(fakeExceptionMessage)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        argumentCaptor<Throwable> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                any(),
                capture(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue).isInstanceOf(UnknownHostException::class.java)
        }
    }

    @Test
    fun `M return null and log error W readPrecomputedFlags() { IOException }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String,
        @StringForgery fakeExceptionMessage: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val exception = IOException(fakeExceptionMessage)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        argumentCaptor<Throwable> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                any(),
                capture(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue).isInstanceOf(IOException::class.java)
        }
    }

    @Test
    fun `M return null and log error W readPrecomputedFlags() { SecurityException }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String,
        @StringForgery fakeExceptionMessage: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val exception = SecurityException(fakeExceptionMessage)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        argumentCaptor<Throwable> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                any(),
                capture(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue).isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun `M return null and log error W readPrecomputedFlags() { generic exception }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String,
        @StringForgery fakeExceptionMessage: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val exception = RuntimeException(fakeExceptionMessage)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        argumentCaptor<Throwable> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                any(),
                capture(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue).isInstanceOf(RuntimeException::class.java)
        }
    }

    // endregion

    // region readPrecomputedFlags() - Response handling

    @Test
    fun `M return null and log error W readPrecomputedFlags() { unsuccessful response }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val fakeResponse = createUnsuccessfulResponse(404, fakeUrl)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return null W readPrecomputedFlags() { null response body }`(
        @StringForgery(regex = "https://[a-z]+\\.(com|net)/[a-z]+") fakeUrl: String
    ) {
        // Given
        val fakeRequest = Request.Builder()
            .url(fakeUrl)
            .build()
        val fakeResponse = createSuccessfulResponseWithNullBody(fakeUrl)

        whenever(mockRequestFactory.create(fakeEvaluationContext, fakeFlagsContext))
            .doReturn(fakeRequest)
        whenever(mockCallFactory.newCall(fakeRequest)).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedDownloader.readPrecomputedFlags(fakeEvaluationContext)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region Helper methods

    private fun createSuccessfulResponse(body: String, url: String): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun createSuccessfulResponseWithNullBody(url: String): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(null)
        .build()

    private fun createUnsuccessfulResponse(code: Int, url: String): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Error")
        .body("".toResponseBody("application/json".toMediaType()))
        .build()

    // endregion
}

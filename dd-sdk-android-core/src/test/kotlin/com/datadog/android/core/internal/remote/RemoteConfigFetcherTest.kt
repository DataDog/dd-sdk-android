/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RemoteConfigFetcherTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    private lateinit var testedFetcher: RemoteConfigNetworkFetcher

    private val fakeUrl = "https://sdk-configuration.browser-intake-datadoghq.com/v1/fake-id.json"

    @BeforeEach
    fun `set up`() {
        testedFetcher = RemoteConfigNetworkFetcher(
            callFactory = mockCallFactory,
            internalLogger = mockInternalLogger
        )
    }

    // region fetch() - Success

    @Test
    fun `M return response body W fetch() { successful 2xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isEqualTo(fakeBody)
    }

    @Test
    fun `M return null and log W fetch() { 2xx response with empty body }`() {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_EMPTY_BODY,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
    }

    // endregion

    // region fetch() - HTTP errors

    @Test
    fun `M return null and log W fetch() { 4xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_HTTP,
            additionalProperties = mapOf(
                RemoteConfigNetworkFetcher.ATTR_RESPONSE_CODE to 404,
                RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString()
            )
        )
    }

    @Test
    fun `M return null and log W fetch() { 5xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_HTTP,
            additionalProperties = mapOf(
                RemoteConfigNetworkFetcher.ATTR_RESPONSE_CODE to 500,
                RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString()
            )
        )
    }

    // endregion

    // region fetch() - Network errors

    @Test
    fun `M return null and log to maintainer only W fetch() { IOException }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val exception = IOException(fakeMessage)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = RemoteConfigNetworkFetcher.ERROR_NETWORK,
            throwableClass = IOException::class.java,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
    }

    @Test
    fun `M return null and log to maintainer and telemetry W fetch() { unexpected exception }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val exception = RuntimeException(fakeMessage)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_NETWORK,
            throwableClass = RuntimeException::class.java,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
    }

    // endregion
}

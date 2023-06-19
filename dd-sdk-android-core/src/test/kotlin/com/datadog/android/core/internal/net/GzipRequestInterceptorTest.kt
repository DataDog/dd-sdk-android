/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.ByteArrayOutputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class GzipRequestInterceptorTest {
    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response
    lateinit var fakeBody: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeUrl = forge.aStringMatching("http://[a-z0-9_]{8}\\.[a-z]{3}")
        fakeBody = forge.anAlphabeticalString()
        fakeRequest = Request.Builder()
            .url(fakeUrl)
            .post(RequestBody.create(null, fakeBody.toByteArray()))
            .build()
        testedInterceptor = GzipRequestInterceptor(mockInternalLogger)
    }

    @Test
    fun `compress body when no encoding is used`() {
        fakeResponse = forgeResponse()
        stubChain()

        val response = testedInterceptor.intercept(mockChain)

        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val buffer = Buffer()
            val stream = ByteArrayOutputStream()
            lastValue.body()!!.writeTo(buffer)
            buffer.copyTo(stream)

            assertThat(stream.toString())
                .isNotEqualTo(fakeBody)

            assertThat(lastValue.header("Content-Encoding"))
                .isEqualTo("gzip")
        }
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    @Test
    fun `ignores body when encoding is set`() {
        fakeRequest = fakeRequest.newBuilder()
            .header("Content-Encoding", "identity")
            .build()
        fakeResponse = forgeResponse()
        stubChain()

        val response = testedInterceptor.intercept(mockChain)

        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val buffer = Buffer()
            val stream = ByteArrayOutputStream()
            lastValue.body()!!.writeTo(buffer)
            buffer.copyTo(stream)

            assertThat(stream.toString())
                .isEqualTo(fakeBody)
            assertThat(lastValue.header("Content-Encoding"))
                .isEqualTo("identity")
        }
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    @Test
    fun `M keep original body W intercept { MultipartBody }`(forge: Forge) {
        // Given
        val fakeMultipartBody = MultipartBody
            .Builder()
            .addFormDataPart(
                forge.aString(),
                forge.aString(),
                RequestBody.create(null, fakeBody.toByteArray())
            )
            .build()

        fakeRequest = fakeRequest.newBuilder()
            .post(fakeMultipartBody)
            .build()
        fakeResponse = forgeResponse()
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val buffer = Buffer()
            val stream = ByteArrayOutputStream()
            val part = (lastValue.body() as MultipartBody).part(0)
            part.body().writeTo(buffer)
            buffer.copyTo(stream)

            assertThat(stream.toString())
                .isEqualTo(fakeBody)
        }
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    @Test
    fun `ignores body when body is null`() {
        fakeRequest = fakeRequest.newBuilder()
            .get()
            .build()
        fakeResponse = forgeResponse()
        stubChain()

        val response = testedInterceptor.intercept(mockChain)

        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.body())
                .isNull()
            assertThat(lastValue.header("Content-Encoding"))
                .isNull()
        }
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    // region Internal

    private fun forgeResponse(): Response {
        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("{}")
        return builder.build()
    }

    private fun stubChain() {
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion
}

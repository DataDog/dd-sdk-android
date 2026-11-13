/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RickAndMortyFallbackInterceptorTest {

    @Mock
    lateinit var mockListener: RickAndMortyFallbackListener

    private lateinit var testedInterceptor: RickAndMortyFallbackInterceptor

    private var fakePort: Int = 0
    private var portProviderCalls = 0
    private var portProviderError: IOException? = null
    private lateinit var fakeApiPath: String
    private lateinit var fakeAvatarPath: String

    private val realOutcomes = mutableListOf<(Request) -> Response>()
    private val localOutcomes = mutableListOf<(Request) -> Response>()
    private val proceededRequests = mutableListOf<Request>()

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakePort = forge.anInt(min = 1024, max = 65535)
        fakeApiPath = "/api/" + forge.anElementFrom("character", "location", "episode") +
            "/" + forge.anInt(min = 1, max = 1000)
        fakeAvatarPath = "/api/character/avatar/${forge.anInt(min = 1, max = 1000)}.jpeg"
        testedInterceptor = RickAndMortyFallbackInterceptor(
            localPortProvider = {
                portProviderCalls++
                portProviderError?.let { throw it }
                fakePort
            },
            listener = mockListener
        )
    }

    @Test
    fun `M pass request through W intercept() {other host}`(forge: Forge) {
        // Given
        val request = request("https://${forge.anAlphabeticalString().lowercase()}.com$fakeApiPath")
        realOutcomes += { fail(it, 429) }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.code).isEqualTo(429)
        assertThat(proceededRequests).containsExactly(request)
        assertThat(portProviderCalls).isZero()
        verifyNoInteractions(mockListener)
    }

    @Test
    fun `M return real response W intercept() {2xx}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        realOutcomes += { success(it) }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.body?.string()).isEqualTo(REAL_BODY)
        assertThat(proceededRequests).containsExactly(request)
        assertThat(portProviderCalls).isZero()
        verifyNoInteractions(mockListener)
    }

    @Test
    fun `M retry on local server with same path and query W intercept() {API, IOException}`(
        @IntForgery(min = 1, max = 50) fakePage: Int
    ) {
        // Given
        val request = request("https://rickandmortyapi.com/api/character?page=$fakePage")
        realOutcomes += { throw IOException("boom") }
        localOutcomes += { success(it, LOCAL_BODY) }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.body?.string()).isEqualTo(LOCAL_BODY)
        assertThat(proceededRequests.map { it.url.toString() }).containsExactly(
            "https://rickandmortyapi.com/api/character?page=$fakePage",
            "http://127.0.0.1:$fakePort/api/character?page=$fakePage"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [404, 429, 500])
    fun `M close failed body and retry locally W intercept() {API, non-2xx}`(code: Int) {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        val failedBody = TrackedBody(REAL_BODY)
        realOutcomes += { fail(it, code, failedBody) }
        localOutcomes += { success(it, LOCAL_BODY) }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo(LOCAL_BODY)
        assertThat(failedBody.isClosed).isTrue()
        assertThat(proceededRequests.last().url.toString()).isEqualTo("http://127.0.0.1:$fakePort$fakeApiPath")
    }

    @Test
    fun `M skip real host W intercept() {API, after first fallback}`() {
        // Given
        val firstRequest = request("https://rickandmortyapi.com$fakeApiPath")
        val secondRequest = request("https://rickandmortyapi.com/api/episode?page=2")
        realOutcomes += { fail(it, 429) }
        localOutcomes += { success(it, LOCAL_BODY) }
        localOutcomes += { success(it, LOCAL_BODY) }
        testedInterceptor.intercept(chainFor(firstRequest))
        proceededRequests.clear()

        // When
        val response = testedInterceptor.intercept(chainFor(secondRequest))

        // Then
        assertThat(response.body?.string()).isEqualTo(LOCAL_BODY)
        assertThat(proceededRequests.map { it.url.toString() })
            .containsExactly("http://127.0.0.1:$fakePort/api/episode?page=2")
    }

    @Test
    fun `M try real host first W intercept() {avatar, after avatar fallback}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeAvatarPath")
        realOutcomes += { throw IOException("boom") }
        realOutcomes += { success(it) }
        localOutcomes += { success(it, LOCAL_BODY) }
        val firstResponse = testedInterceptor.intercept(chainFor(request))
        proceededRequests.clear()

        // When
        val secondResponse = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(firstResponse.body?.string()).isEqualTo(LOCAL_BODY)
        assertThat(secondResponse.body?.string()).isEqualTo(REAL_BODY)
        assertThat(proceededRequests).containsExactly(request)
    }

    @Test
    fun `M notify fallback activation once W intercept() {several API and avatar fallbacks}`() {
        // Given
        val apiRequest = request("https://rickandmortyapi.com$fakeApiPath")
        val avatarRequest = request("https://rickandmortyapi.com$fakeAvatarPath")
        realOutcomes += { fail(it, 429) }
        realOutcomes += { throw IOException("boom") }
        realOutcomes += { fail(it, 500) }
        repeat(4) { localOutcomes += { success(it, LOCAL_BODY) } }

        // When
        testedInterceptor.intercept(chainFor(apiRequest))
        testedInterceptor.intercept(chainFor(avatarRequest))
        testedInterceptor.intercept(chainFor(apiRequest))
        testedInterceptor.intercept(chainFor(avatarRequest))

        // Then
        verify(mockListener).onFallbackActivated()
    }

    @Test
    fun `M rethrow original exception W intercept() {IOException, server start fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        val originalError = IOException("real")
        val startError = IOException("start")
        portProviderError = startError
        realOutcomes += { throw originalError }

        // When
        val thrown = assertThrows<IOException> { testedInterceptor.intercept(chainFor(request)) }

        // Then
        assertThat(thrown).isSameAs(originalError)
        assertThat(proceededRequests).containsExactly(request)
        verify(mockListener).onFallbackFailed(startError)
        verify(mockListener, never()).onFallbackActivated()
    }

    @Test
    fun `M rethrow original exception W intercept() {IOException, local call fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeAvatarPath")
        val originalError = IOException("real")
        val localError = IOException("local")
        realOutcomes += { throw originalError }
        localOutcomes += { throw localError }

        // When
        val thrown = assertThrows<IOException> { testedInterceptor.intercept(chainFor(request)) }

        // Then
        assertThat(thrown).isSameAs(originalError)
        verify(mockListener).onFallbackFailed(localError)
        verify(mockListener, never()).onFallbackActivated()
    }

    @Test
    fun `M return original response W intercept() {non-2xx, server start fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        val failedBody = TrackedBody(REAL_BODY)
        portProviderError = IOException("start")
        realOutcomes += { fail(it, 429, failedBody) }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.code).isEqualTo(429)
        assertThat(response.body?.string()).isEqualTo(REAL_BODY)
        assertThat(proceededRequests).containsExactly(request)
    }

    @Test
    fun `M return original response W intercept() {non-2xx, local call fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        val localError = IOException("local")
        realOutcomes += { fail(it, 429) }
        localOutcomes += { throw localError }

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.code).isEqualTo(429)
        assertThat(response.body?.string()).isEqualTo(REAL_BODY)
        verify(mockListener).onFallbackFailed(localError)
    }

    @Test
    fun `M not make API sticky W intercept() {local call fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        realOutcomes += { fail(it, 429) }
        realOutcomes += { success(it) }
        localOutcomes += { throw IOException("local") }
        testedInterceptor.intercept(chainFor(request))
        proceededRequests.clear()

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.body?.string()).isEqualTo(REAL_BODY)
        assertThat(proceededRequests).containsExactly(request)
    }

    @Test
    fun `M fall back to real host W intercept() {sticky API, local call fails}`() {
        // Given
        val request = request("https://rickandmortyapi.com$fakeApiPath")
        val localError = IOException("local")
        realOutcomes += { fail(it, 429) }
        realOutcomes += { success(it) }
        localOutcomes += { success(it, LOCAL_BODY) }
        localOutcomes += { throw localError }
        testedInterceptor.intercept(chainFor(request))
        proceededRequests.clear()

        // When
        val response = testedInterceptor.intercept(chainFor(request))

        // Then
        assertThat(response.body?.string()).isEqualTo(REAL_BODY)
        assertThat(proceededRequests.map { it.url.host }).containsExactly("127.0.0.1", "rickandmortyapi.com")
        argumentCaptor<Throwable> {
            verify(mockListener).onFallbackFailed(capture())
            assertThat(firstValue).isSameAs(localError)
        }
    }

    private fun chainFor(request: Request): Interceptor.Chain = mock {
        on { request() } doReturn request
        on { proceed(any()) } doAnswer { invocation ->
            val proceeded = invocation.getArgument<Request>(0)
            proceededRequests += proceeded
            val outcomes = if (proceeded.url.host == "127.0.0.1") localOutcomes else realOutcomes
            check(outcomes.isNotEmpty()) { "Unexpected request ${proceeded.url}" }
            outcomes.removeAt(0).invoke(proceeded)
        }
    }

    private fun request(url: String) = Request.Builder().url(url).build()

    private fun success(request: Request, body: String = REAL_BODY) = response(request, 200, TrackedBody(body))

    private fun fail(request: Request, code: Int, body: TrackedBody = TrackedBody(REAL_BODY)) =
        response(request, code, body)

    private fun response(request: Request, code: Int, body: TrackedBody) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status $code")
        .body(body.responseBody)
        .build()

    private class TrackedBody(content: String) {
        var isClosed = false
            private set

        val responseBody = object : ForwardingSource(Buffer().writeUtf8(content)) {
            override fun close() {
                isClosed = true
                super.close()
            }
        }.buffer().asResponseBody(contentLength = content.length.toLong())
    }

    private companion object {
        const val REAL_BODY = "{\"source\":\"real\"}"
        const val LOCAL_BODY = "{\"source\":\"local\"}"
    }
}

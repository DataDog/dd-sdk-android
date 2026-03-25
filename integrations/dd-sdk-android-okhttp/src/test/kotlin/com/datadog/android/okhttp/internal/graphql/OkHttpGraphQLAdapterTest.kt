/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.graphql

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.internal.utils.toBase64
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.GraphQLExtractor
import com.datadog.android.tests.elmyr.anOkHttpRequest
import com.datadog.android.tests.elmyr.anOkHttpResponse
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class OkHttpGraphQLAdapterTest {

    @Mock
    lateinit var mockGraphQLExtractor: GraphQLExtractor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockChain: Interceptor.Chain

    lateinit var fakeRequest: Request

    lateinit var testedHelper: OkHttpGraphQLAdapter

    lateinit var forge: Forge

    @BeforeEach
    fun `set up`(forge: Forge) {
        this.forge = forge
        fakeRequest = forge.anOkHttpRequest()
        whenever(mockChain.request()) doAnswer { fakeRequest }
        testedHelper = OkHttpGraphQLAdapter(mockGraphQLExtractor)
    }

    // region wrapChainWithoutDDHeaders

    @Test
    fun `M strip GraphQL headers W wrapChainWithoutDDHeaders() {with DD headers}`(
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forge.anOkHttpRequest {
            addHeader("User-Agent", fakeUserAgent)
            addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
        }

        val response = forge.anOkHttpResponse(fakeRequest, 200) {
            body("""{"data":{}}""".toResponseBody(HttpSpec.ContentType.APPLICATION_JSON.toMediaType()))
        }

        whenever(mockChain.proceed(any())) doReturn response

        // When
        val wrappedChain = testedHelper.wrapChainWithoutDDHeaders(mockInternalLogger, mockChain)
        wrappedChain.proceed(fakeRequest)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
    }

    @Test
    fun `M return original chain W wrapChainWithoutDDHeaders() {without DD headers}`(
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forge.anOkHttpRequest { addHeader("User-Agent", fakeUserAgent) }

        // When
        val wrappedChain = testedHelper.wrapChainWithoutDDHeaders(mockInternalLogger, mockChain)

        // Then
        assertThat(wrappedChain).isSameAs(mockChain)
    }

    // endregion

    // region hasGraphQLHeaders

    @Test
    fun `M return true W hasGraphQLHeaders() {with any GraphQL header}`(
        @StringForgery fakeValue: String
    ) {
        // Given
        fakeRequest = forge.anOkHttpRequest {
            addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeValue)
        }

        // When
        val result = testedHelper.hasGraphQLHeaders(fakeRequest.headers)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W hasGraphQLHeaders() {without GraphQL headers}`() {
        // Given
        fakeRequest = forge.anOkHttpRequest { addHeader("User-Agent", "test") }

        // When
        val result = testedHelper.hasGraphQLHeaders(fakeRequest.headers)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region extractGraphQLAttributes

    @Test
    fun `M delegate to graphQLExtractor W extractGraphQLAttributes()`(
        @StringForgery fakeGraphQLName: String
    ) {
        // Given
        fakeRequest = forge.anOkHttpRequest {
            addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
        }
        val expectedAttributes = mapOf("key" to "value")
        whenever(mockGraphQLExtractor.extractGraphQLAttributes(any())) doReturn expectedAttributes

        // When
        val result = testedHelper.extractGraphQLAttributes(fakeRequest)

        // Then
        assertThat(result).isEqualTo(expectedAttributes)
        verify(mockGraphQLExtractor).extractGraphQLAttributes(any())
    }

    // endregion

    // region extractGraphQLErrorAttributes

    @Test
    fun `M return error attributes W extractGraphQLErrorAttributes() {graphql errors present}`(
        @StringForgery fakeErrorsJson: String
    ) {
        // Given
        val response = forge.anOkHttpResponse(fakeRequest, 200) {
            body(
                """{"errors":[{"message":"err"}]}""".toResponseBody(
                    HttpSpec.ContentType.APPLICATION_JSON.toMediaType()
                )
            )
            header("Content-Type", HttpSpec.ContentType.APPLICATION_JSON)
        }
        val graphqlAttributes = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to "GetUser")
        whenever(mockGraphQLExtractor.extractGraphQLErrors(any(), any(), any())) doReturn fakeErrorsJson

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, graphqlAttributes, mockInternalLogger)

        // Then
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_ERRORS, fakeErrorsJson)
    }

    @Test
    fun `M return empty map W extractGraphQLErrorAttributes() {no graphql errors}`() {
        // Given
        val response = forge.anOkHttpResponse(fakeRequest, 200) {
            body(
                """{"data":{"user":"John"}}""".toResponseBody(
                    HttpSpec.ContentType.APPLICATION_JSON.toMediaType()
                )
            )
            header("Content-Type", HttpSpec.ContentType.APPLICATION_JSON)
        }
        val graphqlAttributes = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to "GetUser")
        whenever(mockGraphQLExtractor.extractGraphQLErrors(any(), any(), any())) doReturn null

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, graphqlAttributes, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W extractGraphQLErrorAttributes() {empty graphql attributes}`() {
        // Given
        val response = forge.anOkHttpResponse(fakeRequest, 200)

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map and log W extractGraphQLErrorAttributes() {peekBody throws}`() {
        // Given
        val response = forge.anOkHttpResponse(fakeRequest, 200) {
            body(object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = -1L
                override fun source(): BufferedSource {
                    throw IOException("peekBody failed")
                }
            })
        }
        val graphqlAttributes = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to "GetUser")

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, graphqlAttributes, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            any<Throwable>(),
            any<Boolean>(),
            anyOrNull()
        )
    }

    // endregion
}

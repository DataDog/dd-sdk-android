/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.graphql

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.GraphQLExtractor
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class OkHttpGraphQLAdapterTest {

    @Mock
    lateinit var mockGraphQLExtractor: GraphQLExtractor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockChain: Interceptor.Chain

    lateinit var testedHelper: OkHttpGraphQLAdapter

    lateinit var forge: Forge

    @BeforeEach
    fun `set up`(forge: Forge) {
        this.forge = forge
        testedHelper = OkHttpGraphQLAdapter(mockGraphQLExtractor)
    }

    // region convertHeadersToTag

    @Test
    fun `M strip DD headers and attach tag W convertHeadersToTag() {graphql request}`(
        @StringForgery fakeOperationName: String
    ) {
        // Given
        val attrs = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to fakeOperationName)
        whenever(mockGraphQLExtractor.extractGraphQLAttributes(any())) doReturn attrs

        val request = Request.Builder()
            .url("https://example.com/graphql")
            .header(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "encoded-name")
            .header(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "encoded-type")
            .header(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, "encoded-vars")
            .header(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, "encoded-payload")
            .header("User-Agent", "test-agent")
            .build()
        val builder = request.newBuilder()

        // When
        testedHelper.convertHeadersToTag(request, builder)
        val result = builder.build()

        // Then
        GraphQLHeaders.entries.forEach {
            assertThat(result.header(it.headerValue)).isNull()
        }
        assertThat(result.header("User-Agent")).isEqualTo("test-agent")
        assertThat(result.tag(GraphQLAttributes::class.java)).isEqualTo(GraphQLAttributes(attrs))
    }

    @Test
    fun `M not modify builder W convertHeadersToTag() {empty attributes}`() {
        // Given
        whenever(mockGraphQLExtractor.extractGraphQLAttributes(any())) doReturn emptyMap()

        val request = Request.Builder()
            .url("https://example.com/api")
            .header("User-Agent", "test-agent")
            .build()
        val builder = request.newBuilder()

        // When
        testedHelper.convertHeadersToTag(request, builder)
        val result = builder.build()

        // Then
        assertThat(result.header("User-Agent")).isEqualTo("test-agent")
        assertThat(result.tag(GraphQLAttributes::class.java)).isNull()
    }

    // endregion

    // region readGraphQLAttributesFromTag

    @Test
    fun `M return tagged attributes W readGraphQLAttributesFromTag() {tag present}`(
        @StringForgery fakeOperationName: String
    ) {
        // Given
        val attrs = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to fakeOperationName)
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .tag(GraphQLAttributes::class.java, GraphQLAttributes(attrs))
            .build()

        // When
        val result = testedHelper.readGraphQLAttributesFromTag(request)

        // Then
        assertThat(result).isEqualTo(attrs)
    }

    @Test
    fun `M return empty map W readGraphQLAttributesFromTag() {no tag}`() {
        // Given
        val request = Request.Builder().url("https://example.com/api").build()

        // When
        val result = testedHelper.readGraphQLAttributesFromTag(request)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region extractGraphQLErrorAttributes

    @Test
    fun `M return error attributes W extractGraphQLErrorAttributes() {graphql errors present}`(
        @StringForgery fakeErrorsJson: String
    ) {
        // Given
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .build()
        val response = forge.anOkHttpResponse(request, 200) {
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
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .build()
        val response = forge.anOkHttpResponse(request, 200) {
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
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .build()
        val response = forge.anOkHttpResponse(request, 200)

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, emptyMap(), mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map and log W extractGraphQLErrorAttributes() {peekBody throws}`() {
        // Given
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .build()
        val response = forge.anOkHttpResponse(request, 200) {
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

    @Test
    fun `M return empty and skip extraction W extractGraphQLErrorAttributes() {streaming response}`(
        @StringForgery fakeOpName: String,
        @StringForgery fakeBody: String
    ) {
        // Given
        val streamContentType = forge.anElementFrom(
            HttpSpec.ContentType.values().filter(HttpSpec.ContentType::isStream)
        )
        val request = Request.Builder()
            .url("https://example.com/graphql")
            .build()

        val response = forge.anOkHttpResponse(request, 200) {
            body(fakeBody.toResponseBody(streamContentType.toMediaType()))
        }
        val graphqlAttributes = mapOf<String, Any?>(RumAttributes.GRAPHQL_OPERATION_NAME to fakeOpName)

        // When
        val result = testedHelper.extractGraphQLErrorAttributes(response, graphqlAttributes, mockInternalLogger)

        // Then
        assertThat(result).isEmpty()
        verify(mockGraphQLExtractor, never()).extractGraphQLErrors(any(), any(), any())
    }

    // endregion
}

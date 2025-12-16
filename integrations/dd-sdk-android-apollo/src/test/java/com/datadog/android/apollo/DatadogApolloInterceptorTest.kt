/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.apollo

import android.util.Base64
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.datadog.android.apollo.internal.VariablesExtractor
import com.datadog.android.internal.network.GraphQLHeaders
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@Suppress("UnusedFlow")
internal class DatadogApolloInterceptorTest {

    @Mock
    private lateinit var mockScalarAdapters: CustomScalarAdapters

    @Mock
    private lateinit var mockExecutionContext: ExecutionContext

    @Mock
    private lateinit var mockVariablesExtractor: VariablesExtractor

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeUserId: String

    @StringForgery(regex = "[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+")
    lateinit var fakeUserName: String

    @StringForgery(regex = "[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
    lateinit var fakeUserEmail: String

    private lateinit var testedInterceptor: DatadogApolloInterceptor

    @BeforeEach
    fun setUp() {
        testedInterceptor = setupApolloInterceptor(true)
    }

    // region header: name

    @Test
    fun `M add name header W intercept`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "GetUser")
    }

    // endregion

    // region header: type

    @Test
    fun `M add type header W intercept { type query }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "query")
    }

    @Test
    fun `M add type header W intercept { type mutation }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("SetUser", "mutation SetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "mutation")
    }

    @Test
    fun `M add type header W intercept { type subscription }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks(
            "CreateUser",
            "subscription CreateUser { user { id: name } }"
        )
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "subscription")
    }

    @Test
    fun `M not add type header W intercept { unknown type }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks(
            "CreateUser",
            "unknown CreateUser { user { id: name } }"
        )
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)
    }

    // endregion

    // region header: variables

    @Test
    fun `M add variables header W intercept`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks(
            "GetUser",
            "query GetUser { user { id: name } }"
        )
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(
            requestBuilder,
            GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue,
            "{userId: $fakeUserId, filters: [active]}"
        )
    }

    // endregion

    // region header: payload

    @Test
    fun `M add payload header W intercept`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    // endregion

    // region error handling

    @Test
    fun `M handle null variables W intercept { variables extractor returns null }`() {
        // Given
        val nullVariablesExtractor = mock<VariablesExtractor>()
        whenever(nullVariablesExtractor.extractVariables(any(), any())).thenReturn(null)

        val interceptor = DatadogApolloInterceptor(
            sendGraphQLPayloads = true,
            variablesExtractor = nullVariablesExtractor
        )

        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        interceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "GetUser")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "query")
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue)
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    @Test
    fun `M handle empty variables W intercept { variables extractor returns empty string }`() {
        // Given
        val emptyVariablesExtractor = mock<VariablesExtractor>()
        whenever(emptyVariablesExtractor.extractVariables(any(), any())).thenReturn("")

        val interceptor = DatadogApolloInterceptor(
            sendGraphQLPayloads = true,
            variablesExtractor = emptyVariablesExtractor
        )

        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        interceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "GetUser")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "query")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, "")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    // endregion

    // region edge cases

    @Test
    fun `M handle empty document W intercept { empty document }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "GetUser")
        checkHeaderWasAdded(
            requestBuilder,
            GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue,
            "{userId: $fakeUserId, filters: [active]}"
        )
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue)
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    @Test
    fun `M handle document with multiple operation types W intercept { query and mutation }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks(
            "ComplexOperation",
            "query GetUser { user { id } } mutation SetUser { user { id } }"
        )
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "ComplexOperation")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "mutation")
        checkHeaderWasAdded(
            requestBuilder,
            GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue,
            "{userId: $fakeUserId, filters: [active]}"
        )
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    @Test
    fun `M send null payload W intercept { payload flag not provided }`() {
        // Given
        testedInterceptor = setupApolloInterceptor(false)
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "")
        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    // endregion

    @Test
    fun `M proceed with modified request W intercept { chain interaction }`() {
        // Given
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")

        val modifiedRequest = mock<ApolloRequest<Operation.Data>>()
        whenever(requestBuilder.build()).thenReturn(modifiedRequest)

        val chain = mock<ApolloInterceptorChain>()

        // When
        testedInterceptor.intercept(originalRequest, chain)

        // Then
        verify(chain).proceed(modifiedRequest)
    }

    @Test
    fun `M use default variables extractor W intercept { no custom extractor provided }`() {
        // Given
        val defaultInterceptor = DatadogApolloInterceptor()

        val operation = mock<Operation<Operation.Data>>()
        whenever(operation.name()).thenReturn("GetUser")
        whenever(operation.document()).thenReturn("query GetUser { user { id: name } }")

        val requestBuilder = mock<ApolloRequest.Builder<Operation.Data>>()
        whenever(requestBuilder.addHttpHeader(any<String>(), any<String>())).thenReturn(requestBuilder)
        whenever(requestBuilder.build()).thenReturn(mock())

        whenever(mockExecutionContext[CustomScalarAdapters.Key]).thenReturn(mockScalarAdapters)

        val originalRequest = mock<ApolloRequest<Operation.Data>>()
        whenever(originalRequest.operation).thenReturn(operation)
        whenever(originalRequest.newBuilder()).thenReturn(requestBuilder)
        whenever(originalRequest.executionContext).thenReturn(mockExecutionContext)

        val chain = mock<ApolloInterceptorChain>()

        // When
        defaultInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "GetUser")
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "query")
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    @Test
    fun `M not send payload W intercept { default constructor used }`() {
        // Given
        val defaultInterceptor = DatadogApolloInterceptor()
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        defaultInterceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    // region constructor tests

    @Test
    fun `M send payload W intercept { sendGraphQLPayloads true }`() {
        // Given
        val interceptor = DatadogApolloInterceptor(sendGraphQLPayloads = true)
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        interceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    @Test
    fun `M not send payload W intercept { sendGraphQLPayloads false }`() {
        // Given
        val interceptor = DatadogApolloInterceptor(sendGraphQLPayloads = false)
        val (_, originalRequest, requestBuilder) = setupBasicMocks("GetUser", "query GetUser { user { id: name } }")
        val chain = mock<ApolloInterceptorChain>()

        // When
        interceptor.intercept(originalRequest, chain)

        // Then
        checkHeaderWasNotAdded(requestBuilder, GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue)
    }

    // endregion

    // region helper methods

    private fun setupBasicMocks(
        operationName: String,
        operationDocument: String
    ): Triple<Operation<Operation.Data>, ApolloRequest<Operation.Data>, ApolloRequest.Builder<Operation.Data>> {
        val operation = mock<Operation<Operation.Data>>()
        whenever(operation.name()).thenReturn(operationName)
        whenever(operation.document()).thenReturn(operationDocument)

        val requestBuilder = mock<ApolloRequest.Builder<Operation.Data>>()
        whenever(requestBuilder.addHttpHeader(any<String>(), any<String>())).thenReturn(requestBuilder)
        whenever(requestBuilder.build()).thenReturn(mock())

        whenever(mockExecutionContext[CustomScalarAdapters.Key]).thenReturn(mockScalarAdapters)

        val originalRequest = mock<ApolloRequest<Operation.Data>>()
        whenever(originalRequest.operation).thenReturn(operation)
        whenever(originalRequest.newBuilder()).thenReturn(requestBuilder)
        whenever(originalRequest.executionContext).thenReturn(mockExecutionContext)

        return Triple(operation, originalRequest, requestBuilder)
    }

    private fun checkHeaderWasAdded(
        requestBuilder: ApolloRequest.Builder<Operation.Data>,
        headerName: String,
        expectedHeaderValue: String? = null
    ) {
        if (expectedHeaderValue != null) {
            val expectedBase64Value = expectedHeaderValue.toBase64()
            verify(requestBuilder).addHttpHeader(eq(headerName), eq(expectedBase64Value))
        } else {
            verify(requestBuilder).addHttpHeader(eq(headerName), any<String>())
        }
    }

    private fun String.toBase64(): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun checkHeaderWasNotAdded(
        requestBuilder: ApolloRequest.Builder<Operation.Data>,
        headerName: String
    ) {
        verify(requestBuilder, never()).addHttpHeader(eq(headerName), any<String>())
    }

    private fun setupApolloInterceptor(sendGraphQLPayloads: Boolean): DatadogApolloInterceptor {
        // Setup mock variables extractor to return predictable test data
        whenever(mockVariablesExtractor.extractVariables(any(), any())).thenAnswer { invocation ->
            val operation = invocation.getArgument<Operation<*>>(0)
            when (operation.name()) {
                "GetUser" -> "{userId: $fakeUserId, filters: [active]}"
                "SetUser" -> "{userId: $fakeUserId, filters: [active]}"
                "CreateUser" -> "{input: {name: $fakeUserName, email: $fakeUserEmail}}"
                "ComplexOperation" -> "{userId: $fakeUserId, filters: [active]}"
                else -> "{}"
            }
        }

        return DatadogApolloInterceptor(
            sendGraphQLPayloads = sendGraphQLPayloads,
            variablesExtractor = mockVariablesExtractor
        )
    }

    // endregion
}

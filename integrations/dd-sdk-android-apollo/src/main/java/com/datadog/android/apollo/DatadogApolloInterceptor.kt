/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.apollo

import android.util.Base64
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.composeJsonRequest
import com.apollographql.apollo.api.json.buildJsonString
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.datadog.android.apollo.internal.DefaultVariablesExtractor
import com.datadog.android.apollo.internal.VariablesExtractor
import com.datadog.android.internal.network.GraphQLHeaders
import kotlinx.coroutines.flow.Flow

/**
 * A Datadog Apollo interceptor for GraphQL operations.
 * @param sendGraphQLPayloads Should GraphQL payloads be reported or not. This is disabled by default.
 */
class DatadogApolloInterceptor(
    private val sendGraphQLPayloads: Boolean = false
) : ApolloInterceptor {

    private var variablesExtractor: VariablesExtractor = DefaultVariablesExtractor()

    /**
     * Internal constructor for testing purposes.
     * @param sendGraphQLPayloads should graphQL payloads be reported. This is disabled by default.
     * @param variablesExtractor custom variables extractor for testing
     */
    internal constructor(
        sendGraphQLPayloads: Boolean = false,
        variablesExtractor: VariablesExtractor
    ) : this(sendGraphQLPayloads) {
        this.variablesExtractor = variablesExtractor
    }

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val adapters = request.executionContext[CustomScalarAdapters.Key] ?: CustomScalarAdapters.Empty
        val operation = request.operation

        val operationName = operation.name().toBase64()
        val operationType = extractType(operation)?.toBase64()
        val operationVariables = variablesExtractor.extractVariables(operation, adapters)?.toBase64()

        val requestBuilder = request.newBuilder()
        requestBuilder
            .addHttpHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, operationName)

        operationType?.let {
            requestBuilder.addHttpHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, operationType)
        }

        operationVariables?.let {
            requestBuilder.addHttpHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, operationVariables)
        }

        if (sendGraphQLPayloads) {
            val operationPayload = extractPayload(operation, adapters).toBase64()
            requestBuilder.addHttpHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, operationPayload)
        }

        val newRequest = requestBuilder.build()

        return chain.proceed(newRequest)
    }

    private fun <D : Operation.Data> extractType(operation: Operation<D>) =
        when {
            operation.document().contains("mutation") -> "mutation"
            // subscriptions not currently tracked
            operation.document().contains("subscription") -> "subscription"
            operation.document().contains("query") -> "query"
            else -> null // failed to get type
        }

    private fun <D : Operation.Data> extractPayload(operation: Operation<D>, adapters: CustomScalarAdapters) =
        buildJsonString {
            operation.composeJsonRequest(this, adapters)
        }

    private fun String.toBase64(): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        @Suppress("UnsafeThirdPartyFunctionCall") // cannot throw UnsupportedEncodingException
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.apollo

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.composeJsonRequest
import com.apollographql.apollo.api.json.buildJsonString
import com.apollographql.apollo.api.variablesJson
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import kotlinx.coroutines.flow.Flow
import okio.IOException

/**
 * A Datadog Apollo interceptor for GraphQL operations.
 */
open class DatadogApolloInterceptor : ApolloInterceptor {

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val adapters = request.executionContext[CustomScalarAdapters.Key] ?: CustomScalarAdapters.Empty
        val operation = request.operation

        val operationName = operation.name()
        val operationType = extractType(operation)
        val operationVariables = extractVariables(operation, adapters)
        val operationPayload = extractPayload(operation, adapters)

        val requestBuilder = request.newBuilder()
        requestBuilder
            .addHttpHeader(DD_GRAPHQL_NAME_HEADER, operationName)

        operationType?.let {
            requestBuilder.addHttpHeader(DD_GRAPHQL_TYPE_HEADER, operationType)
        }

        operationVariables?.let {
            requestBuilder.addHttpHeader(DD_GRAPHQL_VARIABLES_HEADER, operationVariables)
        }

        operationPayload.let {
            requestBuilder.addHttpHeader(DD_GRAPHQL_PAYLOAD_HEADER, operationPayload)
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

    internal open fun extractVariables(operation: Operation<*>, adapters: CustomScalarAdapters): String? {
        return try {
            operation.variablesJson(adapters)
        } catch (_: IOException) {
            null
        }
    }

    private fun <D : Operation.Data> extractPayload(operation: Operation<D>, adapters: CustomScalarAdapters) =
        buildJsonString {
            operation.composeJsonRequest(this, adapters)
        }

    internal companion object {
        const val DD_GRAPHQL_NAME_HEADER = "_dd.graphql.operation_name"
        const val DD_GRAPHQL_VARIABLES_HEADER = "_dd.graphql.variables"
        const val DD_GRAPHQL_TYPE_HEADER = "_dd.graphql.operation_type"
        const val DD_GRAPHQL_PAYLOAD_HEADER = "_dd.graphql.payload"
    }
}

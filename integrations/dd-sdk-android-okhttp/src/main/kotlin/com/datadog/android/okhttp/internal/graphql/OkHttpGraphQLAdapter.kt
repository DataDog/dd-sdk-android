/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.graphql

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.internal.OkHttpRequestInfo
import com.datadog.android.okhttp.internal.OkHttpResponseInfo
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.GraphQLExtractor
import okhttp3.Request
import okhttp3.Response

internal class OkHttpGraphQLAdapter(
    private val graphQLExtractor: GraphQLExtractor = GraphQLExtractor()
) {

    fun convertHeadersToTag(request: Request, builder: Request.Builder) {
        val attributes = graphQLExtractor.extractGraphQLAttributes(OkHttpRequestInfo(request))
        if (attributes.isEmpty()) return

        GraphQLHeaders.values().forEach { builder.removeHeader(it.headerValue) }
        @Suppress("UnsafeThirdPartyFunctionCall") // ClassCastException can't happen here.
        builder.tag(GraphQLAttributes::class.java, GraphQLAttributes(attributes))
    }

    fun readGraphQLAttributesFromTag(request: Request): Map<String, Any?> =
        request.tag(GraphQLAttributes::class.java)?.attributes.orEmpty()

    @WorkerThread
    @Suppress("ReturnCount")
    fun extractGraphQLErrorAttributes(
        response: Response,
        graphQLAttributes: Map<String, Any?>,
        internalLogger: InternalLogger
    ): Map<String, Any> {
        if (graphQLAttributes.isEmpty()) return emptyMap()
        // Streaming responses surface GraphQL errors per-frame, not as a top-level `errors` array.
        // Draining their bodies via peekBody().string() would block until the (potentially unbounded) body completes.
        val body = response.body
        val mimeType = body?.contentType()?.let { it.type + "/" + it.subtype }
        val isStream = HttpSpec.ContentType.isStream(mimeType)
        val isWebSocket = !response.header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null).isNullOrBlank()
        if (body == null || isStream || isWebSocket) return emptyMap()

        return try {
            val responseInfo = OkHttpResponseInfo(response, internalLogger)

            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught
            val bodyString = response
                .peekBody(GraphQLExtractor.MAX_GRAPHQL_BODY_PEEK)
                .string()
            graphQLExtractor.extractGraphQLErrors(
                responseInfo.contentType,
                bodyString,
                internalLogger
            )?.let {
                mapOf(RumAttributes.GRAPHQL_ERRORS to it)
            } ?: emptyMap()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY_GRAPHQL },
                e
            )
            emptyMap()
        }
    }

    internal companion object {
        internal const val ERROR_PEEK_BODY_GRAPHQL =
            "Failed to peek response body for GraphQL errors."
    }
}

internal data class GraphQLAttributes(
    val attributes: Map<String, Any?>
)

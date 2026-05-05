/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.graphql

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.okhttp.internal.OkHttpRequestInfo
import com.datadog.android.okhttp.internal.OkHttpResponseInfo
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.GraphQLExtractor
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal class OkHttpGraphQLAdapter(
    private val graphQLExtractor: GraphQLExtractor = GraphQLExtractor()
) {

    fun wrapChainWithoutDDHeaders(
        internalLogger: InternalLogger,
        originalChain: Interceptor.Chain
    ): Interceptor.Chain {
        return if (hasGraphQLHeaders(originalChain.request().headers)) {
            object : Interceptor.Chain by originalChain {
                override fun proceed(request: Request): Response {
                    return try {
                        val cleanedRequest = request.newBuilder().apply {
                            removeGraphQLHeaders(this)
                        }.build()
                        return originalChain.proceed(cleanedRequest)
                    } catch (e: IllegalStateException) {
                        internalLogger.log(
                            level = InternalLogger.Level.WARN,
                            target = InternalLogger.Target.MAINTAINER,
                            messageBuilder = { ERROR_FAILED_BUILD_REQUEST },
                            throwable = e
                        )
                        originalChain.proceed(request) // fallback to the original request
                    } catch (e: IOException) {
                        internalLogger.log(
                            level = InternalLogger.Level.WARN,
                            target = InternalLogger.Target.MAINTAINER,
                            messageBuilder = { ERROR_FAILED_BUILD_REQUEST },
                            throwable = e
                        )
                        originalChain.proceed(request) // fallback to the original request
                    }
                }
            }
        } else {
            originalChain
        }
    }

    fun extractGraphQLAttributes(request: Request): Map<String, Any?> =
        graphQLExtractor.extractGraphQLAttributes(OkHttpRequestInfo(request))

    @WorkerThread
    fun extractGraphQLErrorAttributes(
        response: Response,
        graphqlAttributes: Map<String, Any?>,
        internalLogger: InternalLogger
    ): Map<String, Any> {
        if (graphqlAttributes.isEmpty()) return emptyMap()
        return try {
            val responseInfo = OkHttpResponseInfo(response, internalLogger)

            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught
            val body = response
                .peekBody(GraphQLExtractor.MAX_GRAPHQL_BODY_PEEK)
                .string()
            graphQLExtractor.extractGraphQLErrors(
                responseInfo.contentType,
                body,
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

    internal fun hasGraphQLHeaders(headers: Headers): Boolean =
        GraphQLHeaders.values().any { headers[it.headerValue] != null }

    private fun removeGraphQLHeaders(requestBuilder: Request.Builder) {
        GraphQLHeaders.values().forEach { requestBuilder.removeHeader(it.headerValue) }
    }

    internal companion object {
        internal const val ERROR_FAILED_BUILD_REQUEST =
            "Failed to build interceptor chain after removing DD headers. Falling back to original chain."

        internal const val ERROR_PEEK_BODY_GRAPHQL =
            "Failed to peek response body for GraphQL errors."
    }
}

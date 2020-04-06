/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.Interceptor
import okhttp3.Response

internal open class CombinedInterceptor(
    private val requestInterceptors: List<RequestInterceptor>
) : Interceptor {

    internal constructor(requestInterceptor: RequestInterceptor) : this(listOf(requestInterceptor))

    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response {

        val transformedRequest = requestInterceptors
            .fold(chain.request()) { request, interceptor ->
                interceptor.transformRequest(request)
            }

        val result = try {
            val response = chain.proceed(transformedRequest)
            Result.success(response)
        } catch (e: Throwable) {
            Result.failure<Response>(e)
        }

        return result
            .onSuccess { response ->
                requestInterceptors.forEach { it.handleResponse(transformedRequest, response) }
            }
            .onFailure { throwable ->
                requestInterceptors.forEach { it.handleThrowable(transformedRequest, throwable) }
            }
            .getOrThrow()
    }
}

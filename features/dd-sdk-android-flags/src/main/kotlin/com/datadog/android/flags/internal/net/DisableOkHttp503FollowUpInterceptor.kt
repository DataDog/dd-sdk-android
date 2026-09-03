/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.internal.network.HttpSpec
import okhttp3.Interceptor
import okhttp3.Response

/** Prevents OkHttp from retrying an HTTP 503 response before the SDK applies its retry policy. */
internal object DisableOkHttp503FollowUpInterceptor : Interceptor {

    @Suppress("UnsafeThirdPartyFunctionCall")
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val retryAfter = if (response.code == HttpSpec.StatusCode.SERVICE_UNAVAILABLE) {
            response.header(HttpSpec.Header.RETRY_AFTER)
        } else {
            null
        }
        val requestsImmediateRetry = !retryAfter.isNullOrEmpty() && retryAfter.all { it == '0' }
        return if (requestsImmediateRetry) {
            // A zero delay adds nothing to the SDK backoff. Removing it preserves SDK behavior and stops
            // OkHttp from replaying the request inside this call.
            response.newBuilder()
                .removeHeader(HttpSpec.Header.RETRY_AFTER)
                .build()
        } else {
            response
        }
    }
}

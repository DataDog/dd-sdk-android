/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.internal.net.toAttributesMap
import okhttp3.Interceptor
import okhttp3.Response

internal class RumInstrumentationOkHttpAdapter(
    private val rumNetworkInstrumentation: RumNetworkInstrumentation,
    private val registry: RequestTracingStateRegistry
) : Interceptor {
    private val internalLogger: InternalLogger
        get() = (rumNetworkInstrumentation.sdkCoreReference.get() as? FeatureSdkCore)?.internalLogger
            ?: InternalLogger.UNBOUND

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val requestInfo = registry.get(call)?.createModifiedRequestInfo()
        val okHttpRequest = (requestInfo as? OkHttpRequestInfo)?.originalRequest

        if (okHttpRequest == null) {
            rumNetworkInstrumentation.reportInstrumentationError { "OkHttp request is missed" }
            @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException
            return chain.proceed(call.request())
        }

        rumNetworkInstrumentation.startResource(requestInfo)
        return try {
            chain.proceed(okHttpRequest).also { response ->
                rumNetworkInstrumentation.stopResource(
                    requestInfo = requestInfo,
                    responseInfo = OkHttpHttpResponseInfo(response, internalLogger),
                    attributes = registry.get(call).toAttributesMap(
                        RumAttributes.TRACE_ID,
                        RumAttributes.SPAN_ID,
                        RumAttributes.RULE_PSR
                    )
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            rumNetworkInstrumentation.stopResourceWithError(requestInfo, e)
            @Suppress("ThrowingInternalException")
            throw e
        }
    }
}

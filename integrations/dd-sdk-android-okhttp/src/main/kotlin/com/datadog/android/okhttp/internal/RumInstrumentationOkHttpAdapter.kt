/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.toAttributesMap
import okhttp3.Interceptor
import okhttp3.Response

internal class RumInstrumentationOkHttpAdapter(
    private val registry: RequestTracingStateRegistry,
    private val rumNetworkInstrumentation: RumNetworkInstrumentation,
    private val distributedTracingInstrumentation: ApmNetworkInstrumentation?
) : Interceptor {
    private val internalLogger: InternalLogger
        get() = (rumNetworkInstrumentation.sdkCore as? FeatureSdkCore)?.internalLogger
            ?: InternalLogger.UNBOUND

    @WorkerThread
    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()
        // Request might be changed by customer's upstream interceptor(s)
        val okHttpRequest = registry.restoreUUIDTag(call, request)
        if (okHttpRequest == null) {
            rumNetworkInstrumentation.reportInstrumentationError { "OkHttp request wasn't instrumented" }
            @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException
            return chain.proceed(request)
        }

        val distributedTracingInstrumentation = distributedTracingInstrumentation
        val distributedTracingState = distributedTracingInstrumentation?.onRequest(okHttpRequest.toHttpRequestInfo())
        val finalOkHttpRequest = distributedTracingState?.createRequestInfo()?.toOkHttpRequest() ?: okHttpRequest
        val requestInfo = finalOkHttpRequest.toHttpRequestInfo()

        return try {
            rumNetworkInstrumentation.startResource(requestInfo)
            chain.proceed(finalOkHttpRequest).also { okHttpResponse ->
                val responseInfo = okHttpResponse.toHttpResponseInfo(internalLogger)

                if (distributedTracingState != null) {
                    distributedTracingInstrumentation.onResponseSucceeded(
                        response = responseInfo,
                        requestTracingState = distributedTracingState
                    )
                }

                val apmTracingState = registry.get(call)
                val tracingAttributes = (distributedTracingState ?: apmTracingState)
                    .toAttributesMap(
                        RumAttributes.TRACE_ID,
                        RumAttributes.SPAN_ID,
                        RumAttributes.RULE_PSR
                    )

                rumNetworkInstrumentation.stopResource(
                    requestInfo = requestInfo,
                    responseInfo = responseInfo,
                    attributes = tracingAttributes
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            if (distributedTracingState != null) {
                distributedTracingInstrumentation.onResponseFailed(
                    requestTracingState = distributedTracingState,
                    throwable = e
                )
            }

            rumNetworkInstrumentation.stopResourceWithError(requestInfo, e)
            @Suppress("ThrowingInternalException")
            throw e
        }
    }
}

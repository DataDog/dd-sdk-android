/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.telemetry.TracingHeaderTypesSet
import com.datadog.android.okhttp.internal.trace.toInternalTracingHeaderType
import com.datadog.android.okhttp.trace.TracingInterceptor.Companion.OKHTTP_INTERCEPTOR_HEADER_TYPES
import com.datadog.android.okhttp.trace.TracingInterceptor.Companion.OKHTTP_INTERCEPTOR_SAMPLE_RATE
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import okhttp3.Interceptor
import okhttp3.Response

internal class ApmInstrumentationOkHttpAdapter(
    private val apmNetworkInstrumentation: ApmNetworkInstrumentation,
    private val registry: RequestTracingStateRegistry
) : Interceptor {
    private val internalLogger: InternalLogger
        get() = (apmNetworkInstrumentation.sdkCoreReference.get() as? FeatureSdkCore)?.internalLogger
            ?: InternalLogger.UNBOUND

    init {
        val sdkCore = apmNetworkInstrumentation.sdkCoreReference.get() as? FeatureSdkCore
        // update meta for the configuration telemetry reporting, can be done directly from this thread
        sdkCore?.updateFeatureContext(Feature.TRACING_FEATURE_NAME, useContextThread = false) {
            it[OKHTTP_INTERCEPTOR_SAMPLE_RATE] = apmNetworkInstrumentation.sampleRate
            it[OKHTTP_INTERCEPTOR_HEADER_TYPES] = TracingHeaderTypesSet(
                types = apmNetworkInstrumentation.localHeaderTypes
                    .map(TracingHeaderType::toInternalTracingHeaderType)
                    .toSet()
            )
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()
        val tracingState = registry.update(call) { _, state ->
            apmNetworkInstrumentation.onRequest(state.createModifiedRequestInfo())
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException
        if (tracingState == null) return chain.proceed(request)

        val processedRequest = (tracingState.createModifiedRequestInfo() as? OkHttpRequestInfo)?.originalRequest ?: request

        return try {
            chain.proceed(processedRequest).also { response ->
                apmNetworkInstrumentation.onResponseSucceeded(
                    requestTracingState = tracingState,
                    response = OkHttpHttpResponseInfo(response, internalLogger)
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            apmNetworkInstrumentation.onResponseFailed(tracingState, e)
            @Suppress("ThrowingInternalException")
            throw e
        }
    }
}

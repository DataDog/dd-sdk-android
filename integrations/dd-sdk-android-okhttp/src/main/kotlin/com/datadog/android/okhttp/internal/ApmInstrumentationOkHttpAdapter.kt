/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.SdkReference
import com.datadog.android.internal.telemetry.TracingHeaderTypesSet
import com.datadog.android.okhttp.internal.trace.toTelemetryTracingHeaderType
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
    private val sdkCoreReference: SdkReference = SdkReference(apmNetworkInstrumentation.sdkInstanceName) {
        val sdkCore = it as InternalSdkCore
        // update meta for the configuration telemetry reporting, can be done directly from this thread
        sdkCore.updateFeatureContext(Feature.TRACING_FEATURE_NAME, useContextThread = false) { context ->
            context[OKHTTP_INTERCEPTOR_SAMPLE_RATE] = apmNetworkInstrumentation.sampleRate
            context[OKHTTP_INTERCEPTOR_HEADER_TYPES] = TracingHeaderTypesSet(
                types = apmNetworkInstrumentation.localHeaderTypes
                    .map(TracingHeaderType::toTelemetryTracingHeaderType)
                    .toSet()
            )
        }
    }

    private val internalLogger: InternalLogger?
        get() = (sdkCoreReference.get() as? FeatureSdkCore)?.internalLogger

    @Suppress("ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()
        // Request might be changed by customer's upstream interceptor(s)
        val taggedOkHttpRequest = registry.restoreUUIDTag(call, request)
        if (taggedOkHttpRequest == null) {
            apmNetworkInstrumentation.reportInstrumentationError { "OkHttp request wasn't instrumented" }
            @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException
            return chain.proceed(request)
        }

        val tracingState = apmNetworkInstrumentation.onRequest(taggedOkHttpRequest.toHttpRequestInfo())
            ?.also { registry.setTracingState(call, it) }

        @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException
        if (tracingState == null) {
            return chain.proceed(request)
        }

        val processedOkHttpRequest = tracingState.createRequestInfo().toOkHttpRequest() ?: taggedOkHttpRequest

        return try {
            chain.proceed(processedOkHttpRequest).also { okHttpResponse ->
                apmNetworkInstrumentation.onResponseSucceeded(
                    requestTracingState = tracingState,
                    response = okHttpResponse.toHttpResponseInfo(internalLogger ?: InternalLogger.UNBOUND)
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            apmNetworkInstrumentation.onResponseFailed(tracingState, e)
            @Suppress("ThrowingInternalException")
            throw e
        }
    }
}

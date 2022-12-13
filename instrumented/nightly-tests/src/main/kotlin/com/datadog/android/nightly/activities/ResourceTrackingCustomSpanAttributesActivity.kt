/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import com.datadog.android.rum.RumInterceptor
import com.datadog.android.tracing.TracedRequestListener
import com.datadog.android.tracing.TracingInterceptor
import io.opentracing.Span
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class ResourceTrackingCustomSpanAttributesActivity : ResourceTrackingActivity() {
    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RumInterceptor(traceSamplingRate = HUNDRED_PERCENT))
            .addNetworkInterceptor(
                TracingInterceptor(
                    listOf(HOST),
                    tracedRequestListener = object : TracedRequestListener {
                        override fun onRequestIntercepted(
                            request: Request,
                            span: Span,
                            response: Response?,
                            throwable: Throwable?
                        ) {
                            span.setOperationName(TEST_METHOD_NAME)
                        }
                    },
                    traceSamplingRate = HUNDRED_PERCENT
                )
            )
            .build()
    }

    override val randomUrl: String = RANDOM_RESOURCE_WITH_CUSTOM_SPAN

    companion object {
        internal const val TEST_METHOD_NAME = "rum_resource_tracking_with_custom_span_attributes"
        internal const val RANDOM_RESOURCE_WITH_CUSTOM_SPAN = "https://$HOST/25/25"
    }
}

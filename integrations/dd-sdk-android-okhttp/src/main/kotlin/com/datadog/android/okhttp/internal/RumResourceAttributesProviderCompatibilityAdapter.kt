/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.core.SdkReference
import com.datadog.android.rum.RumResourceAttributesProvider
import okhttp3.Request
import okhttp3.Response

internal class RumResourceAttributesProviderCompatibilityAdapter(
    internal val delegate: RumResourceAttributesProvider,
    internal val sdkReference: SdkReference
) : RumResourceAttributesProvider by delegate {

    private val internalLogger: InternalLogger
        get() = (sdkReference.get() as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND

    @Deprecated(
        "Use the variant with HttpRequestInfo/HttpResponseInfo instead",
        replaceWith = ReplaceWith(
            "onProvideAttributes(OkHttpHttpRequestInfo(request), " +
                "OkHttpHttpResponseInfo(response, internalLogger), throwable)"
        )
    )
    override fun onProvideAttributes(
        request: Request,
        response: Response?,
        throwable: Throwable?
    ): Map<String, Any?> {
        @Suppress("DEPRECATION")
        return delegate.onProvideAttributes(request, response, throwable).ifEmpty {
            delegate.onProvideAttributes(
                OkHttpHttpRequestInfo(request),
                response?.let { OkHttpHttpResponseInfo(it, internalLogger) },
                throwable
            )
        }
    }

    override fun onProvideAttributes(
        request: HttpRequestInfo,
        response: HttpResponseInfo?,
        throwable: Throwable?
    ): Map<String, Any?> {
        return delegate.onProvideAttributes(request, response, throwable).ifEmpty {
            @Suppress("DEPRECATION")
            delegate.onProvideAttributes(
                request = (request as? OkHttpHttpRequestInfo)?.request ?: return emptyMap(),
                response = (response as? OkHttpHttpResponseInfo)?.response,
                throwable = throwable
            )
        }
    }
}

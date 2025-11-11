/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.api.instrumentation.network.ResponseInfo
import com.datadog.android.rum.RumResourceAttributesProvider
import okhttp3.Request
import okhttp3.Response

internal class RumResourceAttributesProviderCompatibilityAdapter(
    internal val delegate: RumResourceAttributesProvider
) : RumResourceAttributesProvider {

    @Deprecated("Use the variant with RequestInfo/ResponseInfo instead")
    override fun onProvideAttributes(
        request: Request,
        response: Response?,
        throwable: Throwable?
    ): Map<String, Any?> {
        @Suppress("DEPRECATION")
        return delegate.onProvideAttributes(request, response, throwable)
    }

    override fun onProvideAttributes(
        request: RequestInfo,
        response: ResponseInfo?,
        throwable: Throwable?
    ) = delegate.onProvideAttributes(request, response, throwable).ifEmpty {
        val okHttpRequest = (request as? OkHttpRequestInfo)?.request ?: return emptyMap<String, Any?>()
        val okHttpResponse = (response as? OkHttpResponseInfo)?.response
        @Suppress("DEPRECATION")
        onProvideAttributes(okHttpRequest, okHttpResponse, throwable)
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.tools.annotation.NoOpImplementation
import okhttp3.Request
import okhttp3.Response

/**
 * Provider which listens for the network [HttpRequestInfo] -> [HttpResponseInfo] (or [Throwable]) chain and
 * offers a possibility to add custom attributes to the RUM Resource event.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface RumResourceAttributesProvider {

    /**
     * Deprecated. Use the variant with [HttpRequestInfo]/[HttpResponseInfo] instead.
     * Offers a possibility to create custom attributes collection which later will be attached to
     * the RUM resource event associated with the request.
     * @param request the intercepted [Request]
     * @param response the [Request] response in case of any
     * @param throwable in case an error occurred during the [Request]
     */
    @Deprecated("Use the variant with HttpRequestInfo/HttpResponseInfo instead")
    fun onProvideAttributes(
        request: Request,
        response: Response?,
        throwable: Throwable?
    ): Map<String, Any?>

    /**
     * Offers a possibility to create custom attributes collection which later will be attached to
     * the RUM resource event associated with the request.
     * @param request the intercepted [HttpRequestInfo]
     * @param response the [HttpResponseInfo] representing the response in case of any
     * @param throwable in case an error occurred during the request
     */
    fun onProvideAttributes(
        request: HttpRequestInfo,
        response: HttpResponseInfo?,
        throwable: Throwable?
    ): Map<String, Any?> = emptyMap()
}

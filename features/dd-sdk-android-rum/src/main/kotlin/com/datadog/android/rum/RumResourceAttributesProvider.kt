/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.tools.annotation.NoOpImplementation
import okhttp3.Request
import okhttp3.Response

/**
 * Provider which listens for the OkHttp [Request] -> [Response] (or [Throwable]) chain and
 * offers a possibility to add custom attributes to the RUM Resource event.
 */
@NoOpImplementation
fun interface RumResourceAttributesProvider {

    /**
     * Offers a possibility to create custom attributes collection which later will be attached to
     * the RUM resource event associated with the request.
     * @param request the intercepted [Request]
     * @param response the [Request] response in case of any
     * @param throwable in case an error occurred during the [Request]
     */
    fun onProvideAttributes(
        request: Request,
        response: Response?,
        throwable: Throwable?
    ): Map<String, Any?>
}

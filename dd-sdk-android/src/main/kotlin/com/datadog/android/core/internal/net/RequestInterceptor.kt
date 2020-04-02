/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.Request
import okhttp3.Response

internal interface RequestInterceptor {

    fun transformRequest(request: Request): Request

    fun handleResponse(request: Request, response: Response)

    fun handleThrowable(request: Request, throwable: Throwable)
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import com.datadog.android.lint.InternalApi

/**
 * For internal usage only.
 *
 * Represents an HTTP request info that can be modified.
 *
 * This interface allows instrumentation components to create a modified copy
 * of the request info (e.g., to add tracing headers) while preserving the
 * original request data.
 */
@InternalApi
interface MutableHttpRequestInfo {
    /**
     * Creates a modifier to modify this request info.
     * @return a new [com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder] initialized with this request's data.
     */
    fun newBuilder(): HttpRequestInfoBuilder
}

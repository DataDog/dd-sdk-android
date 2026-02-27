/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.wrappers

import com.datadog.android.sdk.integration.network.models.ClientExecutionResult
import com.datadog.android.sdk.integration.network.models.TestRequest
import com.datadog.android.trace.TracingHeaderType

internal interface HttpTestClientWrapper {
    val name: String

    fun shutdown()

    suspend fun execute(request: TestRequest): ClientExecutionResult

    companion object {
        val tracedHosts = mapOf<String, Set<TracingHeaderType>>(
            "localhost" to setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT),
            "127.0.0.1" to setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
        )
    }
}

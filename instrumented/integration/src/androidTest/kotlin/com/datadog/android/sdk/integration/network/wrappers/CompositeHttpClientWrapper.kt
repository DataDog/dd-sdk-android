/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.wrappers

import com.datadog.android.sdk.integration.network.models.ClientExecutionResult
import com.datadog.android.sdk.integration.network.models.TestRequest

internal class CompositeHttpClientWrapper(
    private val clients: List<HttpTestClientWrapper>
) {
    suspend fun execute(request: TestRequest): Map<String, ClientExecutionResult> =
        clients.associate { client -> client.name to client.execute(request) }

    fun shutdown() = clients.forEach { it.shutdown() }
}

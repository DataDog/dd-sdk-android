/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent

/**
 * Placeholder RequestFactory for evaluations endpoint.
 */
internal class EvaluationsRequestFactory(
    @Suppress("UnusedPrivateProperty")
    private val internalLogger: InternalLogger,
    @Suppress("UnusedPrivateProperty")
    private val customEvaluationEndpoint: String?
) : RequestFactory {
    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request? {
        return null
    }
}

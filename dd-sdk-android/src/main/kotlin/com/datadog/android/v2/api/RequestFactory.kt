/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.v2.api.context.DatadogContext

/**
 * Factory used to build requests from the batches stored.
 */
fun interface RequestFactory {

    // TODO RUMM-2298 Support 1:many relationship between batch and requests
    /**
     * Creates a request for the given batch.
     * @param context Datadog SDK context.
     * @param batchData Raw data of the batch.
     * @param batchMetadata Raw metadata of the batch.
     */
    fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request
}

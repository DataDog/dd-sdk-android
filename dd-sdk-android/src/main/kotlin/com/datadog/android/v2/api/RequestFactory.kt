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

    companion object {
        /**
         * application/json content type.
         */
        const val CONTENT_TYPE_JSON: String = "application/json"

        /**
         * text/plain;charset=UTF-8 content type.
         */
        const val CONTENT_TYPE_TEXT_UTF8: String = "text/plain;charset=UTF-8"

        /**
         * Datadog API key header.
         */
        const val HEADER_API_KEY: String = "DD-API-KEY"

        /**
         * Datadog Event Platform Origin header, e.g. android, flutter, etc.
         */
        const val HEADER_EVP_ORIGIN: String = "DD-EVP-ORIGIN"

        /**
         * Datadog Event Platform Origin version header, e.g. SDK version.
         */
        const val HEADER_EVP_ORIGIN_VERSION: String = "DD-EVP-ORIGIN-VERSION"

        /**
         * Datadog Request ID header, used for debugging purposes.
         */
        const val HEADER_REQUEST_ID: String = "DD-REQUEST-ID"

        /**
         * Datadog source query parameter name.
         */
        const val QUERY_PARAM_SOURCE: String = "ddsource"

        /**
         * Datadog tags query parameter name.
         */
        const val QUERY_PARAM_TAGS: String = "ddtags"
    }
}

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature.stub

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.utils.join
import fr.xgouchet.elmyr.Forge
import org.json.JSONObject
import org.mockito.kotlin.mock
import java.util.UUID

/**
 * A [RequestFactory] implementation that creates [Request] objects with a custom endpoint URL to be used in tests.
 */
class StubRequestFactory(
    private val forge: Forge,
    private val customEndpointUrl: String
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val batchDataAsByteArrayList = batchData.map { it.toJson(batchMetadata).toByteArray() }
        return Request(
            id = requestId,
            description = forge.anAlphabeticalString(),
            url = customEndpointUrl,
            headers = buildHeaders(
                requestId,
                context.clientToken,
                context.source,
                context.sdkVersion
            ),
            body = batchDataAsByteArrayList.join(
                separator = PAYLOAD_SEPARATOR,
                prefix = PAYLOAD_PREFIX,
                suffix = PAYLOAD_SUFFIX,
                internalLogger = mock()
            ),
            contentType = RequestFactory.CONTENT_TYPE_JSON
        )
    }

    private fun RawBatchEvent.toJson(batchMetadata: ByteArray?): String {
        return JSONObject().apply {
            put(DATA_KEY, String(data))
            put(METADATA_KEY, String(metadata))
            if (batchMetadata != null) {
                put(BATCH_METADATA, String(batchMetadata))
            }
        }.toString()
    }

    private fun buildHeaders(
        requestId: String,
        clientToken: String,
        source: String,
        sdkVersion: String
    ): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    companion object {
        /**
         * The key used to store the data in the JSON payload.
         */
        const val DATA_KEY = "data"

        /**
         * The key used to store the metadata in the JSON payload.
         */
        const val METADATA_KEY = "metadata"

        /**
         * The key used to store the batch metadata in the JSON payload.
         */
        const val BATCH_METADATA = "batch_metadata"
        private val PAYLOAD_SEPARATOR = ",".toByteArray(Charsets.UTF_8)
        private val PAYLOAD_PREFIX = "[".toByteArray(Charsets.UTF_8)
        private val PAYLOAD_SUFFIX = "]".toByteArray(Charsets.UTF_8)
    }
}

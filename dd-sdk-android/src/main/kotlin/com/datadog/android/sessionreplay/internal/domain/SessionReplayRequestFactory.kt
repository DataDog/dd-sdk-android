/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.sessionreplay.internal.net.BatchesToSegmentsMapper
import com.datadog.android.sessionreplay.internal.net.SessionReplayOkHttpUploader
import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext

internal class SessionReplayRequestFactory(
    private val sessionReplayOkHttpUploader: SessionReplayOkHttpUploader,
    private val batchToSegmentsMapper: BatchesToSegmentsMapper = BatchesToSegmentsMapper()
) : RequestFactory {
    override fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request {
        // TODO: RUMM-2547 Drop this code and return a list of requests instead when
        // the feature will be available in SDK V2
        // Also add the necessary unit tests once this is done.
        batchToSegmentsMapper.map(batchData).forEach {
            sessionReplayOkHttpUploader.upload(
                it.first,
                (it.second.toString() + "\n").toByteArray()
            )
        }
        return Request(
            sessionReplayOkHttpUploader.requestId,
            "",
            sessionReplayOkHttpUploader.buildUrl(),
            mapOf(
                DataOkHttpUploaderV2.HEADER_API_KEY
                    to sessionReplayOkHttpUploader.clientToken
            ),
            ByteArray(0)
        )
    }
}
